import cloak_forge
from cloak_forge.forge import available_templates, forge_profile

SEED = "7c1e" * 16


def test_forge_profile_is_deterministic():
    first = forge_profile("Pixel 7 - vault A", SEED, "pixel_7")
    again = forge_profile("Pixel 7 - vault A", SEED, "pixel_7")
    assert first == again


def test_forge_profile_is_coherent():
    profile = forge_profile("Pixel 7 - vault A", SEED, "pixel_7")
    assert profile["schema_version"] == "0.2"
    assert cloak_forge.validate_profile(profile) == []


def test_available_templates_are_valid():
    for template in available_templates():
        profile = forge_profile("vault", SEED, template)
        assert cloak_forge.validate_profile(profile) == []


def test_unknown_template_rejected():
    import pytest
    with pytest.raises(cloak_forge.ForgeError):
        forge_profile("vault", SEED, "not-a-template")


def test_forge_emits_full_identifier_bundle():
    from cloak_forge.derive import derive_identifier_bundle
    profile = forge_profile("Pixel 7 - vault A", SEED, "pixel_7")
    assert profile["identifiers"] == derive_identifier_bundle(SEED)


def test_forge_locale_matches_template():
    profile = forge_profile("Pixel 7 - vault A", SEED, "pixel_7")
    assert profile["locale"] == {"language": "en", "country": "US", "timezone": "America/New_York"}
