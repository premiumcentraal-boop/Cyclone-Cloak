import cloak_forge
from cloak_forge.prop_parse import draft_device, parse_build_prop

PANTHER_PROPS = """\
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
"""


def test_parses_real_device_shape():
    device = draft_device(parse_build_prop(PANTHER_PROPS))
    assert device["brand"] == "google"
    assert device["model"] == "Pixel 7"
    assert device["sdk_int"] == 33
    assert device["first_api_level"] == 33
    assert device["security_patch"] == "2023-08-05"
    assert device["build_date_utc"] == 1691712000


def test_comments_and_blanks_are_ignored():
    props = parse_build_prop("# header\n\nro.product.brand=google\nbroken line\n")
    assert props == {"ro.product.brand": "google"}


def test_first_api_level_falls_back_to_sdk():
    text = PANTHER_PROPS.replace("ro.product.first_api_level=33\n", "")
    device = draft_device(parse_build_prop(text))
    assert device["first_api_level"] == 33


def test_draft_profile_shape():
    profile = cloak_forge.draft_profile("vault", "ab" * 32, PANTHER_PROPS)
    assert profile["schema_version"] == "0.1"
    assert len(profile["id"]) == 36
    assert profile["meta"]["source"] == "build.prop"
