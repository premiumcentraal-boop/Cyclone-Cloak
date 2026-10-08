"""Per-profile pif.json generation: canonical PIF keys from the device block."""

from __future__ import annotations

from cloak_forge.forge import forge_profile
from cloak_forge.pif import build_pif

SEED = "7c1e" * 16


def test_pif_carries_the_canonical_keys():
    profile = forge_profile("Pixel 7 - vault A", SEED, "pixel_7")
    body = build_pif(profile)
    assert body["MANUFACTURER"] == "Google"
    assert body["MODEL"] == "Pixel 7"
    assert body["BRAND"] == "google"
    assert body["PRODUCT"] == "panther"
    assert body["DEVICE"] == "panther"
    assert body["FINGERPRINT"] == profile["device"]["fingerprint"]
    assert body["SECURITY_PATCH"] == profile["device"]["security_patch"]
    assert body["FIRST_API_LEVEL"] == str(profile["device"]["first_api_level"])
    assert body["BUILD_ID"] == profile["device"]["build_id"]
    assert body["INCREMENTAL"] == profile["device"]["version_incremental"]
    assert body["VERSION_RELEASE"] == str(profile["device"]["version_release"])
    assert body["SDK_INT"] == str(profile["device"]["sdk_int"])


def test_pif_is_all_string_values():
    profile = forge_profile("vault", SEED, "galaxy_s23")
    assert all(isinstance(v, str) for v in build_pif(profile).values())


def test_pif_is_deterministic():
    first = build_pif(forge_profile("vault", SEED, "pixel_7"))
    again = build_pif(forge_profile("vault", SEED, "pixel_7"))
    assert first == again
