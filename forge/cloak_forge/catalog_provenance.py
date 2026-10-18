"""Bounded, offline provenance records for Catalog Import Bridge v1.

These records describe source evidence, never trust/certification or profile secrets.
The JSON schema is for interchange; this stdlib validator is the runtime gate.
"""

from __future__ import annotations

import copy
import hashlib
import json
import re
from datetime import date
from urllib.parse import unquote, urlsplit

MAX_BYTES = 2_000_000
APPROVED_URLS = (
    ("android.googlesource.com", "/platform/tools/base/"),
    ("developer.android.com", "/studio/run/managing-avds"),
    ("developer.sony.com", "/develop/drivers/"),
    ("opendevices.sony.net", "/aosp-on-xperia-open-devices/"),
    ("github.com", "/jaredrummler/AndroidDeviceNames"),
    ("github.com", "/matomo-org/device-detector"),
    ("support.google.com", "/googleplay/android-developer/answer/9859371"),
    ("firebase.google.com", "/docs/test-lab/android/available-testing-devices"),
    ("github.com", "/remote-android/redroid-doc"),
)


class ImportRejected(ValueError):
    """Fixed machine-readable reason; messages never echo source contents."""

    def __init__(self, code, field=""):
        self.code, self.field = code, field
        super().__init__(code + (": " + field if field else ""))


def canonical(value):
    try:
        return json.dumps(
            value,
            sort_keys=True,
            separators=(",", ":"),
            ensure_ascii=True,
            allow_nan=False,
        ).encode("utf-8")
    except (ValueError, TypeError, RecursionError):
        raise ImportRejected("MALFORMED_JSON") from None


def digest(value):
    return hashlib.sha256(canonical(value)).hexdigest()


def load_json(raw):
    if isinstance(raw, str):
        if len(raw) > MAX_BYTES:
            raise ImportRejected("INPUT_SIZE")
        try:
            raw = raw.encode("utf-8")
        except UnicodeError:
            raise ImportRejected("MALFORMED_JSON") from None
    if not isinstance(raw, bytes) or len(raw) > MAX_BYTES:
        raise ImportRejected("INPUT_SIZE")

    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ImportRejected("DUPLICATE_JSON_KEY")
            result[key] = value
        return result

    try:
        return json.loads(
            raw,
            object_pairs_hook=pairs,
            parse_constant=lambda _: (_ for _ in ()).throw(
                ImportRejected("MALFORMED_JSON")
            ),
        )
    except ImportRejected:
        raise
    except (ValueError, UnicodeError, RecursionError):
        raise ImportRejected("MALFORMED_JSON") from None


def _text(value, limit=2048):
    return (
        isinstance(value, str)
        and 0 < len(value) <= limit
        and value == value.strip()
        and not any(ord(c) < 32 or ord(c) == 127 for c in value)
    )


def validate_source(source):
    required = {
        "id",
        "sha256",
        "license",
        "license_reviewed",
        "notice",
        "observed_on",
        "format",
        "role",
    }
    if (
        not isinstance(source, dict)
        or not required <= source.keys()
        or source.keys() - required - {"url", "local_label", "revision"}
        or ("url" in source) == ("local_label" in source)
    ):
        raise ImportRejected("SOURCE_FORMAT")
    if (
        not _text(source["id"], 64)
        or not re.fullmatch(r"[A-Za-z0-9_-]+", source["id"])
        or not isinstance(source["sha256"], str)
        or not re.fullmatch(r"[0-9a-f]{64}", source["sha256"])
        or not _text(source["license"], 128)
        or type(source["license_reviewed"]) is not bool
        or source["license"].lower() in {"unknown", "none", "unreviewed", "pending"}
        or (
            source["license_reviewed"]
            and source["license"].startswith("LicenseRef-ReviewRequired")
        )
        or not _text(source["notice"], 4096)
        or source["format"]
        not in ("hardware-xml", "build-prop", "review-json", "reference")
        or source["role"] not in ("hardware", "build", "review", "reference")
        or ("revision" in source and not _text(source["revision"], 256))
    ):
        raise ImportRejected("SOURCE_METADATA")
    try:
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", source["observed_on"]):
            raise ValueError()
        date.fromisoformat(source["observed_on"])
    except (ValueError, TypeError):
        raise ImportRejected("OBSERVATION_DATE") from None
    if "local_label" in source:
        if not _text(source["local_label"], 256):
            raise ImportRejected("SOURCE_LABEL")
    else:
        try:
            url = urlsplit(source["url"])
            path = unquote(url.path)
            allowed = (
                url.scheme == "https"
                and not url.username
                and not url.password
                and url.port in (None, 443)
                and not url.fragment
                and "\\" not in path
                and not any(part in {".", ".."} for part in path.split("/"))
                and any(
                    url.hostname == host
                    and (
                        path == prefix.rstrip("/")
                        or path.startswith(prefix.rstrip("/") + "/")
                    )
                    for host, prefix in APPROVED_URLS
                )
            )
        except (ValueError, TypeError, AttributeError):
            allowed = False
        if not allowed or not _text(source["url"]):
            raise ImportRejected("SOURCE_NOT_APPROVED")
    return copy.deepcopy(source)


def checked_source(source, raw, role, source_format):
    source = validate_source(source)
    if source["role"] != role or source["format"] != source_format:
        raise ImportRejected("SOURCE_ROLE")
    if hashlib.sha256(raw).hexdigest() != source["sha256"]:
        raise ImportRejected("SOURCE_HASH_MISMATCH")
    return source


def fact(
    path,
    value,
    source_id,
    evidence,
    confidence="declared",
    availability="known",
    resolves=(),
):
    item = {
        "path": path,
        "value": value,
        "source_id": source_id,
        "evidence": evidence,
        "confidence": confidence,
        "availability": availability,
        "resolves": sorted(resolves),
    }
    item["id"] = digest(item)
    return item


def validate_manifest(manifest, allowed_paths):
    if (
        not isinstance(manifest, dict)
        or set(manifest) != {"format", "sources", "facts"}
        or type(manifest["format"]) is not int
        or manifest["format"] != 1
        or not isinstance(manifest["sources"], list)
        or not 1 <= len(manifest["sources"]) <= 64
        or not isinstance(manifest["facts"], list)
        or len(manifest["facts"]) > 4096
        or len(canonical(manifest)) > MAX_BYTES
    ):
        raise ImportRejected("MANIFEST_FORMAT")
    sources = {}
    for source in manifest["sources"]:
        validate_source(source)
        if source["id"] in sources:
            raise ImportRejected("DUPLICATE_SOURCE")
        sources[source["id"]] = source
    seen, ids = set(), set()
    for item in manifest["facts"]:
        if not isinstance(item, dict) or set(item) != {
            "id",
            "path",
            "value",
            "source_id",
            "evidence",
            "confidence",
            "availability",
            "resolves",
        }:
            raise ImportRejected("FACT_FORMAT")
        if (
            not isinstance(item["path"], str)
            or item["path"] not in allowed_paths
            or not isinstance(item["source_id"], str)
            or item["source_id"] not in sources
            or not _text(item["evidence"], 1024)
            or item["confidence"]
            not in ("declared", "public-reference", "reviewed", "approximated")
            or item["availability"] not in ("known", "unknown", "unavailable")
            or (item["availability"] == "known") == (item["value"] is None)
            or not isinstance(item["resolves"], list)
            or any(not isinstance(v, str) for v in item["resolves"])
            or len(set(item["resolves"])) != len(item["resolves"])
        ):
            raise ImportRejected("FACT_METADATA")
        expected = digest({k: v for k, v in item.items() if k != "id"})
        if item["id"] != expected:
            raise ImportRejected("FACT_HASH_MISMATCH")
        pair = (item["source_id"], item["path"])
        if pair in seen or item["id"] in ids:
            raise ImportRejected("DUPLICATE_FIELD")
        if item["resolves"] and sources[item["source_id"]]["role"] != "review":
            raise ImportRejected("UNREVIEWED_RESOLUTION")
        seen.add(pair)
        ids.add(item["id"])
    for item in manifest["facts"]:
        prior = {
            v["id"]
            for v in manifest["facts"]
            if v["path"] == item["path"] and v["id"] != item["id"]
        }
        if not set(item["resolves"]) <= prior:
            raise ImportRejected("INVALID_RESOLUTION")
    graph = {i["id"]: i["resolves"] for i in manifest["facts"]}
    done, visiting = set(), set()

    def visit(key):
        if key in visiting:
            raise ImportRejected("RESOLUTION_CYCLE")
        if key not in done:
            visiting.add(key)
            for prior in graph[key]:
                visit(prior)
            visiting.remove(key)
            done.add(key)

    try:
        for key in graph:
            visit(key)
    except RecursionError:
        raise ImportRejected("RESOLUTION_COMPLEXITY") from None
    return copy.deepcopy(manifest)
