"""Read-only helper tests use synthetic local logs, never a device."""
import json
import shutil
import subprocess
import zipfile
from pathlib import Path

import pytest

SHELL = shutil.which("powershell") or shutil.which("pwsh")
pytestmark = pytest.mark.skipif(SHELL is None, reason="PowerShell not installed")
SCRIPT = Path(__file__).resolve().parents[1] / "diagnose.ps1"


def run_helper(tmp_path, lines, *filters):
    log = tmp_path / "input.log"
    log.write_text(lines, encoding="utf-8")
    result = subprocess.run([SHELL, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(SCRIPT), "-InputLog", str(log),
                             "-OutputDirectory", str(tmp_path), *filters], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    with zipfile.ZipFile(result.stdout.strip()) as archive:
        assert archive.namelist() == ["diagnostics.json"]
        return json.loads(archive.read("diagnostics.json"))


def app_event():
    return "CloakDiagnostics: " + json.dumps(dict(timestamp=1, severity="ERROR", transition="FAILED",
        subsystem="profiles", action_id="import", thread="3", process="42", target_id="token=private",
        message="private account message", error_summary="IllegalArgumentException", reason="INVALID_PROFILE",
        duration_ms=4, token="secret-token", clipboard="secret-clipboard"))


def test_helper_redacts_raw_payloads_and_module_paths(tmp_path):
    report = run_helper(tmp_path, app_event() + "\nCloakModule: profile unreadable /data/private-content\n")
    encoded = json.dumps(report)
    assert report["redacted"] is True
    assert len(report["events"]) == 2
    for private in ("private-content", "secret-token", "secret-clipboard", "private account", "token=private"):
        assert private not in encoded


def test_helper_filters_and_ignores_malformed_input(tmp_path):
    report = run_helper(tmp_path, app_event() + '\nCloakDiagnostics: {bad}\nCloakModule: installed\n',
                        "-Subsystem", "profiles", "-Severity", "ERROR", "-ActionId", "import", "-Text", "INVALID_PROFILE")
    assert len(report["events"]) == 1
    assert report["ignored_lines"] == 1


def test_probe_status_is_observed_without_values(tmp_path):
    report = run_helper(tmp_path, 'CloakProbeDiagnostics: {"field":"wifi.ssid","status":"BLOCKED","actual":"private-ssid"}\n')
    assert report["events"][0]["severity"] == "WARNING"
    assert "private-ssid" not in json.dumps(report)
