"""Hard expectations for valid, suspicious and malformed profile inputs."""

import copy
import json

import pytest

from cloak_forge import Finding, ValidationResult, forge_profile, has_errors, summarize, validate_profile, validate_profile_result


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
    findings = validate_profile(value)
    assert not has_errors(findings)
    # This fixture launches at API 26 but retains 5G; valid, with a modem warning.
    assert {f.code for f in findings} == {"HARDWARE_NETWORK_GENERATION"}


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


@pytest.mark.parametrize("path,value,severity,code", [
    ("device.manufacturer", "Samsung", "warning", "MODEL_MANUFACTURER_MISMATCH"),
    ("device.sdk_int", 34, "error", "RELEASE_SDK_MISMATCH"),
    ("device.brand", "other", "error", "FP_BRAND_MISMATCH"),
    ("display.height", 6000, "warning", "DISPLAY_ASPECT"),
    ("display.density", 1600, "warning", "DISPLAY_DENSITY_MISMATCH"),
    ("locale.timezone", "Europe//Berlin", "error", "LOCALE"),
    ("identifiers.serial", "bad serial", "error", "IDENTIFIER_FORMAT"),
    ("telephony.carrier_name", "Carrier\nInjected", "error", "TELEPHONY_CARRIER_FORMAT"),
    ("telephony.carrier_name", 123, "error", "TELEPHONY_CARRIER"),
    ("telephony.mcc", "310\n", "error", "TELEPHONY_MCC"),
    ("telephony.mcc", "٣١٠", "error", "TELEPHONY_MCC"),
    ("telephony.mnc", "260\n", "error", "TELEPHONY_MNC"),
    ("telephony.mcc", "000", "info", "TELEPHONY_PLACEHOLDER_OPERATOR"),
    ("network.egress_hint", "x" * 129, "error", "NETWORK_HINT_FORMAT"),
    ("network.egress_hint", "region\x00", "error", "NETWORK_HINT_FORMAT"),
    ("network.egress_hint", None, "error", "NETWORK_HINT_FORMAT"),
    ("device.bootloader", 123, "error", "HARDWARE_FIELD_FORMAT"),
    ("device.baseband", "modem\n", "error", "HARDWARE_FIELD_FORMAT"),
    ("device.baseband", "none", "warning", "HARDWARE_MODEM_UNSPECIFIED"),
    ("device.first_api_level", 28, "warning", "HARDWARE_NETWORK_GENERATION"),
    ("device.hardware", "generic", "info", "HARDWARE_UNSPECIFIED"),
])
def test_coherence_rule_groups_have_stable_reasons(path, value, severity, code):
    findings = validate_profile(change(profile(), path, value))
    field = {"DISPLAY_ASPECT": "display.width", "HARDWARE_NETWORK_GENERATION": "telephony.network_type"}.get(code, path)
    finding = next(f for f in findings if f.code == code and f.field == field)
    assert finding.severity == severity
    assert finding.message.startswith(field)
    assert has_errors(findings) == (severity == "error")


def test_summary_orders_by_severity_field_code_and_message_and_is_immutable():
    findings = [Finding("info", "NOTE", "note", "a"),
                Finding("warning", "WARN", "warning", "a"),
                Finding("error", "Z_CODE", "z reason", "b"),
                Finding("error", "A_CODE", "b reason", "b"),
                Finding("error", "A_CODE", "a reason", "b"),
                Finding("error", "ROOT", "root reason"),
                Finding("error", "LAST", "field first", "a")]
    result = summarize(findings)
    assert result == ValidationResult(tuple(reversed(findings)))
    assert [(f.field, f.code, f.message) for f in result.findings[:5]] == [
        (None, "ROOT", "root reason"), ("a", "LAST", "field first"),
        ("b", "A_CODE", "a reason"), ("b", "A_CODE", "b reason"), ("b", "Z_CODE", "z reason")]
    assert (result.error_count, result.warning_count, result.info_count, result.has_errors) == (5, 1, 1, True)
    findings.clear()
    assert len(result.findings) == 7
    with pytest.raises(AttributeError):
        result.findings = ()


def test_profile_summary_and_list_api_match_without_mutating_input():
    value = profile()
    value["device"].update(hardware="generic", baseband="none")
    value["display"]["width"] = 0
    before = copy.deepcopy(value)
    result = validate_profile_result(value)
    assert tuple(validate_profile(value)) == result.findings
    assert (result.error_count, result.warning_count, result.info_count) == (1, 1, 1)
    assert value == before


@pytest.mark.parametrize("kwargs", [dict(severity="fatal", code="CODE", message="reason"),
    dict(severity="info", code="bad-code", message="reason"),
    dict(severity="warning", code="CODE", message=" ")])
def test_finding_rejects_unstable_severity_code_or_empty_explanation(kwargs):
    with pytest.raises(ValueError):
        Finding(**kwargs)


def test_absent_optional_hardware_and_network_fields_remain_valid():
    value = profile()
    value["device"].pop("baseband")
    value["device"].pop("bootloader")
    value.pop("network")
    assert validate_profile_result(value).findings == ()
    # An egress hint is opaque text, not a URL, IP address or locale binding.
    value["network"] = {"egress_hint": "😀" * 128}
    assert validate_profile(value) == []


def test_invalid_telephony_block_reports_once():
    findings = validate_profile(change(profile(), "telephony", []))
    assert len([f for f in findings if f.code == "BLOCK_TYPE" and f.field == "telephony"]) == 1

def test_prop_fed_values_must_fit_the_property_buffer():
    long_value = "x" * 92
    findings = validate_profile(change(profile(), "device.build_id", long_value))
    codes = [f.code for f in findings]
    assert "PROP_LENGTH" in codes
    assert has_errors(findings)

    # At the limit itself the profile stays valid.
    ok = validate_profile(change(profile(), "device.build_id", "x" * 91))
    assert not [f for f in ok if f.code == "PROP_LENGTH"]
