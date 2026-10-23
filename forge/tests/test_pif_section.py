"""Per-profile PIF envelope section validation."""
import json
import pathlib
import jsonschema

SCHEMA = pathlib.Path(__file__).resolve().parents[2] / "schema"
PROFILE_SCHEMA = json.loads((SCHEMA / "cloak-profile.schema.json").read_text(encoding="utf-8"))


def _profile_with_pif(pif):
    return {
        "schema_version": "0.2",
        "id": "12345678-1234-4abc-8abc-123456789abc",
        "name": "test",
        "seed": "a" * 64,
        "device": {"manufacturer": "Google", "brand": "google", "model": "Pixel 4", "product": "coral", "device": "flame", "hardware": "qcom", "build_id": "RQ3A.210805.001", "sdk_int": 30, "security_patch": "2024-01-01", "version_incremental": "7658938", "version_release": "11", "bootloader": "c1.0", "fingerprint": "google/coral/flame:11/RQ3A.210805.001/7658938:user/release-keys", "baseband": "g7300-210630-B1-7658938", "first_api_level": 30, "build_date_utc": 1628044800, },
        "pif": pif,
    }


def test_pif_section_validates_when_enabled():
    jsonschema.validate(_profile_with_pif({
        "enabled": True,
        "fingerprint": "google/coral/flame:11/RQ3A.210805.001/7658938:user/release-keys",
        "security_patch": "2024-01-01",
        "model": "Pixel 4",
        
        "verified_boot": True,
    }), PROFILE_SCHEMA)


def test_pif_section_validates_when_absent():
    profile = _profile_with_pif(None)
    del profile["pif"]
    jsonschema.validate(profile, PROFILE_SCHEMA)


def test_pif_section_rejects_unknown_fields():
    try:
        jsonschema.validate(_profile_with_pif({"enabled": True, "magic": "x"}), PROFILE_SCHEMA)
        raise AssertionError("expected additionalProperties violation")
    except jsonschema.ValidationError as error:
        assert "magic" in str(error)
