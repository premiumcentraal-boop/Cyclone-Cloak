import copy
import json
from dataclasses import asdict
from pathlib import Path

import pytest

from cloak_forge.engine import generate, validate, derive_posture, check_uniqueness, score_coverage
from cloak_forge.forge import forge_profile
from cloak_forge.pif import build_pif
from cloak_forge.templates import load_catalog
from cloak_forge.validate import validate_catalog_record, validate_catalog_metadata

REQUEST = {"name": "Vault 01", "seed": "ab" * 32, "template": "pixel_7"}


@pytest.mark.parametrize("phone", load_catalog(), ids=lambda p: p["id"])
def test_facade_round_trip_preserves_existing_generation(phone):
    bundle = generate({**REQUEST, "template": phone["id"]})
    assert bundle["profile"] == forge_profile(REQUEST["name"], REQUEST["seed"], phone["id"])
    assert json.dumps(bundle["profile"]) == json.dumps(forge_profile(REQUEST["name"], REQUEST["seed"], phone["id"]))
    assert bundle["findings"] == bundle["uniqueness"] == validate(bundle["profile"]) == []
    assert bundle["posture"] == build_pif(bundle["profile"]) == derive_posture(bundle["profile"])
    assert not {"source_confidence", "camera_class", "sensor_class_list", "expected_capability_set", "sources"} & bundle["profile"].keys()


def test_explicit_legacy_version_changes_only_version():
    profile = generate(REQUEST)["profile"]
    legacy = generate({**REQUEST, "schema_version": "0.2"})["profile"]
    profile["schema_version"] = "0.2"
    assert legacy == profile
    assert validate(legacy) == []
    with pytest.raises(ValueError):
        generate({**REQUEST, "schema_version": "9"})


def test_uniqueness_is_case_insensitive_excludes_self_and_does_not_mutate():
    profile = generate(REQUEST)["profile"]
    other = copy.deepcopy(profile)
    other["id"] = "another"
    other["identifiers"] = {"mac": profile["identifiers"]["mac"].lower()}
    before = copy.deepcopy([profile, other])
    findings = check_uniqueness(profile, [profile, other, other, {}])
    assert [f.field for f in findings] == ["identifiers.mac"]
    assert "1 other" in findings[0].message
    assert [profile, other] == before
    assert check_uniqueness(profile, []) == []


def test_source_implementation_does_not_claim_device_verification():
    profile = generate(REQUEST)["profile"]
    report = {"identifiers.android_id": "PASS", "device.model": "VERIFIED"}
    coverage = score_coverage(profile, report)
    rows = {r["field"]: r for r in coverage["rows"]}
    assert rows["identifiers.android_id"]["test_status"] == "PASS"
    assert rows["device.model"]["test_status"] == "VERIFIED"
    assert rows["camera.cameras"]["test_status"] == "not verified"
    assert rows["display.width"]["implementation"] == "PENDING"
    assert sum(coverage["summary"].values()) == len(rows)
    with pytest.raises(ValueError):
        score_coverage(profile, {"device.model": "invented"})


def test_shared_engine_parity_vector():
    vector = json.loads((Path(__file__).parent / "vectors/engine.json").read_text())
    bundle = generate(vector["request"])
    assert bundle == vector["bundle"]
    assert score_coverage(bundle["profile"], vector["device_report"]) == vector["coverage"]
    other = copy.deepcopy(bundle["profile"])
    other["id"] = vector["other_id"]
    assert [asdict(f) for f in check_uniqueness(bundle["profile"], [other])] == vector["uniqueness"]


@pytest.mark.parametrize("mutate,code", [
    (lambda p: p["device"].update(sdk_int=30), "RELEASE_SDK_MISMATCH"),
    (lambda p: p["device"].update(security_patch="2023-02-29"), "PATCH_DATE"),
    (lambda p: p["display"].update(width=120, height=8000), "CATALOG_DISPLAY_ASPECT"),
    (lambda p: p["camera"]["cameras"][0].update(facing=3), "CAMERA_FACING"),
    (lambda p: p["camera"]["cameras"][0].update(facing=True), "CAMERA_FACING"),
    (lambda p: p.update(source_confidence="guaranteed"), "SOURCE_CONFIDENCE"),
    (lambda p: p.update(sensor_class_list="specific invented sensor"), "CORPUS_LIST"),
    (lambda p: p.update(expected_capability_set=["wifi", "wifi"]), "CORPUS_LIST_DUPLICATE"),
    (lambda p: p.update(drm_security_level="L9"), "DRM_SECURITY_LEVEL"),
    (lambda p: p.update(board="\n"), "CORPUS_TEXT"),
])
def test_catalog_rejects_incoherent_claims(mutate, code):
    phone = copy.deepcopy(load_catalog()[1])
    mutate(phone)
    assert any(f.code == code and f.severity == "error" for f in validate_catalog_record(phone))


def test_optional_corpus_absent_is_valid_and_patch_before_build_is_normal():
    assert validate_catalog_metadata({}) == []
    profile = generate(REQUEST)["profile"]
    assert validate(profile) == []
    phone = copy.deepcopy(load_catalog()[1])
    for key in ("confidence", "source_confidence", "camera_class", "sensor_class_list", "expected_capability_set", "sources"):
        phone.pop(key, None)
    assert validate_catalog_record(phone) == []


def test_engine_boundary_has_no_integration_imports():
    source = (Path(__file__).parents[1] / "cloak_forge/engine.py").read_text()
    for forbidden in ("from .storage", "from .connector", "from .publisher", "import android"):
        assert forbidden not in source
    kotlin = (Path(__file__).parents[2] / "android/app/src/main/java/dev/cyclone/cloak/forge/IdentityEngine.kt").read_text()
    for forbidden in ("import android.", "import dev.cyclone.cloak.ui", "import dev.cyclone.cloak.data",
                      "import dev.cyclone.cloak.cyclone"):
        assert forbidden not in kotlin
