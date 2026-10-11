"""Hard expectations for valid, suspicious and malformed profile inputs."""

import copy
import json

import pytest

from cloak_forge import forge_profile, has_errors, validate_profile


def profile():
    return copy.deepcopy(forge_profile("Validation test", "ab" * 32, "pixel_7"))


def change(value, path, replacement):
    target = value
    *parents, leaf = path.split(".")
    for part in parents:
        target = target[part]
    target[leaf] = replacement
    return value


@pytest.mark.parametrize("path,value,code", [
    ("device.sdk_int", True, "API_TYPE"),
    ("device.sdk_int", "33", "API_TYPE"),
    ("device.sdk_int", 33.5, "API_TYPE"),
    ("device.sdk_int", 2**40, "SDK_RANGE"),
    ("device.first_api_level", 0, "FIRST_API_RANGE"),
    ("device.first_api_level", "oops", "API_TYPE"),
    ("device.first_api_level", 34, "FIRST_API_LEVEL"),
    ("device.build_date_utc", None, "MISSING_DEVICE_FIELD"),
    ("device.build_date_utc", "oops", "BUILD_DATE"),
    ("device.build_date_utc", -1, "BUILD_DATE"),
    ("device.build_date_utc", 253402300800, "BUILD_DATE"),
    ("device.version_release", "13..0", "RELEASE_FORMAT"),
    ("device.model", 123, "DEVICE_FIELD"),
    ("device.model", "   ", "DEVICE_FIELD"),
    ("device.security_patch", "20230805", "PATCH_DATE"),
    ("device.security_patch", "2023-02-29", "PATCH_DATE"),
    ("display.width", True, "DISPLAY_FIELD"),
    ("display.width", 1080.5, "DISPLAY_FIELD"),
    ("display.density", "420", "DISPLAY_FIELD"),
    ("display.height", -1, "DISPLAY_FIELD"),
    ("locale.language", "en\n", "LOCALE"),
    ("locale.language", None, "LOCALE"),
    ("locale.country", "usa", "LOCALE"),
    ("locale.timezone", "Europe//Berlin", "LOCALE"),
    ("locale.timezone", "Europe/../Berlin", "LOCALE"),
    ("locale.timezone", "NotAZone", "LOCALE"),
    ("identifiers.android_id", "0123456789abcdef\n", "IDENTIFIER_FORMAT"),
    ("identifiers.app_set_id", "00000000-0000-1000-8000-000000000000", "IDENTIFIER_FORMAT"),
    ("identifiers.mac", "01:23:45:67:89:AB", "IDENTIFIER_ADDRESS"),
    ("identifiers.bt_mac", "FF:FF:FF:FF:FF:FF", "IDENTIFIER_ADDRESS"),
    ("identifiers.imei_primary", "490154203237519", "IDENTIFIER_CHECKSUM"),
    ("identifiers", [], "BLOCK_TYPE"),
    ("display", "1080x2400", "BLOCK_TYPE"),
    ("locale", False, "BLOCK_TYPE"),
    ("seed", int("1" * 64), "SEED"),
])
def test_invalid_inputs_report_code_field_and_reason(path, value, code):
    findings = validate_profile(change(profile(), path, value))
    finding = next(f for f in findings if f.code == code and f.field == path)
    assert finding.severity == "error"
    assert finding.message.startswith(path)
    assert has_errors(findings)
    # Machine-readable results are independent of profile storage/schema.
    assert json.loads(json.dumps(finding.__dict__))["field"] == path


@pytest.mark.parametrize("path,value,code", [
    ("device.manufacturer", "Samsung", "MODEL_MANUFACTURER_MISMATCH"),
    ("display.width", 100, "DISPLAY_SUSPICIOUS"),
    ("display.density", 2000, "DISPLAY_SUSPICIOUS"),
    ("display.refresh_rate_hz", 500, "DISPLAY_SUSPICIOUS"),
    ("display.height", 6000, "DISPLAY_ASPECT"),
    ("identifiers.android_id", "0" * 16, "IDENTIFIER_PLACEHOLDER"),
])
def test_suspicious_values_warn_without_rejecting(path, value, code):
    findings = validate_profile(change(profile(), path, value))
    assert any(f.code == code and f.severity == "warning" for f in findings)
    assert not has_errors(findings)


@pytest.mark.parametrize("release,sdk", [("8.0", 26), ("8.1", 27), ("9", 28),
    ("10", 29), ("11", 30), ("12", 31), ("12", 32), ("13", 33),
    ("14", 34), ("15", 35), ("16", 36), ("13.0", 33)])
def test_supported_release_pairs_are_valid(release, sdk):
    value = profile()
    device = value["device"]
    device.update(version_release=release, sdk_int=sdk, first_api_level=26)
    device["fingerprint"] = device["fingerprint"].replace(":13/", f":{release}/")
    value["ua"]["value"] = value["ua"]["value"].replace("Android 13;", f"Android {release};")
    assert validate_profile(value) == []


def test_broken_fingerprint_does_not_hide_independent_api_failures():
    value = profile()
    value["device"].update(fingerprint="broken", sdk_int="invalid", first_api_level=1)
    assert {"FINGERPRINT_FORMAT", "API_TYPE", "FIRST_API_RANGE"} <= {f.code for f in validate_profile(value)}


@pytest.mark.parametrize("zone", ["UTC", "Japan", "NZ-CHAT", "GMT+0", "Etc/GMT+5", "America/Argentina/Buenos_Aires", "Europe/Berlin"])
def test_valid_timezone_formats(zone):
    assert validate_profile(change(profile(), "locale.timezone", zone)) == []


def test_density_relation_is_reported_on_density_and_landscape_is_valid():
    value = profile()
    value["display"].update(width=2400, height=1080)
    assert validate_profile(value) == []
    value["display"]["density"] = 1600
    findings = validate_profile(value)
    assert any(f.code == "DISPLAY_DENSITY_MISMATCH" and f.field == "display.density" for f in findings)


def test_unknown_manufacturer_is_not_rejected_and_inputs_are_not_mutated():
    value = profile()
    value["device"].update(manufacturer="Acme", model="Acme Phone")
    value["ua"]["value"] = value["ua"]["value"].replace("Pixel 7", "Acme Phone")
    before = copy.deepcopy(value)
    assert validate_profile(value) == []
    assert value == before


def test_duplicate_imeis_warn():
    value = profile()
    value["identifiers"]["imei_secondary"] = value["identifiers"]["imei_primary"]
    assert any(f.code == "IDENTIFIER_DUPLICATE" and f.field == "identifiers.imei_secondary" for f in validate_profile(value))
