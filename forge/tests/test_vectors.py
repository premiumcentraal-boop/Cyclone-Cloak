"""The shared vectors (forge/tests/vectors) are the contract with the app's Kotlin port; both suites run them."""

import copy
import json
from datetime import date
from pathlib import Path

import cloak_forge
from cloak_forge import validate as validate_module

VECTORS = Path(__file__).parent / "vectors"


def _apply(profile, mutations):
    for mutation in mutations:
        *parents, leaf = mutation[0].split(".")
        target = profile
        for part in parents:
            target = target[part]
        if len(mutation) == 1:
            target.pop(leaf, None)
        else:
            target[leaf] = mutation[1]
    return profile


def test_validation_vectors(monkeypatch):
    vectors = json.loads((VECTORS / "validation.json").read_text())

    class FixedDate(date):
        @classmethod
        def today(cls):
            return date.fromisoformat(vectors["today"])

    monkeypatch.setattr(validate_module, "date", FixedDate)
    for case in vectors["cases"]:
        profile = _apply(copy.deepcopy(cloak_forge.forge_profile(vectors["name"], vectors["seed"], case["phone"])), case["mutations"])
        got = [[f.severity, f.code] for f in cloak_forge.validate_profile(profile)]
        assert got == case["expect"], case["name"]


def test_dump_vectors():
    for case in json.loads((VECTORS / "dumps.json").read_text())["cases"]:
        assert cloak_forge.draft_phone(cloak_forge.parse_build_prop(case["text"])) == case["expect"], case["name"]
