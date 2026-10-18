"""Device Factory v1. Pure generation/validation; no persistence or runtime calls.

Factory results are schema-compatible profiles plus a separate quality report.
Callers must provide the complete existing library at each acceptance boundary.
"""

from __future__ import annotations

import copy
import hashlib
import json
import re
import uuid
from collections.abc import Iterable, Mapping
from dataclasses import asdict, dataclass
from datetime import UTC, date, datetime
from pathlib import Path

from .derive import derive_identifier_bundle, luhn_check_digit, luhn_valid
from .engine import IDENTIFIER_FIELDS
from .reality import at
from .reality import generate as generate_reality
from .validate import Finding, validate_profile

VERSION = 1
CATALOG = Path(__file__).resolve().parents[2] / "catalog/device-factory-v1.json"
IMMUTABLE_BLOCKS = ("device", "display", "camera", "telephony", "locale", "network")
SECTIONS = (
    "compute",
    "memory",
    "panel",
    "cameras",
    "sensors",
    "power",
    "carrier",
    "webview",
    "environment",
)


# Independent model/launch-platform constraints reject another device's SoC
# even when a modified catalog entry is otherwise self-consistent.
PLATFORMS = {
    "pixel_6": ("gs101", 31),
    "pixel_6a": ("gs101", 32),
    "pixel_6_pro": ("gs101", 31),
    "pixel_7": ("gs201", 33),
    "pixel_7a": ("gs201", 33),
    "pixel_7_pro": ("gs201", 33),
    "pixel_8": ("zuma", 34),
    "pixel_8a": ("zuma", 34),
    "pixel_8_pro": ("zuma", 34),
    "pixel_9": ("zumapro", 34),
    "pixel_9_pro": ("zumapro", 34),
    "galaxy_s23": ("qcom", 33),
}


def _hash(value):
    return hashlib.sha256(
        json.dumps(
            value,
            sort_keys=True,
            separators=(",", ":"),
            ensure_ascii=True,
            allow_nan=False,
        ).encode()
    ).hexdigest()


def _error(code, field):
    # Neither finding text nor exceptions disclose rejected identifier values.
    return Finding(
        "error", code, f"{field}: failed factory {code.lower()} check.", field
    )


class FactoryError(ValueError):
    def __init__(self, findings):
        self.findings = tuple(findings)
        super().__init__(
            "Factory rejected input; no profiles accepted: "
            + ", ".join(sorted({f.code for f in findings}))
        )


@dataclass(frozen=True)
class InstanceRequest:
    template_id: str
    seed: str
    ordinal: int = 0
    revision: int = 0
    label: str = "Device"

    def validate(self):
        if (
            not isinstance(self.template_id, str)
            or not self.template_id
            or not isinstance(self.seed, str)
            or not 1 <= len(self.seed) <= 1024
            or type(self.ordinal) is not int
            or not 0 <= self.ordinal < 2**31
            or type(self.revision) is not int
            or not 0 <= self.revision < 2**31
            or not isinstance(self.label, str)
            or not self.label.strip()
            or not 1 <= len(self.label) <= 64
            or any(ord(c) < 32 or ord(c) == 127 for c in self.label)
        ):
            raise FactoryError([_error("REQUEST", "request")])

    def derived_seed(self):
        self.validate()
        return _hash(
            [
                "cyclone-device-factory",
                VERSION,
                self.template_id,
                self.seed,
                self.ordinal,
                self.revision,
            ]
        )


def _body(template, request):
    seed = request.derived_seed()
    identifiers = derive_identifier_bundle(seed)
    # ICCID: telecom MII + US issuer class + subscriber digits + Luhn.
    prefix = template["classes"]["carrier_region"]["iccid_prefix"]
    digits = prefix + str(
        int(_hash([seed, "iccid"]), 16) % 10 ** (18 - len(prefix))
    ).zfill(18 - len(prefix))
    identifiers["sim_serial"] = digits + luhn_check_digit(digits)
    if template["telephony"]["sim_slot_count"] == 1:
        identifiers.pop("imei_secondary")
    d = template["device"]
    version = template["reality"]["webview"]["values"]["version"]
    ua = (
        f"Mozilla/5.0 (Linux; Android {d['version_release']}; {d['model']}) "
        f"AppleWebKit/537.36 (KHTML, like Gecko) Chrome/{version} Mobile Safari/537.36"
    )
    return dict(
        schema_version="0.4",
        id=str(uuid.uuid5(uuid.NAMESPACE_URL, "cloak-factory-v1:" + seed)),
        name=request.label,
        seed=seed,
        identifiers=identifiers,
        **{k: copy.deepcopy(template[k]) for k in IMMUTABLE_BLOCKS},
        reality=generate_reality(template, seed, ua),
        ua={"value": ua},
        integrity={"verdict": "unknown", "checked_at": None, "keybox_tag": None},
        health={"state": "new", "last_seen": None},
        meta={
            "source": "forge",
            "forge_version": "device-factory-v1",
            "template": template["id"],
        },
    )


def _template_findings(t):
    try:
        request = InstanceRequest(t["id"], "catalog-validation")
        p = _body(t, request)
        issues = [f for f in validate_profile(p) if f.severity == "error"]
        d, c = t["device"], t["classes"]

        def check(ok, field):
            if not ok:
                issues.append(_error("TEMPLATE_COHERENCE", field))

        check(
            (d["hardware"], d["first_api_level"]) == PLATFORMS[t["id"]],
            "device.hardware",
        )

        check(
            all(d[k] == v for k, v in t["firmware_binding"].items())
            and set(t["firmware_binding"])
            == {
                "fingerprint",
                "security_patch",
                "build_id",
                "version_incremental",
                "build_date_utc",
            },
            "firmware_binding",
        )
        check(
            date.fromisoformat(d["security_patch"])
            <= datetime.fromtimestamp(d["build_date_utc"], UTC).date(),
            "device.security_patch",
        )
        check(
            d["sdk_int"] in (33, 34) and d["first_api_level"] <= d["sdk_int"],
            "device.sdk_int",
        )
        if t["id"].startswith("pixel_"):
            check(
                d["manufacturer"] == "Google"
                and d["brand"] == "google"
                and d["model"]
                == "Pixel " + t["id"][6:].replace("_", " ").replace("pro", "Pro"),
                "device.model",
            )
        else:
            check(
                t["id"] == "galaxy_s23"
                and d["manufacturer"] == "samsung"
                and d["brand"] == "samsung"
                and d["model"] == "SM-S911B",
                "device.model",
            )
        earliest = {33: date(2022, 8, 15), 34: date(2023, 10, 4)}[d["sdk_int"]]
        check(
            datetime.fromtimestamp(d["build_date_utc"], UTC).date() >= earliest,
            "device.build_date_utc",
        )
        check(
            320 <= t["display"]["width"] <= 1600
            and 1200 <= t["display"]["height"] <= 3400
            and t["display"]["screen_size_class"] == "normal"
            and 300 <= t["display"]["density"] <= 640,
            "display",
        )
        rates = c["refresh_capabilities_hz"]
        check(
            rates in ([60], [60, 90], [60, 120])
            and t["display"]["refresh_rate_hz"] == max(rates)
            and c["mode_class"] == ("fixed-60" if rates == [60] else "oled-variable"),
            "classes.mode_class",
        )
        check(
            [m["refresh_hz"] for m in t["reality"]["panel"]["values"]["modes"]]
            == rates,
            "reality.panel",
        )
        check(5.5 <= c["display_inches"] <= 7.0, "classes.display_inches")
        cams = t["camera"]["cameras"]
        check(
            [v["facing"] for v in cams].count(0) == 1
            and [v["facing"] for v in cams].count(1) in (2, 3)
            and len(cams) == (4 if c["camera_class"] == "triple-rear-front" else 3),
            "camera",
        )
        check(
            len(t["reality"]["cameras"]["values"]["inventory"]) == len(cams),
            "reality.cameras",
        )
        check(
            len(t["reality"]["sensors"]["values"]["inventory"]) >= 5
            and c["sensor_inventory_class"] == "phone-motion-environment",
            "reality.sensors",
        )
        mem = t["reality"]["memory"]["values"]
        check(
            mem["ram_gib"] in (6, 8, 12, 16)
            and mem["storage_gib"] in (128, 256, 512)
            and c["ram_storage_class"]
            == f"{mem['ram_gib']}GiB/{mem['storage_gib']}GiB",
            "reality.memory",
        )
        check(
            3000 <= t["reality"]["power"]["values"]["design_capacity_mah"] <= 5500
            and c["battery_class"] == "li-ion",
            "reality.power",
        )
        compute = t["reality"]["compute"]["values"]
        hardware = {
            "Google Tensor": "gs101",
            "Google Tensor G2": "gs201",
            "Google Tensor G3": "zuma",
            "Google Tensor G4": "zumapro",
            "Snapdragon 8 Gen 2 for Galaxy": "qcom",
        }
        check(
            hardware[compute["soc"]] == d["hardware"]
            and c["graphics_adapter_class"] == compute["gpu"],
            "reality.compute",
        )
        check(
            c["browser_class"] == "chromium-126-android"
            and t["reality"]["webview"]["values"]["version"] == "126.0.6478.122",
            "reality.webview",
        )
        check(
            c["play_services_expectations"]
            == {
                "packages": ["com.google.android.gms", "com.android.vending"],
                "certification": "unverified",
                "attestation": "requires runtime evidence",
            },
            "classes.play_services_expectations",
        )
        r = c["carrier_region"]
        check(
            (r["country"], r["mcc"], r["mnc"], r["iccid_prefix"])
            == ("US", "310", "260", "8901260")
            and t["locale"]["country"] == "US"
            and t["locale"]["timezone"] == "America/New_York"
            and t["telephony"]["carrier_name"] == "T-Mobile"
            and t["telephony"]["mcc"] == r["mcc"]
            and t["telephony"]["mnc"] == r["mnc"],
            "classes.carrier_region",
        )
        for field in ("bootloader", "baseband"):
            check(
                re.fullmatch(c["modem_patterns"][field], d[field]) is not None,
                "device." + field,
            )
        check(bool(t["sources"]), "sources")
        return issues
    except (KeyError, TypeError, ValueError, AttributeError, OverflowError, re.error):
        return [_error("INVALID_TEMPLATE", "template")]


class DeviceFactory:
    """An immutable catalog snapshot; generated dictionaries are caller-owned copies."""

    def __init__(self, catalog_path=CATALOG):
        try:
            document = json.loads(Path(catalog_path).read_text(encoding="utf-8"))
        except (ValueError, UnicodeError):
            raise FactoryError([_error("CATALOG_FORMAT", "catalog")]) from None
        if (
            not isinstance(document, dict)
            or type(document.get("format")) is not int
            or document.get("format") != VERSION
            or not isinstance(document.get("templates"), list)
        ):
            raise FactoryError([_error("CATALOG_FORMAT", "catalog")])
        self._templates = {}
        for t in document["templates"]:
            findings = _template_findings(t)
            if findings:
                raise FactoryError(findings)
            if t["id"] in self._templates:
                raise FactoryError([_error("DUPLICATE_TEMPLATE", "catalog")])
            self._templates[t["id"]] = copy.deepcopy(t)
        if not self._templates:
            raise FactoryError([_error("EMPTY_CATALOG", "catalog")])

    def catalog(self):
        return copy.deepcopy(list(self._templates.values()))

    def _template(self, template_id):
        if template_id not in self._templates:
            raise FactoryError([_error("UNKNOWN_TEMPLATE", "template_id")])
        return self._templates[template_id]

    def validate(self, profile, template_id, existing: Iterable[Mapping] = ()):
        """Strict acceptance gate. Includes same-ID records and cross-slot collisions."""
        t = self._template(template_id)
        findings = []
        try:
            expected_fields = {
                "schema_version",
                "id",
                "name",
                "seed",
                "identifiers",
                "reality",
                "ua",
                "integrity",
                "health",
                "meta",
                *IMMUTABLE_BLOCKS,
            }
            if set(profile) != expected_fields:
                findings.append(_error("PROFILE_FIELDS", "profile"))
            findings.extend(validate_profile(profile))
            if profile.get("id") != str(
                uuid.uuid5(
                    uuid.NAMESPACE_URL, "cloak-factory-v1:" + profile.get("seed", "")
                )
            ):
                findings.append(_error("PROFILE_ID_DERIVATION", "id"))
            if profile.get("meta") != {
                "source": "forge",
                "forge_version": "device-factory-v1",
                "template": template_id,
            }:
                findings.append(_error("FACTORY_METADATA", "meta"))
            for block in IMMUTABLE_BLOCKS:
                if profile.get(block) != t[block]:
                    findings.append(_error("TEMPLATE_MISMATCH", block))
            for section in SECTIONS:
                actual = at(profile, "reality." + section + ".values")
                expected = t["reality"][section]["values"]
                if section == "webview":
                    actual = {
                        k: v for k, v in (actual or {}).items() if k != "user_agent"
                    }
                if actual != expected:
                    findings.append(_error("CAPABILITY_MISMATCH", "reality." + section))
            if profile.get("schema_version") != "0.4":
                findings.append(_error("FACTORY_SCHEMA", "schema_version"))
            ids = profile.get("identifiers", {})
            required = set(IDENTIFIER_FIELDS)
            if t["telephony"]["sim_slot_count"] == 1:
                required.remove("imei_secondary")
            if set(ids) != required:
                findings.append(_error("IDENTIFIER_SET", "identifiers"))
            seen = set()
            for key in sorted(required):
                v = ids.get(key)
                if not isinstance(v, str) or not v or set(v.replace(":", "")) == {"0"}:
                    findings.append(_error("IDENTIFIER_REQUIRED", "identifiers." + key))
                    continue
                normalized = v.lower().replace(":", "").replace("-", "")
                if normalized in seen:
                    findings.append(_error("IDENTIFIER_REUSED", "identifiers." + key))
                seen.add(normalized)
                if (
                    key in ("mac", "bt_mac")
                    and re.fullmatch(r"(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}", v)
                    and int(v[:2], 16) & 3 != 2
                ):
                    findings.append(_error("MAC_LOCALITY", "identifiers." + key))
                if key == "sim_serial" and (
                    not re.fullmatch(r"[0-9]{19}", v)
                    or not luhn_valid(v)
                    or not v.startswith(t["classes"]["carrier_region"]["iccid_prefix"])
                ):
                    findings.append(
                        _error("ICCID_REGION_CHECKSUM", "identifiers." + key)
                    )
            for other in existing:
                if profile.get("id") == other.get("id"):
                    findings.append(_error("PROFILE_ID_COLLISION", "id"))
                other_values = {
                    str(v).lower().replace(":", "").replace("-", "")
                    for v in other.get("identifiers", {}).values()
                }
                if seen & other_values:
                    findings.append(_error("LIBRARY_COLLISION", "identifiers"))
                inv = at(profile, "reality.inventory_id")
                if inv and inv == at(other, "reality.inventory_id"):
                    findings.append(_error("LIBRARY_COLLISION", "reality.inventory_id"))
        except (KeyError, TypeError, ValueError, AttributeError, OverflowError):
            findings.append(_error("MALFORMED_PROFILE", "profile"))
        return sorted(set(findings), key=lambda f: (f.severity, f.field or "", f.code))

    def quality_report(self, profile, request, existing=(), created_at=None):
        request.validate()
        findings = self.validate(profile, request.template_id, existing)
        expected = _body(self._template(request.template_id), request)
        if profile != expected:
            findings.append(_error("DERIVATION_MISMATCH", "profile"))
        paths = (
            ["device." + k for k in expected["device"]]
            + ["identifiers." + k for k in expected["identifiers"]]
            + ["reality." + k + ".values" for k in SECTIONS]
            + ["telephony", "display", "locale", "camera", "ua"]
        )
        score = round(
            100
            * sum(at(profile, p) not in (None, "", {}, []) for p in paths)
            / len(paths),
            2,
        )
        timestamp = (
            created_at if created_at is not None else datetime.now(UTC).isoformat()
        )
        try:
            if datetime.fromisoformat(timestamp).utcoffset() is None:
                raise ValueError()
        except (ValueError, TypeError):
            raise FactoryError([_error("TIMESTAMP", "created_at")]) from None
        return {
            "validation_state": "invalid"
            if any(f.severity == "error" for f in findings)
            else "valid",
            "completeness_score": score,
            "failed_checks": [asdict(f) for f in findings if f.severity == "error"],
            "warnings": [asdict(f) for f in findings if f.severity != "error"]
            + [
                {
                    "code": "REFERENCE_APPROXIMATION",
                    "message": "Firmware/HAL classes are approximated; runtime verification remains required.",
                },
                {
                    "code": "SYNTHETIC_IDENTIFIERS",
                    "message": "IMEI TACs are synthetic; TAC allocation is unverified; DRM IDs are local tokens, not provisioning or attestation.",
                },
            ],
            "template_id": request.template_id,
            "seed_fingerprint": _hash(["seed", request.seed]),
            "ordinal": request.ordinal,
            "revision": request.revision,
            "creation_timestamp": timestamp,
            "factory_version": VERSION,
            "template_digest": _hash(self._template(request.template_id)),
        }

    def generate(self, request: InstanceRequest, existing=(), *, created_at=None):
        request.validate()
        profile = _body(self._template(request.template_id), request)
        report = self.quality_report(profile, request, existing, created_at)
        if report["validation_state"] != "valid":
            raise FactoryError([Finding(**f) for f in report["failed_checks"]])
        return {"profile": profile, "quality": report}

    def batch(
        self,
        template_id,
        seed,
        count,
        *,
        label_prefix="Device",
        seed_policy="shared",
        item_seeds=None,
        revision=0,
        start_ordinal=0,
        existing=(),
        created_at=None,
    ):
        """All-or-nothing in memory. Collision rejects the whole batch without reseeding."""
        if (
            type(count) is not int
            or not 1 <= count <= 1000
            or seed_policy not in ("shared", "per-item")
        ):
            raise FactoryError([_error("BATCH_REQUEST", "batch")])
        if (
            (
                seed_policy == "per-item"
                and (
                    not isinstance(item_seeds, (list, tuple))
                    or len(item_seeds) != count
                )
            )
            or (seed_policy == "shared" and item_seeds is not None)
            or type(start_ordinal) is not int
        ):
            raise FactoryError([_error("SEED_POLICY", "batch")])
        library = copy.deepcopy(list(existing))
        result = []
        timestamp = (
            created_at if created_at is not None else datetime.now(UTC).isoformat()
        )
        for i in range(count):
            request = InstanceRequest(
                template_id,
                seed if seed_policy == "shared" else item_seeds[i],
                start_ordinal + i,
                revision,
                f"{label_prefix} {start_ordinal + i + 1:03d}",
            )
            bundle = self.generate(request, library, created_at=timestamp)
            result.append(bundle)
            library.append(bundle["profile"])
        return result
