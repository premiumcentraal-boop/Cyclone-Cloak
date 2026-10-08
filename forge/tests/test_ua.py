"""Forge UA coherence: the agent string must agree with the device block."""

from __future__ import annotations

import cloak_forge
from cloak_forge.forge import forge_profile
from cloak_forge.derive import derive_chrome_version

SEED = "7c1e" * 16


def test_ua_is_deterministic():
    first = forge_profile("Pixel 7 - vault A", SEED, "pixel_7")
    again = forge_profile("Pixel 7 - vault A", SEED, "pixel_7")
    assert first["ua"] == again["ua"]


def test_ua_matches_device_and_template():
    profile = forge_profile("Pixel 7 - vault A", SEED, "pixel_7")
    major, build, patch = derive_chrome_version(SEED)
    assert profile["ua"]["value"] == (
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) "
        f"AppleWebKit/537.36 (KHTML, like Gecko) Chrome/{major}.0.{build}.{patch} "
        "Safari/537.36"
    )
    assert "Pixel 7" in profile["ua"]["value"]
    assert cloak_forge.validate_profile(profile) == []


def test_ua_is_phone_form_on_phone_templates():
    # Both bundled templates are large-class phones; a phone-class profile
    # would carry "Mobile Safari" instead. The token never disappears.
    for template in ("pixel_7", "galaxy_s23"):
        ua = forge_profile("vault", SEED, template)["ua"]["value"]
        assert "Safari/537.36" in ua


def test_ua_model_mismatch_is_caught():
    profile = forge_profile("Pixel 7 - vault A", SEED, "pixel_7")
    profile["device"]["model"] = "Galaxy S23"
    findings = cloak_forge.validate_profile(profile)
    assert "UA_MODEL_MISMATCH" in [f.code for f in findings]
    assert cloak_forge.has_errors(findings)


def test_ua_release_mismatch_is_caught():
    profile = forge_profile("Pixel 7 - vault A", SEED, "pixel_7")
    profile["device"]["version_release"] = "14"
    findings = cloak_forge.validate_profile(profile)
    assert "UA_RELEASE_MISMATCH" in [f.code for f in findings]
    assert cloak_forge.has_errors(findings)


def test_chrome_version_stays_in_a_plausible_range():
    for index in range(4):
        seed = ("0" * 62) + f"{index:02d}"
        major, build, patch = derive_chrome_version(seed)
        assert 100 <= major <= 130
        assert 1000 <= build <= 9999
        assert 1 <= patch <= 99
