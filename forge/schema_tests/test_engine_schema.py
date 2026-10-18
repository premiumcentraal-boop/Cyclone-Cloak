"""Supplemental Draft 2020-12 checks; run with --with jsonschema."""
import json
import sys
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator
from referencing import Registry, Resource

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "forge"))
from cloak_forge.engine import generate, generate_unique
from cloak_forge.templates import load_catalog

SCHEMAS = [json.loads(p.read_text()) for p in (ROOT / "schema").glob("*.schema.json")]
REGISTRY = Registry().with_resources((s["$id"], Resource.from_contents(s)) for s in SCHEMAS)


def validator(name):
    schema = json.loads((ROOT / "schema" / name).read_text())
    Draft202012Validator.check_schema(schema)
    return Draft202012Validator(schema, registry=REGISTRY)


@pytest.mark.parametrize("version", ["0.2", "0.3", "0.4"])
@pytest.mark.parametrize("phone", load_catalog(), ids=lambda p: p["id"])
def test_every_emitted_bundle_validates(phone, version):
    bundle = generate({"name": "Schema test", "seed": "ab" * 32, "template": phone["id"], "schema_version": version})
    validator("engine-bundle.schema.json").validate(bundle)


def test_catalog_schema_and_optional_metadata():
    v = validator("phone-catalog.schema.json")
    v.validate(json.loads((ROOT / "catalog/phones.json").read_text()))
    minimal = load_catalog()[0]
    for key in ("confidence", "source_confidence", "camera_class", "sensor_class_list", "expected_capability_set", "sources"):
        minimal.pop(key, None)
    v.validate({"schemaVersion": 1, "phones": [minimal]})


def test_bundle_rejects_missing_required_and_unknown_fields():
    v = validator("engine-bundle.schema.json")
    assert list(v.iter_errors({"profile": {}}))
    assert list(v.iter_errors({"profile": {}, "metadata": {}, "bindings": {}}))


def test_unique_metadata_is_additive_and_rejects_incomplete_retry_pair():
    v = validator("engine-bundle.schema.json")
    bundle = generate_unique({"name": "Unique", "seed": "ab" * 32, "template": "pixel_7"}, [])
    v.validate(bundle)
    bundle["metadata"].pop("chosen_seed")
    assert list(v.iter_errors(bundle))


@pytest.mark.parametrize("section", ["compute", "memory", "panel", "cameras", "sensors", "power", "drm", "radio", "carrier", "packages", "webview", "environment"])
def test_reality_rejects_private_unknown_fields_and_availability_contradictions(section):
    profile = generate({"name": "Reality", "seed": "ab" * 32, "template": "pixel_7", "schema_version": "0.4"})["profile"]
    v = validator("cloak-profile.schema.json")
    entry = profile["reality"][section]
    entry["values"] = {"private_key": "must never be accepted"}
    assert list(v.iter_errors(profile))
    entry["availability"] = "known"; entry["values"] = {}
    assert list(v.iter_errors(profile))
    entry["availability"] = "unavailable"
    v.validate(profile)
