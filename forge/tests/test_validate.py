import cloak_forge

SEED = "7c1e" * 16

PANTHER_PROPS = """\
# comment lines and blanks must be ignored

ro.product.brand=google
ro.product.device=panther
ro.product.manufacturer=Google
ro.product.model=Pixel 7
ro.product.name=panther
ro.build.fingerprint=google/panther/panther:13/TQ3A.230805.001/10193186:user/release-keys
ro.build.id=TQ3A.230805.001
ro.build.version.incremental=10193186
ro.build.version.release=13
ro.build.version.sdk=33
ro.build.version.security_patch=2023-08-05
ro.build.date.utc=1691712000
ro.product.first_api_level=33
ro.hardware=gs101
ro.bootloader=panther-1.0-8769422
"""


def make_profile(**overrides):
    profile = cloak_forge.draft_profile("Pixel 7 - vault A", SEED, PANTHER_PROPS)
    profile.update(overrides)
    return profile


def test_panther_dump_yields_coherent_profile():
    profile = make_profile()
    assert cloak_forge.validate_profile(profile) == []


def test_draft_profile_is_deterministic():
    again = cloak_forge.draft_profile("Pixel 7 - vault A", SEED, PANTHER_PROPS)
    assert again["id"] == make_profile()["id"]


def test_brand_mismatch_is_caught():
    profile = make_profile()
    profile["device"]["brand"] = "samsung"
    findings = cloak_forge.validate_profile(profile)
    assert "FP_BRAND_MISMATCH" in [f.code for f in findings]
    assert cloak_forge.has_errors(findings)


def test_sdk_mismatch_is_caught():
    profile = make_profile()
    profile["device"]["sdk_int"] = 34
    codes = [f.code for f in cloak_forge.validate_profile(profile)]
    assert "RELEASE_SDK_MISMATCH" in codes


def test_future_patch_warns():
    profile = make_profile()
    profile["device"]["security_patch"] = "2030-01-01"
    codes = [f.code for f in cloak_forge.validate_profile(profile)]
    assert "PATCH_FUTURE" in codes
    assert not cloak_forge.has_errors(cloak_forge.validate_profile(profile))


def test_dev_keys_warns():
    profile = make_profile()
    profile["device"]["fingerprint"] = (
        "google/panther/panther:13/TQ3A.230805.001/10193186:user/dev-keys"
    )
    codes = [f.code for f in cloak_forge.validate_profile(profile)]
    assert "TAGS" in codes
    assert not cloak_forge.has_errors(cloak_forge.validate_profile(profile))


def test_missing_required_field_short_circuits():
    profile = make_profile()
    profile["device"]["fingerprint"] = ""
    findings = cloak_forge.validate_profile(profile)
    assert "MISSING_DEVICE_FIELD" in [f.code for f in findings]


def test_identifier_format_and_luhn():
    profile = make_profile(
        identifiers={
            "android_id": cloak_forge.derive_android_id(SEED),
            "imei_primary": "490154203237518",  # valid Luhn
            "imei_secondary": "490154203237519",  # bad check digit
        }
    )
    codes = [f.code for f in cloak_forge.validate_profile(profile)]
    assert "IDENTIFIER_CHECKSUM" in codes


def test_unknown_identifier_warns():
    profile = make_profile(identifiers={"something_else": "12345678901234567890"})
    codes = [f.code for f in cloak_forge.validate_profile(profile)]
    assert "UNKNOWN_IDENTIFIER" in codes
