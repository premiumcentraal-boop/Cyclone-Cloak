import copy
import json
import subprocess
import sys
from pathlib import Path

import pytest
from cloak_forge.play_surface import (
    ACCOUNT_MODES,
    READINESS_STATES,
    ContractError,
    account_slot_conflicts,
    bind_readiness,
    encode_readiness,
    encode_surface,
    parse_readiness,
    parse_surface,
    service_conflicts,
    validate_profile_section,
    validate_readiness,
    validate_surface,
)
from cloak_forge.validate import validate_profile

ROOT = Path(__file__).resolve().parents[2]
V = json.loads((ROOT / "forge/tests/vectors/play-surface.json").read_text())


@pytest.mark.parametrize("case", V["surface_cases"], ids=lambda c: c["name"])
def test_shared_surface_vectors(case):
    issues = validate_surface(case["value"])
    assert [{"field": i.field, "code": i.code} for i in issues] == case["issues"]
    assert bool(issues) == (case["name"] not in ("valid", "unknown_mode"))


@pytest.mark.parametrize("case", V["readiness_cases"], ids=lambda c: c["name"])
def test_shared_readiness_vectors(case):
    issues = validate_readiness(case["value"])
    assert [{"field": i.field, "code": i.code} for i in issues] == case["issues"]
    assert bool(issues) == (case["name"] not in READINESS_STATES)


@pytest.mark.parametrize("case", V["service_cases"], ids=lambda c: c["name"])
def test_shared_service_vectors(case):
    issues = service_conflicts(V["surface"], case["observed"])
    assert [{"field": i.field, "code": i.code} for i in issues] == case["issues"]


@pytest.mark.parametrize("mode", ACCOUNT_MODES)
def test_valid_modes_and_roundtrip(mode):
    value = copy.deepcopy(V["surface"])
    value["account"].update(mode=mode)
    if mode == "none":
        value["account"].update(slot_id=None, shared=False)
    assert parse_surface(encode_surface(value)) == value
    assert parse_readiness(encode_readiness(V["readiness"])) == V["readiness"]


@pytest.mark.parametrize(
    "text",
    [
        '{"format":1,"format":1}',
        '{"password":"DO_NOT_ECHO_SECRET"}',
        '{"format":NaN}',
        '{"format":Infinity}',
        "{} trailing",
        "[]",
        '{"nested":' * 40 + "0" + "}" * 40,
        " " * 131073,
        "\ud800",
    ],
    ids=[
        "duplicate-key",
        "private-key",
        "nan",
        "infinity",
        "trailing",
        "array",
        "depth",
        "size",
        "unicode",
    ],
)
def test_strict_bounded_parsing_and_redacted_errors(text):
    for parser in (parse_surface, parse_readiness):
        with pytest.raises(ContractError) as caught:
            parser(text)
        assert "DO_NOT_ECHO_SECRET" not in str(caught.value)
        assert "password" not in str(caught.value)


def assignment(name, active=True, shared=False):
    value = copy.deepcopy(V["surface"])
    value["account"]["shared"] = shared
    return {"profile_id": name, "active": active, "profile": {"play_surface": value}}


def test_slot_conflicts_require_all_active_assignments_to_consent():
    assert not account_slot_conflicts([assignment("one")])
    values = [assignment("one"), assignment("two")]
    before = copy.deepcopy(values)
    assert len(account_slot_conflicts(values)) == 2
    assert values == before
    values[1]["active"] = False
    assert not account_slot_conflicts(values)
    values = [assignment("one", shared=True), assignment("two")]
    assert len(account_slot_conflicts(values)) == 2
    values[1]["profile"]["play_surface"]["account"]["shared"] = True
    assert not account_slot_conflicts(values)
    values[1]["profile"]["play_surface"]["account"]["slot_id"] = "another-slot"
    assert not account_slot_conflicts(values)
    with pytest.raises(ContractError):
        account_slot_conflicts([assignment("one"), assignment("one")])


@pytest.mark.parametrize(
    "field,reason",
    [
        ("profile_id", "PS_PROFILE_MISMATCH"),
        ("profile_hash", "PS_PROFILE_MISMATCH"),
        ("artifact_hash", "PS_ARTIFACT_MISMATCH"),
        ("evidence_digest", "PS_EVIDENCE_MISMATCH"),
    ],
)
def test_evidence_binding_downgrades_without_relabeling(field, reason):
    record = copy.deepcopy(V["readiness"])
    args = {
        k: record[k]
        for k in ("profile_id", "profile_hash", "artifact_hash", "evidence_digest")
    }
    args[field] = "other-profile" if field == "profile_id" else "d" * 64
    result = bind_readiness(record, **args)
    assert result["state"] == "UNKNOWN" and reason in result["reason_codes"]
    assert result[field] == record[field] and record == V["readiness"]


def test_no_silent_promotion_missing_evidence_and_explicit_conflicts():
    record = copy.deepcopy(V["readiness"])
    args = {
        k: record[k]
        for k in ("profile_id", "profile_hash", "artifact_hash", "evidence_digest")
    }
    assert bind_readiness(record, **args) == record
    args["evidence_digest"] = None
    assert "PS_NO_EVIDENCE" in bind_readiness(record, **args)["reason_codes"]
    record.update(state="UNKNOWN", reason_codes=["PS_REVIEW_REQUIRED"])
    args["evidence_digest"] = record["evidence_digest"]
    assert bind_readiness(record, **args)["state"] == "UNKNOWN"
    expected = copy.deepcopy(V["surface"])
    expected["services"]["play_protect"] = "enabled"
    observed = copy.deepcopy(expected["services"])
    observed["play_services"]["version_name"] = "different"
    observed["backup_sync"] = "enabled"
    codes = {i.code for i in service_conflicts(expected, observed)}
    assert codes == {"PS_PACKAGE_CONFLICT", "PS_EXPECTATION_CONFLICT"}


@pytest.mark.parametrize("version", ["0.2", "0.3", "0.4"])
def test_profile_validation_compatibility_and_no_mutation(version):
    profile = json.loads((ROOT / "forge/tests/vectors/reality.json").read_text())[
        "profile"
    ]
    profile.pop("reality", None)
    profile["schema_version"] = version
    before = copy.deepcopy(profile)
    assert not validate_profile_section(profile)
    prior = validate_profile(profile)
    profile["play_surface"] = copy.deepcopy(V["surface"])
    assert validate_profile(profile) == prior
    profile["play_surface"]["account"]["mode"] = "bad"
    assert any(i.code == "PS_VALUE" for i in validate_profile(profile))
    profile.pop("play_surface")
    assert profile == before


def test_generated_constants_are_current():
    subprocess.run(
        [sys.executable, str(ROOT / "tools/generate_play_surface.py"), "--check"],
        check=True,
    )


def test_partial_observations_do_not_hide_known_conflicts_or_invent_missing_facts():
    expected = copy.deepcopy(V["surface"])
    observed = copy.deepcopy(expected["services"])
    observed["play_services"].update(state="unknown", version_name="different")
    assert any(
        i.field.endswith("play_services") and i.code == "PS_PACKAGE_CONFLICT"
        for i in service_conflicts(expected, observed)
    )
    observed["play_services"].pop("version_name")
    assert any(
        i.field.endswith("play_services") and i.code == "PS_EXPECTATION_UNKNOWN"
        for i in service_conflicts(expected, observed)
    )


@pytest.mark.parametrize("target", ["surface", "readiness"])
def test_every_required_root_field_and_confidential_field_is_rejected(target):
    value = copy.deepcopy(V[target])
    validator = validate_surface if target == "surface" else validate_readiness
    for field in list(value):
        incomplete = copy.deepcopy(value)
        incomplete.pop(field)
        assert any(i.code == "PS_REQUIRED" for i in validator(incomplete))
    for field in (
        "password",
        "token",
        "credentials",
        "account_contents",
        "private_key",
    ):
        malformed = copy.deepcopy(value)
        malformed[field] = "DO_NOT_ECHO_SECRET"
        issues = validator(malformed)
        assert any(i.code == "PS_FIELD" for i in issues)
        assert all(
            "DO_NOT_ECHO_SECRET" not in i.message and field not in i.field
            for i in issues
        )
