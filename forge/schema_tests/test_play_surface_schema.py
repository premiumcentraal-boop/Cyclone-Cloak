import copy
import json
from datetime import datetime
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator, FormatChecker
from referencing import Registry, Resource

ROOT = Path(__file__).resolve().parents[2]
SCHEMAS = [json.loads(p.read_text()) for p in (ROOT / "schema").glob("*.schema.json")]
REGISTRY = Registry().with_resources(
    (s["$id"], Resource.from_contents(s)) for s in SCHEMAS
)
V = json.loads((ROOT / "forge/tests/vectors/play-surface.json").read_text())
FORMATS = FormatChecker()


@FORMATS.checks("date-time", raises=ValueError)
def utc_timestamp(value):
    if not isinstance(value, str):
        return True
    datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ")
    return True


def validator(name):
    schema = json.loads((ROOT / "schema" / name).read_text())
    Draft202012Validator.check_schema(schema)
    return Draft202012Validator(schema, registry=REGISTRY, format_checker=FORMATS)


@pytest.mark.parametrize("case", V["surface_cases"], ids=lambda c: c["name"])
def test_surface_schema_agrees_with_shared_parsers(case):
    assert bool(
        list(validator("play-surface.schema.json").iter_errors(case["value"]))
    ) == bool(case["issues"])


@pytest.mark.parametrize("case", V["readiness_cases"], ids=lambda c: c["name"])
def test_readiness_schema_agrees_with_shared_parsers(case):
    assert bool(
        list(validator("play-readiness.schema.json").iter_errors(case["value"]))
    ) == bool(case["issues"])


@pytest.mark.parametrize("version", ["0.2", "0.3", "0.4"])
def test_profile_section_is_additive(version):
    profile = json.loads((ROOT / "forge/tests/vectors/reality.json").read_text())[
        "profile"
    ]
    profile.pop("reality", None)
    profile["schema_version"] = version
    v = validator("cloak-profile.schema.json")
    v.validate(profile)
    profile["play_surface"] = copy.deepcopy(V["surface"])
    v.validate(profile)
    profile["play_surface"]["account"]["token"] = "secret"
    assert list(v.iter_errors(profile))
