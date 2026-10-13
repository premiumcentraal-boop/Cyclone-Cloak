"""Safety checks for the device-test harness, not simulated hook success."""
import importlib.util
from pathlib import Path

import pytest

spec = importlib.util.spec_from_file_location("runtime_probe", Path(__file__).resolve().parents[2] / "tools/run_runtime_probe.py")
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


def test_probe_binding_preserves_other_users_and_packages_without_mutation():
    index = {"schemaVersion": 2, "entries": {"13/other.app": {"key": "production"}}, "extra": True}
    updated = probe.edited_index(index, 11, "test", "fixture", {"test"})
    assert updated["entries"]["13/other.app"] == index["entries"]["13/other.app"]
    assert updated["extra"] is True
    assert len(index["entries"]) == 1
    assert probe.edited_index(updated, 11, None, None, {"test"}) == index


def test_probe_refuses_to_overwrite_existing_binding():
    index = {"schemaVersion": 2, "entries": {"11/" + probe.PACKAGE: {"key": "production"}}}
    with pytest.raises(ValueError, match="refusing"):
        probe.edited_index(index, 11, "test", "fixture", {"test"})


def test_probe_does_not_publish_observed_identifiers():
    report = {"rows": [{"id": "test", "actual": "HOST-IDENTIFIER", "status": "FAIL"}]}
    sanitized = probe.redact(report)
    assert "actual" not in sanitized["rows"][0]
    assert len(sanitized["rows"][0]["actual_sha256"]) == 64
    assert report["rows"][0]["actual"] == "HOST-IDENTIFIER"


def test_runtime_fixtures_are_distinct_and_pass_existing_validation():
    import json
    from cloak_forge.validate import validate_profile
    fixtures = [json.loads((probe.REPO / f"android/probe/src/main/assets/profile-{f}.json").read_text()) for f in ("a", "b")]
    for fixture in fixtures:
        assert validate_profile(fixture) == []
        assert fixture["schema_version"] == "0.3"
    assert fixtures[0]["device"]["model"] != fixtures[1]["device"]["model"]
    assert fixtures[0]["identifiers"] != fixtures[1]["identifiers"]


def test_matrix_requires_transitions_and_restored_control_for_proof():
    matrix_spec = importlib.util.spec_from_file_location("matrix", probe.REPO / "tools/render_runtime_matrix.py")
    matrix = importlib.util.module_from_spec(matrix_spec)
    matrix_spec.loader.exec_module(matrix)
    reads = {
        "bound-a": {"status": "PASS", "expected": "a", "actual_sha256": "a"},
        "bound-a-repeat": {"status": "PASS", "actual_sha256": "a"},
        "bound-b": {"status": "PASS", "expected": "b", "actual_sha256": "b"},
        "unbound-before": {"actual_sha256": "host"},
        "unbound-after": {"actual_sha256": "host"},
    }
    assert matrix.classify(reads) == "VERIFIED"
    reads["bound-b"]["expected"] = "a"
    assert matrix.classify(reads) == "MATCH_ONLY"
    reads["unbound-after"]["actual_sha256"] = "different"
    assert matrix.classify(reads) == "CONTROL_CHANGED"
