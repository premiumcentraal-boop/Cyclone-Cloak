"""Every successful Health claim must be supported by the checked-in device proof."""
import json
from pathlib import Path

import pytest

from tools.render_health_coverage import render
from tools.render_runtime_matrix import classify

ROOT = Path(__file__).resolve().parents[2]
DATA = json.loads((ROOT / "android/app/src/main/assets/compatibility-coverage.json").read_text(encoding="utf-8"))
EVIDENCE = json.loads((ROOT / DATA["device_evidence"]).read_text(encoding="utf-8"))
READS = {}
for run in EVIDENCE["runs"]:
    for row in run["rows"]:
        READS.setdefault(row["id"], {})[run["run"]] = row


def test_all_requested_areas_are_present_once():
    expected = {"build", "properties", "telephony", "android_id", "serial", "advertising_id",
                "display", "locale_timezone", "wifi", "bluetooth", "camera", "sensors", "drm"}
    assert len(DATA["rows"]) == len(expected)
    assert {row["id"] for row in DATA["rows"]} == expected
    assert DATA["baseline_sha256"] == EVIDENCE["artifact"] == EVIDENCE["metadata"]["module_arm64_sha256"]


@pytest.mark.parametrize("row", DATA["rows"], ids=lambda row: row["id"])
def test_each_claim_has_evidence(row):
    assert row["area"] and row["note"] and row["device_reads"]
    assert row["implementation_status"] in {"partial", "not_implemented"}
    assert len(row["device_reads"]) == len(set(row["device_reads"]))
    assert set(row["device_reads"]) <= READS.keys()
    proofs = {classify(READS[read]) for read in row["device_reads"]}
    actual_verified = {read for read in row["device_reads"] if classify(READS[read]) == "VERIFIED"}
    assert set(row["verified_reads"]) == actual_verified
    if actual_verified:
        assert row["test_status"] == "partial_device_proof"
        assert row["last_verified_utc"] == EVIDENCE["metadata"]["measured_at_utc"][:10]
    else:
        assert row["last_verified_utc"] is None
        assert row["test_status"] != "partial_device_proof"
    if row["test_status"] == "device_failed":
        assert "FAIL" in proofs
    elif row["test_status"] == "device_failed_and_blocked":
        assert {"FAIL", "BLOCKED"} <= proofs
    elif row["test_status"] == "unmapped":
        assert proofs == {"UNMAPPED"}
    elif row["test_status"] == "unit_passed_device_failed":
        assert "FAIL" in proofs
        assert row["unit_evidence"]
    else:
        assert row["test_status"] == "partial_device_proof"
    for reference in row["unit_evidence"]:
        assert (ROOT / reference).is_file()


def test_selected_reads_do_not_double_count_or_hide_unproven_results():
    claimed = [read for row in DATA["rows"] for read in row["verified_reads"]]
    assert len(claimed) == len(set(claimed)) == 13
    assert "build.getSerial" not in claimed
    assert "display.resources.width" not in claimed
    assert "camera.characteristics" not in claimed


def test_generated_document_is_current():
    assert (ROOT / "docs/HEALTH-COVERAGE.md").read_text(encoding="utf-8") == render(DATA)
