"""Coherence validation for Cloak profiles.

A profile is only as strong as its least consistent field: every rule here mirrors a cross-check
an anti-fraud SDK can run against a real device. The forge refuses to emit a profile that fails
any ERROR finding.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import date, datetime, timezone
from typing import Any, Mapping

from .derive import luhn_valid

ERROR = "error"
WARNING = "warning"

SCHEMA_VERSION = "0.2"

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
_TIMEZONE_RE = re.compile(r"^[A-Za-z0-9_+-]+(/[A-Za-z0-9_+-]+)*$")

_FINGERPRINT_RE = re.compile(
    r"^(?P<brand>[^/:\s]+)/(?P<product>[^/:\s]+)/(?P<device>[^/:\s]+)"
    r":(?P<release>[0-9.]+)/(?P<build_id>[^/]+)/(?P<incremental>[^:/]+)"
    r":(?P<build_type>[^:/]+)/(?P<tags>release-keys|dev-keys)$"
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

# Unambiguous release -> SDK pairs. Older devices (SDK 26-29) are skipped
# because their release strings vary across OEMs.
_RELEASE_TO_SDK = {"11": 30, "12": 31, "12L": 32, "13": 33, "14": 34, "15": 35, "16": 36}

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


def has_errors(findings: list[Finding]) -> bool:
    return any(finding.severity == ERROR for finding in findings)


def _add(findings: list[Finding], severity: str, code: str, message: str) -> None:
    findings.append(Finding(severity, code, message))


def _validate_fingerprint(findings: list[Finding], device: Mapping[str, Any]) -> None:
    match = _FINGERPRINT_RE.match(str(device["fingerprint"]))
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

    sdk = int(device["sdk_int"])
    if not 26 <= sdk <= 36:
        _add(findings, ERROR, "SDK_RANGE", "sdk_int must be between 26 (Android 8.0) and 36 (Android 16)")
    expected_sdk = _RELEASE_TO_SDK.get(str(device["version_release"]))
    if expected_sdk is not None and expected_sdk != sdk:
        _add(
            findings,
            ERROR,
            "RELEASE_SDK_MISMATCH",
            f"release '{device['version_release']}' implies SDK {expected_sdk}, profile says {sdk}",
        )
    first_api = int(device["first_api_level"])
    if first_api > sdk:
        _add(findings, ERROR, "FIRST_API_LEVEL", "first_api_level cannot exceed sdk_int")


def _validate_patch(findings: list[Finding], device: Mapping[str, Any]) -> None:
    try:
        patch = date.fromisoformat(str(device["security_patch"]))
    except (TypeError, ValueError):
        _add(findings, ERROR, "PATCH_DATE", "security_patch must be an ISO date (YYYY-MM-DD)")
        return
    if patch > date.today():
        _add(findings, WARNING, "PATCH_FUTURE", "security_patch is in the future")
    try:
        built = datetime.fromtimestamp(int(device["build_date_utc"]), tz=timezone.utc).date()
    except (OverflowError, OSError, ValueError):
        return
    if patch > built:
        _add(findings, WARNING, "PATCH_AFTER_BUILD", "security patch is newer than the build date")


def _validate_identifiers(findings: list[Finding], identifiers: Mapping[str, Any]) -> None:
    for key, value in identifiers.items():
        if not isinstance(value, str):
            _add(findings, ERROR, "IDENTIFIER_FORMAT", f"identifiers.{key} must be a string")
            continue
        check = _IDENTIFIER_CHECKS.get(key)
        if check is None:
            _add(findings, WARNING, "UNKNOWN_IDENTIFIER", f"identifiers.{key} is not part of the v0 identifier set")
            continue
        pattern, description = check
        if not pattern.match(value):
            _add(findings, ERROR, "IDENTIFIER_FORMAT", f"identifiers.{key} must be {description}")
            continue
        if key in ("imei_primary", "imei_secondary") and not luhn_valid(value):
            _add(findings, ERROR, "IDENTIFIER_CHECKSUM", f"identifiers.{key} fails the IMEI Luhn check")


def _validate_locale(findings: list[Finding], locale: Mapping[str, Any]) -> None:
    language = locale.get("language")
    country = locale.get("country")
    tz_name = locale.get("timezone")
    if language is not None and not _LANG_RE.match(str(language)):
        _add(findings, ERROR, "LOCALE", "locale.language must be a two-letter lowercase code")
    if country is not None and not _COUNTRY_RE.match(str(country)):
        _add(findings, ERROR, "LOCALE", "locale.country must be a two-letter uppercase code")
    if tz_name is not None and not _TIMEZONE_RE.match(str(tz_name)):
        _add(findings, ERROR, "LOCALE", "locale.timezone must be an IANA zone like Europe/Berlin")


def validate_profile(profile: Mapping[str, Any]) -> list[Finding]:
    findings: list[Finding] = []
    if profile.get("schema_version") != SCHEMA_VERSION:
        _add(findings, ERROR, "SCHEMA_VERSION", f"schema_version must be {SCHEMA_VERSION}")
    if not _SEED_RE.match(str(profile.get("seed", ""))):
        _add(findings, ERROR, "SEED", "seed must be 64 lowercase hex characters")
    name = profile.get("name")
    if not isinstance(name, str) or not name.strip():
        _add(findings, ERROR, "NAME", "profile name must be a non-empty string")

    device = profile.get("device") or {}
    for field_name in _REQUIRED_DEVICE_FIELDS:
        value = device.get(field_name)
        if value is None or value == "":
            _add(findings, ERROR, "MISSING_DEVICE_FIELD", f"device.{field_name} is required")
    if has_errors(findings):
        return findings

    _validate_fingerprint(findings, device)
    _validate_patch(findings, device)
    _validate_identifiers(findings, profile.get("identifiers") or {})
    _validate_telephony(findings, profile.get("telephony") or {})
    _validate_display(findings, profile.get("display") or {})
    _validate_locale(findings, profile.get("locale") or {})
    return findings



def _validate_telephony(findings: list[Finding], telephony: Mapping[str, Any]) -> None:
    if not telephony:
        return
    if not isinstance(telephony, Mapping):
        _add(findings, ERROR, "TELEPHONY", "telephony must be an object")
        return
    carrier = telephony.get("carrier_name")
    if not isinstance(carrier, str) or not carrier.strip():
        _add(findings, ERROR, "TELEPHONY_CARRIER", "telephony.carrier_name must be a non-empty string")
    if not _MCC_RE.match(str(telephony.get("mcc", ""))):
        _add(findings, ERROR, "TELEPHONY_MCC", "telephony.mcc must be three digits")
    if not _MNC_RE.match(str(telephony.get("mnc", ""))):
        _add(findings, ERROR, "TELEPHONY_MNC", "telephony.mnc must be two or three digits")
    if str(telephony.get("network_type", "")) not in _NETWORK_TYPES:
        _add(findings, ERROR, "TELEPHONY_NETWORK_TYPE", "telephony.network_type must be one of 5G, LTE, HSPA, UMTS, GSM")
    if str(telephony.get("sim_slot_count", 1)) not in ("1", "2"):
        _add(findings, ERROR, "TELEPHONY_SLOTS", "telephony.sim_slot_count must be 1 or 2")


def _validate_display(findings: list[Finding], display: Mapping[str, Any]) -> None:
    if not display:
        return
    if not isinstance(display, Mapping):
        _add(findings, ERROR, "DISPLAY", "display must be an object")
        return
    for field in ("width", "height", "density", "refresh_rate_hz"):
        value = display.get(field)
        if not isinstance(value, int) or value <= 0:
            _add(findings, ERROR, "DISPLAY_FIELD", f"display.{field} must be a positive integer")
            continue
    if str(display.get("screen_size_class", "")) not in _DISPLAY_CLASSES:
        _add(findings, ERROR, "DISPLAY_CLASS", "display.screen_size_class must be small, normal, or large")


_MCC_RE = re.compile(r"^\d{3}$")
_MNC_RE = re.compile(r"^\d{2,3}$")
_NETWORK_TYPES = {"5G", "LTE", "HSPA", "UMTS", "GSM"}
_DISPLAY_CLASSES = {"small", "normal", "large"}
