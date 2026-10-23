"""Host-only regression checks for partial evidence and package isolation."""
import argparse
import json
import sys
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "tools"))
from run_runtime_probe import PHASES, edited_index, parse_phases
from render_runtime_matrix import classify, render


class RunnerTest(unittest.TestCase):
    def test_filesystem_observations_are_unverified_and_not_profile_paths(self):
        root = Path(__file__).resolve().parents[3]
        source = json.loads((root/'catalog/reality-runtime-fields.json').read_text())
        generated = json.loads((root/'android/probe/src/main/assets/reality-runtime-fields.json').read_text())
        self.assertEqual(source, generated)
        self.assertEqual(len(source['fields']), 97)
        observations = source['observation_surfaces']
        self.assertEqual(len(observations), 4)
        self.assertEqual({r['surface'] for r in observations},
                         {'/system/build.prop', '/proc/version', '/proc/cpuinfo', '/proc/self/maps'})
        for row in observations:
            self.assertTrue(row['field_path'].startswith('diagnostics.filesystem.'))
            self.assertEqual(row['status'], 'DIAGNOSTIC_ONLY')
            self.assertIs(row['runtime_covered'], False)
        schema = json.loads((root/'schema/filesystem-observations.schema.json').read_text())
        for variant in schema['properties']['rows']['items']['oneOf']:
            self.assertEqual(variant['properties']['status']['enum'], ['OBSERVED', 'BLOCKED', 'MISSING'])

    def test_diagnostics_permissions_and_sdk_are_isolated(self):
        probe = Path(__file__).resolve().parents[1]
        attr = '{http://schemas.android.com/apk/res/android}name'
        permissions = {p.attrib[attr] for p in ET.parse(probe/'src/diagnostics/AndroidManifest.xml').getroot().findall('uses-permission')}
        for permission in ('ACCESS_FINE_LOCATION', 'ACCESS_COARSE_LOCATION', 'ACCESS_WIFI_STATE', 'BLUETOOTH_CONNECT'):
            self.assertIn('android.permission.' + permission, permissions)
        self.assertIn('com.google.android.gms.permission.AD_ID', permissions)
        main = ET.parse(probe/'src/main/AndroidManifest.xml').getroot()
        self.assertFalse(any(p.attrib[attr].startswith('android.permission.') for p in main.findall('uses-permission')))
        build = (probe/'build.gradle').read_text()
        self.assertIn('diagnosticsImplementation "com.google.android.gms:play-services-ads-identifier:18.3.0"', build)
        self.assertNotIn('implementation "com.google.android.gms:', build)

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
