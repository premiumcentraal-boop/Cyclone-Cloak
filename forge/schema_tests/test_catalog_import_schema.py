"""Importer interchange is separate from, and compatible with, saved profile JSON."""

import copy
import json
import sys
from pathlib import Path

import jsonschema
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from cloak_forge.device_factory import DeviceFactory, InstanceRequest
from cloak_forge.hardware_xml import FIELD_TABLE, validate_candidate
from test_engine_schema import validator

ROOT = Path(__file__).resolve().parents[2]
CANDIDATE = json.loads(
    (ROOT / "forge/tests/fixtures/catalog-import/synthetic-candidate.json").read_text()
)
RULES = json.loads((ROOT / "schema/catalog-provenance.v1.schema.json").read_text())
VALIDATOR = jsonschema.Draft202012Validator(
    RULES, format_checker=jsonschema.FormatChecker()
)


def test_provenance_schema_and_checked_in_synthetic_manifest():
    VALIDATOR.check_schema(RULES)
    VALIDATOR.validate(CANDIDATE["provenance"])
    assert set(RULES["$defs"]["fact"]["properties"]["path"]["enum"]) == set(
        json.loads(FIELD_TABLE.read_text())["paths"]
    )
    assert validate_candidate(CANDIDATE)["id"] == "synthetic_pixel7_import"


@pytest.mark.parametrize(
    "change",
    [
        lambda m: m.update(extra=True),
        lambda m: m["sources"][0].pop("license"),
        lambda m: m["sources"][0].pop("license_reviewed"),
        lambda m: m["sources"][0].update(sha256="bad"),
        lambda m: m["sources"][0].update(observed_on="2026-02-30"),
        lambda m: m["facts"][0].update(path="device.secret"),
        lambda m: m["facts"][0].update(availability="unknown", value=12),
        lambda m: m["facts"][0].update(availability="known", value=None),
        lambda m: m["facts"][0].update(confidence="verified-platform"),
    ],
)
def test_malformed_manifest_rejected(change):
    manifest = copy.deepcopy(CANDIDATE["provenance"])
    change(manifest)
    assert list(VALIDATOR.iter_errors(manifest))


def test_imported_generation_uses_existing_profile_schema_unchanged():
    factory = DeviceFactory.from_imported_candidates([CANDIDATE])
    for bundle in factory.batch(CANDIDATE["template"]["id"], "schema-import", 4):
        validator("cloak-profile.schema.json").validate(bundle["profile"])
        assert set(bundle["profile"]) == set(
            DeviceFactory().generate(InstanceRequest("pixel_7", "canonical"))["profile"]
        )


@pytest.mark.parametrize("schema", ["0.2", "0.3"])
def test_old_profile_generation_and_import_schema_unchanged(schema):
    from cloak_forge.forge import forge_profile

    profile = forge_profile("Legacy", "a" * 64, schema_version=schema)
    before = copy.deepcopy(profile)
    validator("cloak-profile.schema.json").validate(profile)
    DeviceFactory.from_imported_candidates([CANDIDATE])
    assert profile == before
