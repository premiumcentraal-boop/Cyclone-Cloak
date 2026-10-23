"""Coherence validation for Cloak profiles.

Checks stored device-configuration values for internal consistency. Errors block use;
warnings and information describe uncertainty without rejecting a compatible profile.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import date, datetime, timezone
from typing import Any, Iterable, Mapping

from .derive import luhn_valid

ERROR = "error"
WARNING = "warning"
INFO = "info"
_SEVERITY_ORDER = {ERROR: 0, WARNING: 1, INFO: 2}

SCHEMA_VERSION = "0.4"
_LEGACY_SCHEMA_VERSIONS = ("0.2", "0.3")

_SEED_RE = re.compile(r"^[0-9a-f]{64}$")
_ANDROID_ID_RE = re.compile(r"^[0-9a-f]{16}$")
_UUID4_RE = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
_MAC_RE = re.compile(r"^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
_HEX16_RE = re.compile(r"^[0-9a-f]{16}$")
_HEX64_RE = re.compile(r"^[0-9a-f]{64}$")
_SERIAL_RE = re.compile(r"^[0-9A-Z]{6,16}$")
_SIM_SERIAL_RE = re.compile(r"^[0-9]{19,20}$")
_IMEI_RE = re.compile(r"^[0-9]{15}$")
_LANG_RE = re.compile(r"^[a-z]{2}$")
_COUNTRY_RE = re.compile(r"^[A-Z]{2}$")
_TIMEZONE_RE = re.compile(r"^(?:UTC|GMT|GMT0|GMT[+-]0|Greenwich|UCT|Universal|Zulu|CET|EET|MET|WET|EST|HST|MST|EST5EDT|CST6CDT|MST7MDT|PST8PDT|Cuba|Egypt|Eire|GB|GB-Eire|Hongkong|Iceland|Iran|Israel|Jamaica|Japan|Kwajalein|Libya|Navajo|NZ|NZ-CHAT|Poland|Portugal|PRC|ROC|ROK|Singapore|Turkey|W-SU|[A-Za-z]+/[A-Za-z0-9_+-]+(?:/[A-Za-z0-9_+-]+)*)$")

_UA_RE = re.compile(
    r"^Mozilla/5\.0 \(Linux; Android [0-9.]+; [^()]+\) AppleWebKit/537\.36 "
    r"\(KHTML, like Gecko\) Chrome/[0-9]+\.0\.[0-9]+\.[0-9]+ (Mobile )?Safari/537\.36$"
)

_FINGERPRINT_RE = re.compile(
    r"^(?P<brand>[^/:\s]+)/(?P<product>[^/:\s]+)/(?P<device>[^/:\s]+)"
    r":(?P<release>[0-9]+(?:\.[0-9]+){0,2})/(?P<build_id>[^/:\s]+)/(?P<incremental>[^/:\s]+)"
    r":(?P<build_type>[^/:\s]+)/(?P<tags>release-keys|dev-keys)$"
)

_REQUIRED_DEVICE_FIELDS = (
    "manufacturer",
    "brand",
    "model",
    "product",
    "device",
    "hardware",
    "fingerprint",
    "version_release",
    "sdk_int",
    "security_patch",
    "build_id",
    "version_incremental",
    "build_date_utc",
    "first_api_level",
)

# Release -> SDK defaults; validation allows both API 31 and 32 for Android 12.
_RELEASE_TO_SDK = {"8": 26, "8.0": 26, "8.1": 27, "9": 28, "10": 29, "11": 30, "12": 31, "12L": 32, "13": 33, "14": 34, "15": 35, "16": 36}

_IDENTIFIER_CHECKS = {
    "android_id": (_ANDROID_ID_RE, "16 lowercase hex characters"),
    "advertising_id": (_UUID4_RE, "a UUIDv4"),
    "app_set_id": (_UUID4_RE, "a UUIDv4"),
    "mac": (_MAC_RE, "a MAC address like AA:BB:CC:DD:EE:FF"),
    "bt_mac": (_MAC_RE, "a MAC address like AA:BB:CC:DD:EE:FF"),
    "imei_primary": (_IMEI_RE, "15 digits with a valid Luhn check digit"),
    "imei_secondary": (_IMEI_RE, "15 digits with a valid Luhn check digit"),
    "sim_serial": (_SIM_SERIAL_RE, "19-20 digits"),
    "gsf_id": (_HEX16_RE, "16 lowercase hex characters"),
    "widevine_id": (_HEX64_RE, "64 lowercase hex characters"),
    "serial": (_SERIAL_RE, "6-16 uppercase alphanumerics"),
}


@dataclass(frozen=True)
class Finding:
    severity: str
    code: str
    message: str
    field: str | None = None

    def __post_init__(self) -> None:
        if self.severity not in _SEVERITY_ORDER:
            raise ValueError("severity must be info, warning or error")
        if not re.fullmatch(r"[A-Z][A-Z0-9_]*", self.code) or not self.message.strip():
            raise ValueError("finding needs a stable reason code and explanation")


@dataclass(frozen=True)
class ValidationResult:
    """Diagnostic summary only; never part of the stored profile."""

    findings: tuple[Finding, ...]

    def __post_init__(self) -> None:
        object.__setattr__(self, "findings", tuple(sorted(self.findings, key=_finding_key)))

    @property
    def has_errors(self) -> bool:
        return has_errors(self.findings)

    @property
    def error_count(self) -> int:
        return sum(f.severity == ERROR for f in self.findings)

    @property
    def warning_count(self) -> int:
        return sum(f.severity == WARNING for f in self.findings)

    @property
    def info_count(self) -> int:
        return sum(f.severity == INFO for f in self.findings)


def _finding_key(finding: Finding) -> tuple[int, str, str, str]:
    return (_SEVERITY_ORDER[finding.severity], finding.field or "", finding.code, finding.message)


def summarize(findings: Iterable[Finding]) -> ValidationResult:
    return ValidationResult(tuple(findings))


def validate_profile_result(profile: Mapping[str, Any]) -> ValidationResult:
    return summarize(validate_profile(profile))


def has_errors(findings: Iterable[Finding]) -> bool:
    return any(finding.severity == ERROR for finding in findings)


def _add(findings: list[Finding], severity: str, code: str, message: str, field: str | None = None) -> None:
    if field is None:
        path = re.search(r"(?:device|identifiers|display|locale|telephony|ua|camera)\.[A-Za-z0-9_]+", message)
        field = path.group() if path else {
            "SCHEMA_VERSION": "schema_version", "SEED": "seed", "NAME": "name",
            "FINGERPRINT_FORMAT": "device.fingerprint", "BUILD_TYPE": "device.fingerprint",
            "TAGS": "device.fingerprint", "PATCH_DATE": "device.security_patch",
            "PATCH_FUTURE": "device.security_patch", "PATCH_AFTER_BUILD": "device.security_patch",
            "UA_MISSING": "ua", "UA_BLOCK": "ua", "TELEPHONY": "telephony", "DISPLAY": "display", "CAMERA": "camera",
        }.get(code)
    if field and not message.startswith(field):
        message = f"{field}: {message}"
    findings.append(Finding(severity, code, message, field))


def _validate_fingerprint(findings: list[Finding], device: Mapping[str, Any]) -> None:
    match = _FINGERPRINT_RE.fullmatch(str(device["fingerprint"]))
    if not match:
        _add(findings, ERROR, "FINGERPRINT_FORMAT", "fingerprint does not match the AOSP grammar")
        return
    parts = match.groupdict()
    for part, field, code in (
        ("brand", "brand", "FP_BRAND_MISMATCH"),
        ("product", "product", "FP_PRODUCT_MISMATCH"),
        ("device", "device", "FP_DEVICE_MISMATCH"),
        ("release", "version_release", "FP_RELEASE_MISMATCH"),
        ("build_id", "build_id", "FP_BUILD_ID_MISMATCH"),
        ("incremental", "version_incremental", "FP_INCREMENTAL_MISMATCH"),
    ):
        if parts[part] != str(device[field]):
            _add(findings, ERROR, code, f"fingerprint {part} disagrees with device.{field}")
    if parts["build_type"] != "user":
        _add(findings, ERROR, "BUILD_TYPE", "fingerprint build type must be 'user'")
    if parts["tags"] != "release-keys":
        _add(findings, WARNING, "TAGS", "dev-keys fingerprints look like development builds")

def _validate_versions(findings: list[Finding], device: Mapping[str, Any]) -> None:
    numbers = {}
    for field in ("sdk_int", "first_api_level"):
        value = device[field]
        if type(value) is not int:
            _add(findings, ERROR, "API_TYPE", f"device.{field} must be an integer")
        else:
            numbers[field] = value
            if not 26 <= value <= 36:
                code = "SDK_RANGE" if field == "sdk_int" else "FIRST_API_RANGE"
                _add(findings, ERROR, code, f"device.{field} must be between 26 and 36")
    sdk, first_api = numbers.get("sdk_int"), numbers.get("first_api_level")
    release = device["version_release"]
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+){0,2}", release):
        _add(findings, ERROR, "RELEASE_FORMAT", "device.version_release must be a numeric Android version such as 8.1 or 14")
    else:
        canonical = re.sub(r"(?:\.0)+$", "", release)
        expected = _RELEASE_TO_SDK.get(canonical)
        allowed = {31, 32} if canonical == "12" else {expected} if expected is not None else set()
        if not allowed:
            _add(findings, WARNING, "RELEASE_UNKNOWN", "device.version_release is not in the supported Android 8–16 release map")
        elif sdk is not None and sdk not in allowed:
            levels = "/".join(str(level) for level in sorted(allowed))
            _add(findings, ERROR, "RELEASE_SDK_MISMATCH", f"device.sdk_int must be {levels} for Android {release}; got {sdk}")
    if first_api is not None and sdk is not None and first_api > sdk:
        _add(findings, ERROR, "FIRST_API_LEVEL", "device.first_api_level cannot exceed device.sdk_int")
    model, manufacturer, brand = device["model"], device["manufacturer"].lower(), device["brand"].lower()
    expected_manufacturer = "google" if re.match(r"^Pixel(?:\s|$)", model, re.I) else "samsung" if re.match(r"^(?:SM-|GT-|Galaxy(?:\s|$))", model, re.I) else None
    aliases = {"google": {"google", "google llc", "google inc."}, "samsung": {"samsung", "samsung electronics"}}
    if expected_manufacturer and manufacturer not in aliases[expected_manufacturer]:
        _add(findings, WARNING, "MODEL_MANUFACTURER_MISMATCH", f"device.manufacturer is unusual for device.model; expected {expected_manufacturer}", "device.manufacturer")
    if brand in aliases and manufacturer in set.union(*aliases.values()) and manufacturer not in aliases[brand]:
        _add(findings, WARNING, "BRAND_MANUFACTURER_MISMATCH", "device.manufacturer disagrees with the known device.brand family", "device.manufacturer")


def _validate_patch(findings: list[Finding], device: Mapping[str, Any]) -> None:
    timestamp = device["build_date_utc"]
    built = None
    if type(timestamp) is not int or not 0 <= timestamp <= 253402300799:
        _add(findings, ERROR, "BUILD_DATE", "device.build_date_utc must be integer Unix seconds between 0 and 253402300799")
    else:
        built = datetime.fromtimestamp(timestamp, tz=timezone.utc).date()
        if built > date.today():
            _add(findings, WARNING, "BUILD_FUTURE", "device.build_date_utc is in the future")
    try:
        if not re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}", str(device["security_patch"])):
            raise ValueError("date format")
        patch = date.fromisoformat(str(device["security_patch"]))
    except (TypeError, ValueError):
        _add(findings, ERROR, "PATCH_DATE", "security_patch must be an ISO date (YYYY-MM-DD)")
        return
    if patch > date.today():
        _add(findings, WARNING, "PATCH_FUTURE", "security_patch is in the future")
    if built is not None and patch > built:
        _add(findings, WARNING, "PATCH_AFTER_BUILD", "security patch is newer than the build date")


def _validate_identifiers(findings: list[Finding], identifiers: Mapping[str, Any]) -> None:
    for key, value in sorted(identifiers.items()):
        if not isinstance(value, str):
            _add(findings, ERROR, "IDENTIFIER_FORMAT", f"identifiers.{key} must be a string", f"identifiers.{key}")
            continue
        check = _IDENTIFIER_CHECKS.get(key)
        if check is None:
            _add(findings, WARNING, "UNKNOWN_IDENTIFIER", f"identifiers.{key} is not part of the v0 identifier set", f"identifiers.{key}")
            continue
        pattern, description = check
        if not pattern.fullmatch(value):
            _add(findings, ERROR, "IDENTIFIER_FORMAT", f"identifiers.{key} must be {description}")
            continue
        if key in ("imei_primary", "imei_secondary") and not luhn_valid(value):
            _add(findings, ERROR, "IDENTIFIER_CHECKSUM", f"identifiers.{key} fails the IMEI Luhn check")
        if key in ("mac", "bt_mac") and int(value[:2], 16) & 1:
            _add(findings, ERROR, "IDENTIFIER_ADDRESS", f"identifiers.{key} must be a unicast address (first octet's low bit must be zero)")
        elif set(value.replace(":", "")) == {"0"}:
            _add(findings, WARNING, "IDENTIFIER_PLACEHOLDER", f"identifiers.{key} is an all-zero placeholder")
    if isinstance(identifiers.get("imei_primary"), str) and identifiers.get("imei_primary") and identifiers.get("imei_primary") == identifiers.get("imei_secondary"):
        _add(findings, WARNING, "IDENTIFIER_DUPLICATE", "identifiers.imei_secondary duplicates identifiers.imei_primary", "identifiers.imei_secondary")


def _validate_camera(findings: list[Finding], camera: Any) -> None:
    """Camera hardware claims: coherent counts, facings and sensor sizes."""
    if camera is None:
        return
    if not isinstance(camera, Mapping) or not isinstance(camera.get("cameras"), list):
        _add(findings, ERROR, "CAMERA", "camera must be an object with a cameras list", "camera")
        return
    cameras = camera["cameras"]
    if not 1 <= len(cameras) <= 6:
        _add(findings, ERROR, "CAMERA_COUNT", f"camera.cameras must hold 1-6 entries, got {len(cameras)}", "camera.cameras")
        return
    for index, cam in enumerate(cameras):
        if not isinstance(cam, Mapping):
            _add(findings, ERROR, "CAMERA_ENTRY", f"camera.cameras[{index}] must be an object", f"camera.cameras.{index}")
            continue
        facing = cam.get("facing")
        level = cam.get("hardware_level")
        width = cam.get("pixel_width")
        height = cam.get("pixel_height")
        if type(facing) is not int or facing not in (0, 1, 2):
            _add(findings, ERROR, "CAMERA_FACING", f"camera.cameras[{index}].facing must be 0 (front), 1 (back) or 2 (external)", f"camera.cameras.{index}.facing")
        if type(level) is not int or not 0 <= level <= 4:
            _add(findings, ERROR, "CAMERA_LEVEL", f"camera.cameras[{index}].hardware_level must be an integer in 0-4", f"camera.cameras.{index}.hardware_level")
        for name, value, bounds in (("pixel_width", width, (320, 20000)), ("pixel_height", height, (240, 20000))):
            if type(value) is not int or not bounds[0] <= value <= bounds[1]:
                _add(findings, ERROR, "CAMERA_SIZE", f"camera.cameras[{index}].{name} must be an integer in {bounds[0]}-{bounds[1]}", f"camera.cameras.{index}.{name}")
        if type(width) is int and type(height) is int and width > 0 and height > 0 and not 0.5 <= width / height <= 2.0:
            _add(findings, WARNING, "CAMERA_ASPECT", f"camera.cameras[{index}] aspect ratio {width}/{height} is unusual for a phone sensor", f"camera.cameras.{index}.pixel_width")


def _validate_locale(findings: list[Finding], locale: Mapping[str, Any]) -> None:
    language = locale.get("language")
    country = locale.get("country")
    tz_name = locale.get("timezone")
    if "language" in locale and (not isinstance(language, str) or not _LANG_RE.fullmatch(language)):
        _add(findings, ERROR, "LOCALE", "locale.language must be a two-letter lowercase code")
    if "country" in locale and (not isinstance(country, str) or not _COUNTRY_RE.fullmatch(country)):
        _add(findings, ERROR, "LOCALE", "locale.country must be a two-letter uppercase code")
    if "timezone" in locale and (not isinstance(tz_name, str) or not _TIMEZONE_RE.fullmatch(tz_name)):
        _add(findings, ERROR, "LOCALE", "locale.timezone must be an IANA zone like Europe/Berlin")


def validate_profile(profile: Mapping[str, Any]) -> list[Finding]:
    findings: list[Finding] = []
    if not isinstance(profile, Mapping):
        _add(findings, ERROR, "BLOCK_TYPE", "profile must be an object", "profile")
        return sorted(findings, key=_finding_key)
    def block(key: str) -> Mapping[str, Any]:
        value = profile.get(key, {})
        if not isinstance(value, Mapping):
            _add(findings, ERROR, "BLOCK_TYPE", f"{key} must be an object", key)
            return {}
        return value
    if profile.get("schema_version") != SCHEMA_VERSION and profile.get("schema_version") not in _LEGACY_SCHEMA_VERSIONS:
        _add(findings, ERROR, "SCHEMA_VERSION", f"schema_version must be {SCHEMA_VERSION}")
    if not isinstance(profile.get("seed"), str) or not _SEED_RE.fullmatch(profile["seed"]):
        _add(findings, ERROR, "SEED", "seed must be 64 lowercase hex characters")
    name = profile.get("name")
    if not isinstance(name, str) or not name.strip():
        _add(findings, ERROR, "NAME", "profile name must be a non-empty string")

    device = block("device")
    for field_name in _REQUIRED_DEVICE_FIELDS:
        value = device.get(field_name)
        if value is None or value == "":
            _add(findings, ERROR, "MISSING_DEVICE_FIELD", f"device.{field_name} is required")
        elif field_name not in ("sdk_int", "first_api_level", "build_date_utc") and (not isinstance(value, str) or not value.strip() or any(ord(c) < 32 or ord(c) == 127 for c in value)):
            _add(findings, ERROR, "DEVICE_FIELD", f"device.{field_name} must be non-blank text without control characters")
    if has_errors(findings):
        return sorted(findings, key=_finding_key)

    from .reality import validate as validate_reality
    findings.extend(Finding(severity, code, message, field) for severity, code, field, message in validate_reality(profile))
    from .instance_realism import validate as validate_realism
    findings.extend(validate_realism(profile))
    from .play_surface import validate_profile_section
    findings.extend(Finding(ERROR, issue.code, issue.message, issue.field) for issue in validate_profile_section(profile))
    _validate_fingerprint(findings, device)
    _validate_versions(findings, device)
    _validate_patch(findings, device)
    _validate_identifiers(findings, block("identifiers"))
    _validate_telephony(findings, block("telephony"))
    _validate_display(findings, block("display"))
    _validate_camera(findings, profile.get("camera"))
    _validate_locale(findings, block("locale"))
    _validate_network(findings, block("network"))
    _validate_prop_lengths(findings, profile)
    telephony = profile.get("telephony", {})
    _validate_hardware(findings, device, telephony if isinstance(telephony, Mapping) else {})
    ua = profile.get("ua")
    if ua is None:
        _add(findings, WARNING, "UA_MISSING", "profile carries no user agent block")
    else:
        _validate_ua(findings, ua, device)
    return sorted(findings, key=_finding_key)


def validate_catalog_metadata(record: Mapping[str, Any]) -> list[Finding]:
    """Optional corpus claims; absence is not a finding and never implies proof."""
    findings: list[Finding] = []
    for field in ("confidence", "source_confidence"):
        if field in record and record[field] not in ("public reference", "approximated", "needs verification"):
            _add(findings, ERROR, "SOURCE_CONFIDENCE", f"{field} must be public reference, approximated, or needs verification", field)
    for field in ("camera_class", "board"):
        if field in record and (not isinstance(record[field], str) or not record[field].strip()
                                or any(ord(c) < 32 or ord(c) == 127 for c in record[field])):
            _add(findings, ERROR, "CORPUS_TEXT", f"{field} must be non-blank text without control characters", field)
    for field in ("expected_capability_set", "sensor_class_list", "sources"):
        if field in record:
            value = record[field]
            if not isinstance(value, list) or any(not isinstance(v, str) or not v.strip() for v in value):
                _add(findings, ERROR, "CORPUS_LIST", f"{field} must be a list of non-blank strings", field)
            elif len(value) != len(set(value)):
                _add(findings, ERROR, "CORPUS_LIST_DUPLICATE", f"{field} must not contain duplicates", field)
    if "drm_security_level" in record and record["drm_security_level"] not in ("L1", "L2", "L3"):
        _add(findings, ERROR, "DRM_SECURITY_LEVEL", "drm_security_level must be L1, L2, or L3", "drm_security_level")
    return sorted(findings, key=_finding_key)


def validate_catalog_record(record: Mapping[str, Any]) -> list[Finding]:
    """Run profile coherence on template blocks without changing generated data."""
    from .forge import build_ua
    from .reality import at, user_agent
    profile = {key: record[key] for key in ("device", "display", "telephony", "network", "locale", "camera", "reality") if key in record}
    profile.update(schema_version="0.4", seed="ab" * 32, name="Catalog validation")
    device = record.get("device", {})
    if isinstance(device, Mapping) and all(isinstance(device.get(k), str) for k in ("model", "version_release")):
        display = record.get("display", {})
        screen_class = display.get("screen_size_class", "large") if isinstance(display, Mapping) else "large"
        declared_ua = at(record, 'reality.webview.values.user_agent')
        profile["ua"] = {"value": declared_ua if isinstance(declared_ua, str) else user_agent(record.get('reality', {}), build_ua(device, screen_class, "ab" * 32))}
    findings = validate_profile(profile) + validate_catalog_metadata(record)
    display = record.get("display", {})
    if isinstance(display, Mapping):
        w, h = display.get("width"), display.get("height")
        if type(w) is int and type(h) is int and min(w, h) > 0 and max(w, h) > 4 * min(w, h):
            _add(findings, ERROR, "CATALOG_DISPLAY_ASPECT", "display.width and display.height imply an aspect ratio above 4:1 for a phone template", "display.width")
    return sorted(findings, key=_finding_key)



def _validate_ua(findings: list[Finding], ua: Mapping[str, Any], device: Mapping[str, Any]) -> None:
    if not isinstance(ua, Mapping):
        _add(findings, ERROR, "UA_BLOCK", "ua must be an object")
        return
    value = ua.get("value")
    if not isinstance(value, str) or not _UA_RE.match(value):
        _add(
            findings,
            ERROR,
            "UA_FORMAT",
            "ua.value must be a Chrome-on-Android user agent string",
        )
        return
    model = str(device["model"])
    release = str(device["version_release"])
    if f"; {model})" not in value:
        _add(findings, ERROR, "UA_MODEL_MISMATCH", "ua.value model disagrees with device.model")
    if f"Android {release};" not in value:
        _add(findings, ERROR, "UA_RELEASE_MISMATCH", "ua.value Android release disagrees with device.version_release")


def _validate_telephony(findings: list[Finding], telephony: Mapping[str, Any]) -> None:
    if not telephony:
        return
    if not isinstance(telephony, Mapping):
        _add(findings, ERROR, "TELEPHONY", "telephony must be an object")
        return
    carrier = telephony.get("carrier_name")
    if not isinstance(carrier, str) or not carrier.strip():
        _add(findings, ERROR, "TELEPHONY_CARRIER", "telephony.carrier_name must be a non-empty string")
    elif any(ord(c) < 32 or ord(c) == 127 for c in carrier):
        _add(findings, ERROR, "TELEPHONY_CARRIER_FORMAT", "telephony.carrier_name must not contain control characters")
    if not _MCC_RE.fullmatch(str(telephony.get("mcc", ""))):
        _add(findings, ERROR, "TELEPHONY_MCC", "telephony.mcc must be three digits")
    if not _MNC_RE.fullmatch(str(telephony.get("mnc", ""))):
        _add(findings, ERROR, "TELEPHONY_MNC", "telephony.mnc must be two or three digits")
    if str(telephony.get("network_type", "")) not in _NETWORK_TYPES:
        _add(findings, ERROR, "TELEPHONY_NETWORK_TYPE", "telephony.network_type must be one of 5G, LTE, HSPA, UMTS, GSM")
    if str(telephony.get("sim_slot_count", 1)) not in ("1", "2"):
        _add(findings, ERROR, "TELEPHONY_SLOTS", "telephony.sim_slot_count must be 1 or 2")
    if telephony.get("mcc") == "000":
        _add(findings, INFO, "TELEPHONY_PLACEHOLDER_OPERATOR", "telephony.mcc is all zero; confirm that a placeholder operator is intended")


def _validate_network(findings: list[Finding], network: Mapping[str, Any]) -> None:
    if "egress_hint" not in network:
        return
    hint = network["egress_hint"]
    if not isinstance(hint, str) or len(hint) > 128 or any(ord(c) < 32 or ord(c) == 127 for c in hint):
        _add(findings, ERROR, "NETWORK_HINT_FORMAT", "network.egress_hint must be text of at most 128 characters without control characters", "network.egress_hint")


def _validate_hardware(findings: list[Finding], device: Mapping[str, Any], telephony: Mapping[str, Any]) -> None:
    # Existing schema has no camera/sensor feature flags. Do not infer capabilities
    # from OEM names, physical SIM slots, or the mere presence of identifier values.
    for field in ("bootloader", "baseband"):
        if field in device:
            value = device[field]
            if not isinstance(value, str) or any(ord(c) < 32 or ord(c) == 127 for c in value):
                _add(findings, ERROR, "HARDWARE_FIELD_FORMAT", f"device.{field} must be text without control characters")
    if device.get("hardware", "").lower() in ("unknown", "generic"):
        _add(findings, INFO, "HARDWARE_UNSPECIFIED", "device.hardware does not identify a specific hardware platform; capability consistency cannot be established")
    baseband = device.get("baseband")
    if telephony.get("network_type") in _NETWORK_TYPES and isinstance(baseband, str) and baseband.strip().lower() in ("none", "unknown", "n/a"):
        _add(findings, WARNING, "HARDWARE_MODEM_UNSPECIFIED", "device.baseband does not identify a modem although telephony.network_type declares cellular service")
    first_api = device.get("first_api_level")
    if telephony.get("network_type") == "5G" and type(first_api) is int and first_api < 29:
        _add(findings, WARNING, "HARDWARE_NETWORK_GENERATION", "telephony.network_type declares 5G on hardware launched before API 29; confirm modem support", "telephony.network_type")


def _validate_display(findings: list[Finding], display: Mapping[str, Any]) -> None:
    if not display:
        return
    if not isinstance(display, Mapping):
        _add(findings, ERROR, "DISPLAY", "display must be an object")
        return
    for field in ("width", "height", "density", "refresh_rate_hz"):
        value = display.get(field)
        if type(value) is not int or value <= 0:
            _add(findings, ERROR, "DISPLAY_FIELD", f"display.{field} must be a positive integer")
            continue
        bounds = {"width": (120, 8192), "height": (120, 8192), "density": (72, 1000), "refresh_rate_hz": (24, 240)}
        low, high = bounds[field]
        if not low <= value <= high:
            _add(findings, WARNING, "DISPLAY_SUSPICIOUS", f"display.{field} is outside the usual range {low}–{high}")
    width, height, density = (display.get(field) for field in ("width", "height", "density"))
    if all(type(value) is int and value > 0 for value in (width, height, density)):
        if max(width, height) > 4 * min(width, height):
            _add(findings, WARNING, "DISPLAY_ASPECT", "display.width and display.height imply an aspect ratio above 4:1", "display.width")
        if not 120 * density <= min(width, height) * 160 <= 1600 * density:
            _add(findings, WARNING, "DISPLAY_DENSITY_MISMATCH", "display.density implies a shortest edge outside 120–1600 dp for these dimensions", "display.density")
    if str(display.get("screen_size_class", "")) not in _DISPLAY_CLASSES:
        _add(findings, ERROR, "DISPLAY_CLASS", "display.screen_size_class must be small, normal, or large")


# Android's property buffer is PROP_VALUE_MAX (92) bytes including the NUL, so
# a value the runtime serves through a system-property surface must fit in 91
# characters. Values beyond that cannot exist on a real device either.
_PROP_FED_BLOCKS = ("device", "telephony", "locale", "identifiers")
_PROP_VALUE_LIMIT = 91


def _validate_prop_lengths(findings: list[Finding], profile: Mapping[str, Any]) -> None:
    for block_name in _PROP_FED_BLOCKS:
        block_data = profile.get(block_name, {})
        if not isinstance(block_data, Mapping):
            continue
        for key, value in block_data.items():
            if isinstance(value, str) and len(value) > _PROP_VALUE_LIMIT:
                _add(
                    findings,
                    ERROR,
                    "PROP_LENGTH",
                    f"{block_name}.{key} exceeds the { _PROP_VALUE_LIMIT }-character property buffer limit",
                )


_MCC_RE = re.compile(r"^[0-9]{3}$")
_MNC_RE = re.compile(r"^[0-9]{2,3}$")
_NETWORK_TYPES = {"5G", "LTE", "HSPA", "UMTS", "GSM"}
_DISPLAY_CLASSES = {"small", "normal", "large"}
