"""Catalog Import Bridge v1: offline AOSP XML -> evidence -> reviewed candidates.

No network, script execution, persistence, catalog replacement or runtime calls.
Only an explicitly promoted candidate can enter Device Factory's opt-in path.
"""

from __future__ import annotations

import copy
import math
import re
import xml.etree.ElementTree as ET
from collections import defaultdict
from decimal import Decimal, InvalidOperation
from pathlib import Path

from .catalog_provenance import (
    ImportRejected,
    checked_source,
    digest,
    fact,
    load_json,
    validate_manifest,
)
from .prop_parse import _DEVICE_KEYS, parse_build_prop

FIELD_TABLE = Path(__file__).resolve().parents[2] / "catalog/import-fields-v1.json"
MAX_XML_BYTES = 256_000
DENSITIES = {
    "ldpi": 120,
    "mdpi": 160,
    "tvdpi": 213,
    "hdpi": 240,
    "xhdpi": 320,
    "xxhdpi": 480,
    "xxxhdpi": 640,
}
NAMESPACES = {"", *{f"http://schemas.android.com/sdk/devices/{v}" for v in range(1, 7)}}
STRUCTURE = {
    "devices": {"device"},
    "device": {"name", "id", "manufacturer", "hardware", "software", "state", "meta"},
    "hardware": {
        "screen",
        "networking",
        "sensors",
        "mic",
        "camera",
        "keyboard",
        "nav",
        "ram",
        "buttons",
        "internal-storage",
        "removable-storage",
        "cpu",
        "gpu",
        "abi",
        "dock",
        "power-type",
    },
    "screen": {
        "screen-size",
        "diagonal-length",
        "pixel-density",
        "screen-ratio",
        "dimensions",
        "xdpi",
        "ydpi",
        "touch",
    },
    "dimensions": {"x-dimension", "y-dimension"},
    "touch": {"multitouch", "mechanism", "screen-type"},
    "camera": {"location", "autofocus", "flash"},
    "software": {
        "api-level",
        "live-wallpaper-support",
        "bluetooth-profiles",
        "gl-version",
        "gl-extensions",
        "status-bar",
    },
    "state": {"description", "screen-orientation", "keyboard-state", "nav-state"},
    "meta": {"icons", "frame"},
    "icons": {"sixteen", "sixty-four"},
    "frame": {
        "path",
        "portrait-x-offset",
        "portrait-y-offset",
        "landscape-x-offset",
        "landscape-y-offset",
    },
}


def _table():
    return load_json(FIELD_TABLE.read_bytes())


def _validate(manifest):
    return validate_manifest(manifest, set(_table()["paths"]))


def _raw(text, maximum=MAX_XML_BYTES):
    if isinstance(text, str):
        if len(text) > maximum:
            raise ImportRejected("INPUT_SIZE")
        try:
            text = text.encode("utf-8")
        except UnicodeError:
            raise ImportRejected("INPUT_ENCODING") from None
    if not isinstance(text, bytes) or not 0 < len(text) <= maximum:
        raise ImportRejected("INPUT_SIZE")
    try:
        decoded = text.decode("utf-8")
    except UnicodeError:
        raise ImportRejected("INPUT_ENCODING") from None
    if "\x00" in decoded:
        raise ImportRejected("INPUT_ENCODING")
    return text


def _number(value, minimum, maximum, integer=False):
    try:
        result = Decimal(value)
        if (
            not result.is_finite()
            or not minimum <= result <= maximum
            or (integer and result != int(result))
        ):
            raise ValueError()
        return int(result) if integer else float(result)
    except (ValueError, TypeError, InvalidOperation):
        raise ImportRejected("XML_VALUE") from None


def _bool(value):
    if value not in {"true", "false"}:
        raise ImportRejected("XML_BOOLEAN")
    return value == "true"


def parse_hardware_xml(text, source, *, device_id=None):
    """Parse one explicitly selected device. Unknown XML is rejected, never guessed."""
    raw = _raw(text)
    source = checked_source(source, raw, "hardware", "hardware-xml")
    if re.search(rb"<!\s*(?:DOCTYPE|ENTITY)", raw, re.IGNORECASE):
        raise ImportRejected("XML_DECLARATION_FORBIDDEN")
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        raise ImportRejected("XML_MALFORMED") from None
    count = 0

    def check(node, depth=0):
        nonlocal count
        count += 1
        if count > 4096 or depth > 16:
            raise ImportRejected("XML_COMPLEXITY")
        namespace, _, tag = (
            node.tag[1:].partition("}")
            if node.tag.startswith("{")
            else ("", "", node.tag)
        )
        if namespace not in NAMESPACES:
            raise ImportRejected("XML_NAMESPACE")
        node.tag = tag
        allowed_attrs = (
            {"unit"}
            if tag in {"ram", "internal-storage", "removable-storage"}
            else {"name", "default"}
            if tag == "state"
            else set()
        )
        if set(node.attrib) - allowed_attrs:
            raise ImportRejected("XML_ATTRIBUTE")
        children = set()
        for child in node:
            check(child, depth + 1)
            if child.tag not in STRUCTURE.get(tag, set()):
                raise ImportRejected("XML_UNSUPPORTED_FIELD")
            if child.tag in children and (tag, child.tag) not in {
                ("devices", "device"),
                ("device", "state"),
                ("device", "software"),
                ("hardware", "camera"),
            }:
                raise ImportRejected("DUPLICATE_FIELD")
            children.add(child.tag)
        if len(node) and (node.text or "").strip():
            raise ImportRejected("XML_MIXED_CONTENT")
        if (node.tail or "").strip():
            raise ImportRejected("XML_MIXED_CONTENT")

    check(root)
    if root.tag not in {"devices", "device"}:
        raise ImportRejected("XML_ROOT")
    devices = list(root) if root.tag == "devices" else [root]
    ids = [d.findtext("id") or d.findtext("name") for d in devices]
    if len(ids) != len(set(ids)):
        raise ImportRejected("DUPLICATE_DEVICE")
    selected = [
        d for d, key in zip(devices, ids) if device_id is None or key == device_id
    ]
    if len(selected) != 1:
        raise ImportRejected("DEVICE_SELECTION_REQUIRED")
    device = selected[0]
    output = {"format": 1, "sources": [source], "facts": []}

    def emit(path, value, evidence):
        output["facts"].append(fact(path, value, source["id"], evidence))

    for xml, path in {
        "name": "device.model",
        "manufacturer": "device.manufacturer",
    }.items():
        value = device.findtext(xml)
        if value:
            emit(path, value.strip(), "device/" + xml)
    hw = device.find("hardware")
    if hw is None:
        raise ImportRejected("XML_HARDWARE_REQUIRED")
    mappings = {
        "screen/dimensions/x-dimension": (
            "display.width",
            lambda v: _number(v, 1, 16384, True),
        ),
        "screen/dimensions/y-dimension": (
            "display.height",
            lambda v: _number(v, 1, 16384, True),
        ),
        "screen/screen-size": ("display.screen_size_class", str),
        "screen/diagonal-length": (
            "hardware.diagonal_inches",
            lambda v: _number(v, 0.1, 200),
        ),
        "screen/xdpi": ("hardware.physical_xdpi", lambda v: _number(v, 1, 5000)),
        "screen/ydpi": ("hardware.physical_ydpi", lambda v: _number(v, 1, 5000)),
        "cpu": ("hardware.cpu_label", str),
        "gpu": ("hardware.gpu_label", str),
        "mic": ("hardware.microphone", _bool),
        "keyboard": ("hardware.keyboard", str),
        "nav": ("hardware.navigation", str),
        "power-type": ("hardware.power_type", str),
        "screen/touch/mechanism": ("hardware.touch", str),
    }
    for xml, (path, convert) in mappings.items():
        value = hw.findtext(xml)
        if value and value.strip():
            emit(path, convert(value.strip()), "hardware/" + xml)
    density = hw.findtext("screen/pixel-density")
    if density:
        density = density.strip()
        if density in {"nodpi", "anydpi"}:
            output["facts"].append(
                fact(
                    "display.density",
                    None,
                    source["id"],
                    "hardware/screen/pixel-density",
                    availability="unavailable",
                )
            )
        else:
            value = DENSITIES.get(density)
            if value is None:
                match = re.fullmatch(r"([0-9]+)dpi", density)
                if not match:
                    raise ImportRejected("XML_DENSITY")
                value = _number(match[1], 72, 2000, True)
            emit("display.density", value, "hardware/screen/pixel-density")
    for xml, path in {
        "ram": "hardware.ram_bytes",
        "internal-storage": "hardware.storage_bytes",
        "removable-storage": "hardware.removable_bytes",
    }.items():
        node = hw.find(xml)
        if node is not None:
            units = {
                "B": 1,
                "KiB": 1024,
                "MiB": 1024**2,
                "GiB": 1024**3,
                "KB": 1000,
                "MB": 1000**2,
                "GB": 1000**3,
            }
            if node.get("unit") not in units:
                raise ImportRejected("XML_UNIT")
            value = _number(node.text, 0, 2**40) * units[node.get("unit")]
            if not math.isfinite(value) or value != int(value) or value > 2**60:
                raise ImportRejected("XML_UNIT")
            emit(path, int(value), "hardware/" + xml)
    for xml in ("networking", "sensors", "abi", "dock"):
        value = hw.findtext(xml)
        if value is not None:
            tokens = value.split()
            if len(set(tokens)) != len(tokens):
                raise ImportRejected("DUPLICATE_FIELD")
            emit("hardware." + xml, sorted(tokens), "hardware/" + xml)
    cameras = []
    for camera in hw.findall("camera"):
        row = {}
        for key in ("location", "autofocus", "flash"):
            value = camera.findtext(key)
            if value is not None:
                row[key] = value.strip() if key == "location" else _bool(value.strip())
        if row.get("location") not in {"front", "back", "external"}:
            raise ImportRejected("XML_CAMERA")
        if any(c["location"] == row["location"] for c in cameras):
            raise ImportRejected("DUPLICATE_FIELD")
        cameras.append(row)
    if cameras:
        emit(
            "hardware.cameras",
            cameras,
            "hardware/camera; presence only, no HAL/megapixel claim",
        )
    # Software API ranges describe AVD compatibility, not shipped SDK or launch API.
    # States, icons, GL strings and paths are accepted inert metadata, never opened/executed.
    return _validate(output)


def _append(manifest, source, additions):
    output = copy.deepcopy(_validate(manifest))
    if source["id"] in {s["id"] for s in output["sources"]}:
        raise ImportRejected("DUPLICATE_SOURCE")
    output["sources"].append(source)
    output["facts"].extend(additions)
    return _validate(output)


def resolve(manifest):
    """Priority never silently resolves disagreement. Review must name the evidence."""
    manifest = _validate(manifest)
    sources = {s["id"]: s for s in manifest["sources"]}
    grouped = defaultdict(list)
    for item in manifest["facts"]:
        grouped[item["path"]].append(item)
    fields, conflicts = {}, []
    priorities = {"review": 0, "build": 1, "hardware": 2, "reference": 3}
    for path, items in sorted(grouped.items()):
        concrete = [f for f in items if f["availability"] != "unknown"]
        resolved_ids = {i for f in items for i in f["resolves"]}
        active = [f for f in concrete or items if f["id"] not in resolved_ids]
        candidates = sorted(
            active or concrete or items,
            key=lambda f: (priorities[sources[f["source_id"]]["role"]], f["id"]),
        )
        chosen = candidates[0]
        selected_value = digest([chosen["availability"], chosen["value"]])
        disagree = [
            f
            for f in concrete
            if digest([f["availability"], f["value"]]) != selected_value
        ]
        if disagree and (
            sources[chosen["source_id"]]["role"] != "review"
            or not {f["id"] for f in disagree} <= set(chosen["resolves"])
        ):
            conflicts.append(path)
        fields[path] = copy.deepcopy(chosen)
    return {"fields": fields, "conflicts": conflicts}


def attach_build_properties(manifest, text, source):
    raw = _raw(text)
    source = checked_source(source, raw, "build", "build-prop")
    props = parse_build_prop(raw.decode("utf-8"))
    # Keep the established parser semantics, but reject duplicate raw keys before its last-wins map.
    seen = set()
    for line in raw.decode("utf-8").splitlines():
        parsed = parse_build_prop(line)
        for key in parsed:
            if key in seen:
                raise ImportRejected("DUPLICATE_PROPERTY")
            seen.add(key)
    fields = resolve(manifest)["fields"]
    for path, key in {
        "device.manufacturer": "ro.product.manufacturer",
        "device.model": "ro.product.model",
    }.items():
        known = fields.get(path)
        if not known or known["availability"] != "known" or not props.get(key):
            raise ImportRejected("BUILD_MATCH_INCOMPLETE", path)
        if known["value"] != props[key]:
            raise ImportRejected("BUILD_IDENTITY_MISMATCH", path)
    additions = []
    for name, keys in _DEVICE_KEYS.items():
        values = [props[k] for k in keys if props.get(k)]
        # Do not use draft_device's model fallback or SDK-to-first_api_level fallback.
        if name in {"brand", "product", "device"}:
            values = [props[keys[0]]] if props.get(keys[0]) else []
        if len(set(values)) > 1:
            raise ImportRejected("PROPERTY_ALIAS_CONFLICT", "device." + name)
        value = values[0] if values else None
        if value is not None and name in {
            "sdk_int",
            "first_api_level",
            "build_date_utc",
        }:
            if not re.fullmatch(r"[0-9]+", value):
                raise ImportRejected("PROPERTY_INTEGER", "device." + name)
            value = int(value)
        additions.append(
            fact(
                "device." + name,
                value,
                source["id"],
                "property:" + keys[0],
                availability="known" if value is not None else "unknown",
            )
        )
    if "ro.sf.lcd_density" in props:
        value = _number(props["ro.sf.lcd_density"], 72, 2000, True)
        additions.append(
            fact("display.density", value, source["id"], "property:ro.sf.lcd_density")
        )
    return _append(manifest, source, additions)


def attach_review(manifest, text, source):
    """Attach explicit local facts/corrections; input is hashed, plain JSON only."""
    raw = _raw(text, 2_000_000)
    source = checked_source(source, raw, "review", "review-json")
    review = load_json(raw)
    if (
        not isinstance(review, dict)
        or set(review) != {"facts"}
        or not isinstance(review["facts"], list)
    ):
        raise ImportRejected("REVIEW_FORMAT")
    additions = []
    for row in review["facts"]:
        if not isinstance(row, dict) or set(row) != {
            "path",
            "value",
            "availability",
            "confidence",
            "evidence",
            "resolves",
        }:
            raise ImportRejected("REVIEW_FORMAT")
        additions.append(fact(source_id=source["id"], **row))
    return _append(manifest, source, additions)


def _set(target, path, value):
    parts = path.split(".")
    for part in parts[:-1]:
        target = target.setdefault(part, {})
    target[parts[-1]] = copy.deepcopy(value)


def completion(manifest):
    resolved = resolve(manifest)
    required = _table()["required"]
    missing = [
        p
        for p in required
        if p not in resolved["fields"]
        or resolved["fields"][p]["availability"] != "known"
    ]
    reasons = []
    used_sources = {f["source_id"] for f in manifest["facts"]}
    if any(
        not s["license_reviewed"]
        for s in manifest["sources"]
        if s["id"] in used_sources
    ):
        reasons.append("SOURCE_LICENSE_REVIEW_REQUIRED")
    if not any(s["role"] == "build" for s in manifest["sources"]):
        reasons.append("BUILD_PROPERTIES_REQUIRED")
    if not any(s["role"] == "review" for s in manifest["sources"]):
        reasons.append("EXPLICIT_REVIEW_REQUIRED")
    if missing:
        reasons.append("INCOMPLETE_FACTS")
    if resolved["conflicts"]:
        reasons.append("CONFLICTING_FACTS")
    template = {}
    for path in required:
        if path not in missing:
            _set(template, path, resolved["fields"][path]["value"])
    if not reasons:
        template["sources"] = [
            s.get("url", s.get("local_label")) for s in manifest["sources"]
        ]
        try:
            _check_template(template, resolved["fields"])
        except ImportRejected as exc:
            reasons.append(exc.code)
    return {
        "complete": not reasons,
        "reasons": sorted(reasons),
        "missing": sorted(missing),
        "conflicts": resolved["conflicts"],
        "template": template,
    }


def _check_template(template, fields):
    from .catalog_provenance import _text
    from .device_factory import InstanceRequest, _body
    from .validate import validate_profile

    try:
        if (
            not isinstance(template["id"], str)
            or not re.fullmatch(r"[a-z][a-z0-9_]{0,63}", template["id"])
            or not _text(template["label"], 128)
        ):
            raise ImportRejected("TEMPLATE_ID")
        device = template["device"]
        if any(device[k] != v for k, v in template["firmware_binding"].items()):
            raise ImportRejected("FIRMWARE_BINDING_MISMATCH")
        if set(template["firmware_binding"]) != {
            "fingerprint",
            "security_patch",
            "build_id",
            "version_incremental",
            "build_date_utc",
        }:
            raise ImportRejected("FIRMWARE_BINDING_MISMATCH")
        if (
            template["classes"]["play_services_expectations"].get("certification")
            != "unknown"
            or template["classes"]["play_services_expectations"].get("attestation")
            != "unknown"
        ):
            raise ImportRejected("TRUST_MUST_REMAIN_UNKNOWN")
        prefix = template["classes"]["carrier_region"]["iccid_prefix"]
        if not isinstance(prefix, str) or not re.fullmatch(r"89[0-9]{3,8}", prefix):
            raise ImportRejected("ICCID_PREFIX")
        if (
            type(device["first_api_level"]) is not int
            or not 1 <= device["first_api_level"] <= device["sdk_int"]
        ):
            raise ImportRejected("FIRST_API_LEVEL")
        if not 0 < template["display"]["refresh_rate_hz"] <= 1000:
            raise ImportRejected("DISPLAY_REFRESH")
        # Hardware hints remain descriptive. Compare only facts with exact semantic equivalents.
        comparisons = {
            "hardware.ram_bytes": ("memory", "ram_gib", 1024**3),
            "hardware.storage_bytes": ("memory", "storage_gib", 1024**3),
            "hardware.cpu_label": ("compute", "soc", 1),
            "hardware.gpu_label": ("compute", "gpu", 1),
        }
        for path, (section, name, factor) in comparisons.items():
            item = fields.get(path)
            if item and item["availability"] == "known":
                value = template["reality"][section]["values"][name]
                if (value * factor) != item["value"]:
                    raise ImportRejected("HARDWARE_COMPLETION_CONFLICT", path)
        declared = fields.get("hardware.cameras")
        if declared and declared["availability"] == "known":
            available = {v["location"] for v in declared["value"]}
            inventory = {
                v["facing"]
                for v in template["reality"]["cameras"]["values"]["inventory"]
            }
            if inventory != available:
                raise ImportRejected("HARDWARE_COMPLETION_CONFLICT", "hardware.cameras")
        classes, reality = template["classes"], template["reality"]
        memory = reality["memory"]["values"]
        if (
            classes["ram_storage_class"]
            != f"{memory['ram_gib']}GiB/{memory['storage_gib']}GiB"
            or classes["graphics_adapter_class"] != reality["compute"]["values"]["gpu"]
            or classes["refresh_capabilities_hz"]
            != [m["refresh_hz"] for m in reality["panel"]["values"]["modes"]]
            or template["display"]["refresh_rate_hz"]
            not in classes["refresh_capabilities_hz"]
        ):
            raise ImportRejected("TEMPLATE_CLASS_CONFLICT")
        body = _body(
            template, InstanceRequest(template["id"], "catalog-import-validation")
        )
        errors = [f for f in validate_profile(body) if f.severity == "error"]
        if errors:
            raise ImportRejected("PROFILE_COHERENCE", errors[0].field or "profile")
    except ImportRejected:
        raise
    except (KeyError, TypeError, ValueError, AttributeError, OverflowError):
        raise ImportRejected("TEMPLATE_INVALID") from None


def promote(manifest, *, reviewer):
    """Explicit, local review assertion, not a signature or external approval verdict."""
    from .catalog_provenance import _text

    if not _text(reviewer, 128):
        raise ImportRejected("REVIEWER_REQUIRED")
    status = completion(manifest)
    if not status["complete"]:
        raise ImportRejected(status["reasons"][0])
    candidate = {
        "format": 1,
        "kind": "catalog-import-candidate",
        "template": status["template"],
        "provenance": copy.deepcopy(manifest),
        "reviewer": reviewer,
        "platform_trust": "unknown",
    }
    candidate["digest"] = digest(candidate)
    return candidate


def validate_candidate(candidate):
    if not isinstance(candidate, dict) or set(candidate) != {
        "format",
        "kind",
        "template",
        "provenance",
        "reviewer",
        "platform_trust",
        "digest",
    }:
        raise ImportRejected("CANDIDATE_FORMAT")
    if (
        type(candidate["format"]) is not int
        or candidate["format"] != 1
        or candidate["kind"] != "catalog-import-candidate"
        or candidate["platform_trust"] != "unknown"
        or digest({k: v for k, v in candidate.items() if k != "digest"})
        != candidate["digest"]
    ):
        raise ImportRejected("CANDIDATE_DIGEST")
    rebuilt = promote(candidate["provenance"], reviewer=candidate["reviewer"])
    if candidate != rebuilt:
        raise ImportRejected("CANDIDATE_PROVENANCE_MISMATCH")
    return copy.deepcopy(candidate["template"])
