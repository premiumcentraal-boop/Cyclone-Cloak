"""Host-only regression checks for partial evidence and package isolation."""
import argparse
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "tools"))
from run_runtime_probe import PHASES, edited_index, parse_phases
from render_runtime_matrix import classify, render


class RunnerTest(unittest.TestCase):
    def test_default_and_subset_order(self):
        self.assertEqual(parse_phases("all"), list(PHASES))
        self.assertEqual(parse_phases("bound-b,bound-a"), ["bound-a", "bound-b"])

    def test_invalid_subsets(self):
        for value in ("", "unknown", "bound-a,bound-a", "all,bound-a"):
            with self.assertRaises(argparse.ArgumentTypeError):
                parse_phases(value)

    def test_diagnostics_binding_leaves_default_binding_alone(self):
        index = {"schemaVersion": 2, "entries": {"0/dev.cyclone.cloak.probe": {"key": "existing"}}}
        updated = edited_index(index, 0, "owned", "fixture", {"owned"}, "dev.cyclone.cloak.probe.diag")
        self.assertEqual(updated["entries"]["0/dev.cyclone.cloak.probe"], {"key": "existing"})
        cleaned = edited_index(updated, 0, None, None, {"owned"}, "dev.cyclone.cloak.probe.diag")
        self.assertEqual(cleaned, index)

    def test_existing_diagnostics_binding_is_refused(self):
        index = {"schemaVersion": 2, "entries": {"0/dev.cyclone.cloak.probe.diag": {"key": "foreign"}}}
        with self.assertRaises(ValueError):
            edited_index(index, 0, None, None, {"owned"}, "dev.cyclone.cloak.probe.diag")

    def test_partial_pass_never_becomes_verified(self):
        row = {"id": "wifi.serviceMac", "status": "PASS", "reason": "MATCH", "expected": "synthetic", "actual_sha256": "hash"}
        self.assertEqual(classify({"bound-a": row}), "INCOMPLETE")
        result = render({"metadata": {"phases": ["bound-a"]}, "runs": [{"run": "bound-a", "rows": [row]}]})
        self.assertIn("PASS | not run | INCOMPLETE", result)

    def test_full_proof_unchanged(self):
        reads = {phase: {"status": "PASS", "expected": "a", "actual_sha256": "a"} for phase in PHASES}
        reads["bound-b"] = {"status": "PASS", "expected": "b", "actual_sha256": "b"}
        self.assertEqual(classify(reads), "VERIFIED")


if __name__ == "__main__":
    unittest.main()
