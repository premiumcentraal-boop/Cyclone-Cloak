"""Synthetic evidence only: these tests neither contact Google nor access a device."""

import importlib.util
import json
import zipfile
from copy import deepcopy
from pathlib import Path
from types import SimpleNamespace

import pytest
from jsonschema import ValidationError

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location(
    "play_evidence_harness", ROOT / "tools/run_play_evidence.py"
)
harness = importlib.util.module_from_spec(spec)
spec.loader.exec_module(harness)


def sample():
    return json.loads(
        (ROOT / "forge/tests/fixtures/play_evidence_report.json").read_text(
            encoding="utf-8"
        )
    )


def validate(report, expected=None):
    return harness.validate_report(json.dumps(report).encode(), expected)


def row(report, name):
    return next(r for r in report["rows"] if r["id"] == name)


def test_shared_fixture_schema_and_semantics():
    report = sample()
    assert validate(report) == report
    assert len(report["rows"]) == 16


@pytest.mark.parametrize(
    "key,value",
    [
        ("profile_hash", "c" * 64),
        ("profile_id", "other"),
        ("artifact_hash", "d" * 64),
        ("android_user_id", 0),
        ("request_id", "other"),
    ],
)
def test_report_binding_mismatch(key, value):
    with pytest.raises(ValueError, match="REPORT_BINDING_MISMATCH"):
        validate(sample(), {key: value})


@pytest.mark.parametrize(
    "name",
    [
        "play_protect.state",
        "account.association",
        "google_settings.access",
        "integrity.verdict",
    ],
)
def test_unmeasured_cannot_be_promoted(name):
    report = sample()
    row(report, name)["status"] = "PASS"
    with pytest.raises((ValidationError, ValueError)):
        validate(report)


def test_permission_block_and_missing_package_remain_explicit():
    report = validate(sample())
    assert row(report, "play_services.availability")["reason"] == "PERMISSION_DENIED"
    assert row(report, "package.com.google.android.gms")["status"] == "MISSING"
    assert report["readiness_report"]["play_services"]["state"] == "unknown"
    assert report["readiness_report"]["google_account"] == "unknown"


@pytest.mark.parametrize(
    "mutation",
    [
        lambda r: r.update(token="PRIVATE_TOKEN"),
        lambda r: r["rows"].append(deepcopy(r["rows"][0])),
        lambda r: r["rows"].__setitem__(1, deepcopy(r["rows"][0])),
        lambda r: r["rows"].pop(),
        lambda r: r.update(timestamp="invalid"),
        lambda r: r.update(android_user_id=True),
        lambda r: row(r, "account.association")["metadata"].update(
            account_name="private"
        ),
        lambda r: row(r, "account.association")["metadata"].update(
            version_name="private"
        ),
        lambda r: r["readiness_report"].update(google_account="associated"),
        lambda r: r["warnings"].clear(),
    ],
)
def test_malformed_reports_rejected(mutation):
    report = sample()
    mutation(report)
    with pytest.raises((ValidationError, ValueError)):
        validate(report)


def test_duplicate_json_keys_and_size_limits():
    with pytest.raises(ValueError, match="DUPLICATE_JSON_KEY"):
        harness.load_json(b'{"format":1,"format":1}')
    with pytest.raises(ValueError, match="REPORT_SIZE_INVALID"):
        harness.load_json(b"x" * (harness.MAX_BYTES + 1))


@pytest.mark.parametrize("key", ["format", "android_user_id"])
def test_float_integer_substitution_is_rejected(key):
    report = sample()
    report[key] = float(report[key])
    with pytest.raises(ValueError, match="INVALID_INTEGER_TYPE"):
        validate(report)


def test_semantic_hash_and_user_matching():
    report = sample()
    row(report, "profile.binding")["metadata"]["actual_profile_hash"] = "c" * 64
    with pytest.raises(ValueError, match="PROFILE_HASH_MISMATCH"):
        validate(report)
    report = sample()
    row(report, "process.android_user")["metadata"]["android_user_id"] = 0
    with pytest.raises(ValueError, match="ANDROID_USER_MISMATCH"):
        validate(report)


def test_schema_rejects_every_unknown_id_status_and_reason():
    for key in ("id", "status", "reason"):
        report = sample()
        report["rows"][0][key] = "UNRECOGNIZED"
        with pytest.raises((ValidationError, ValueError)):
            validate(report)


def test_bundle_layout_and_no_raw_private_payload(tmp_path):
    report = sample()
    counts, digest = harness.bundle(
        tmp_path, report, {"mode": "synthetic"}, {"not_applicable": True}
    )
    assert counts["FAIL"] == 0
    assert len(digest) == 64
    with zipfile.ZipFile(tmp_path / "evidence-bundle.zip") as archive:
        assert set(archive.namelist()) == {
            "report.json",
            "readiness-report.json",
            "provenance.json",
            "cleanup.json",
            "summary.md",
        }
        assert all(
            word not in archive.read("report.json")
            for word in (b'"token"', b'"account_name"', b'"profile_body"')
        )


def test_offline_runner_creates_bundle_and_refuses_overwrite(tmp_path):
    output = tmp_path / "evidence"
    args = [
        "--report",
        str(ROOT / "forge/tests/fixtures/play_evidence_report.json"),
        "--output",
        str(output),
    ]
    assert harness.main(args) == 0
    assert (output / "evidence-bundle.zip").exists()
    with pytest.raises(FileExistsError):
        harness.main(args)


def test_rejected_private_input_is_not_preserved(tmp_path):
    source = tmp_path / "bad.json"
    source.write_text('{"token":"DO_NOT_PRESERVE_THIS"}', encoding="utf-8")
    output = tmp_path / "failed"
    assert harness.main(["--report", str(source), "--output", str(output)]) == 1
    assert not (output / "report.json").exists()
    assert "DO_NOT_PRESERVE_THIS" not in (output / "failure.json").read_text()


def test_device_path_only_uses_own_storage_and_waits_for_user(tmp_path, monkeypatch):
    report = sample()
    profile = tmp_path / "profile.json"
    profile.write_text('{"schema_version":"0.4"}', encoding="utf-8")
    report["profile_hash"] = harness.hashlib.sha256(profile.read_bytes()).hexdigest()
    row(report, "profile.binding")["metadata"]["actual_profile_hash"] = report[
        "profile_hash"
    ]
    report["readiness_report"]["profile_hash"] = report["profile_hash"]
    report["request_id"] = "fixed-request"
    calls = []

    class FakeAdb:
        def __init__(self, *args):
            pass

        def call(self, *args, **kwargs):
            calls.append(args)
            return SimpleNamespace(
                returncode=0, stdout=b"package:/data/app/safe/base.apk\n"
            )

        def own(self, command, **kwargs):
            calls.append((command,))
            raw = (
                json.dumps(report).encode()
                if command.startswith("cat files/reports/")
                else b"e" * 64 + b"  /data/app/safe/base.apk\n"
            )
            return SimpleNamespace(returncode=0, stdout=raw)

    monkeypatch.setattr(harness, "Adb", FakeAdb)
    monkeypatch.setattr(harness.uuid, "uuid4", lambda: "fixed-request")
    args = SimpleNamespace(
        profile=profile,
        profile_id=report["profile_id"],
        artifact_digest=report["artifact_hash"],
        serial="fake",
        user=17,
        adb="adb",
        wait_integrity=False,
        timeout=1,
    )
    captured, provenance, cleanup = harness.capture(args, tmp_path)
    assert captured == report
    assert all(cleanup.values())
    assert provenance["integrity_requested_by_runner"] is False
    text = str(calls)
    assert "files/inputs/fixed-request.json" in text
    assert "am', 'start" in text
    assert "--activity-clear-top" in text
    assert "--activity-single-top" in text  # Deliver the new request to a reused screen.
    for forbidden in (
        "pm grant",
        "pm revoke",
        "install-existing",
        "su ",
        "switch-user",
        "force-stop",
    ):
        assert forbidden not in text


def test_separate_manifest_has_no_permissions_or_services():
    import xml.etree.ElementTree as ET

    root = ET.parse(
        ROOT / "android/playEvidence/src/main/AndroidManifest.xml"
    ).getroot()
    assert root.findall("uses-permission") == []
    assert root.findall(".//service") == []
    text = (
        ROOT
        / "android/playEvidence/src/main/java/dev/cyclone/cloak/playevidence/EvidenceActivity.kt"
    ).read_text()
    assert "requestPermissions" not in text
    assert "No collection on launch" in text

def test_root_transport_preserves_bytes_and_honors_remote_failure(monkeypatch):
    adb = harness.Adb('adb', 'serial', 17)
    def call(*args, **kwargs):
        marker = __import__('re').search(r'CLOAK_EVIDENCE_EXIT_[a-f0-9]+', args[-1])[0]
        return SimpleNamespace(returncode=0, stdout=b'{"exact":"bytes"}\n\n'+marker.encode()+b'=0\n')
    monkeypatch.setattr(adb, 'call', call)
    assert adb.root('cat safe').stdout == b'{"exact":"bytes"}\n'
    def failed(*args, **kwargs):
        marker = __import__('re').search(r'CLOAK_EVIDENCE_EXIT_[a-f0-9]+', args[-1])[0]
        return SimpleNamespace(returncode=0, stdout=b'not a report\n'+marker.encode()+b'=1\n')
    monkeypatch.setattr(adb, 'call', failed)
    assert adb.root('cat missing', check=False).returncode == 1
    with pytest.raises(RuntimeError, match='DIAGNOSTICS_PRIVATE_ACCESS_FAILED'):
        adb.root('cat missing')


def test_root_transport_rejects_missing_exit_marker(monkeypatch):
    adb = harness.Adb('adb', 'serial', 17)
    monkeypatch.setattr(adb, 'call', lambda *a, **k: SimpleNamespace(returncode=0, stdout=b'fake success'))
    with pytest.raises(RuntimeError, match='DIAGNOSTICS_PRIVATE_ACCESS_FAILED'):
        adb.root('id')


def test_signed_apk_staging_has_app_ownership_and_selinux_label(monkeypatch):
    adb = harness.Adb('adb', 'serial', 17)
    commands = []
    def root(command, **kwargs):
        commands.append(command)
        return SimpleNamespace(returncode=0, stdout=b'1710312\n' if command.startswith('stat') else b'')
    monkeypatch.setattr(adb, 'root', root)
    adb.enable_root_private_access()
    adb.own('cat > files/inputs/request-1.json', data=b'private')
    assert 'chown 1710312:1710312' in commands[1]
    assert 'restorecon -F' in commands[1]  # Repair stale per-user MLS categories too.
    assert 'restorecon -RF' in commands[1]  # Android needs recursive app-data relabeling.
    assert 'chown 1710312:1710312' in commands[2]
    assert 'restorecon -F' in commands[2]
    assert all('private' not in command for command in commands)
    for forbidden in ('rm -rf /', 'cat /data/user/17/dev.cyclone.cloak/files/profiles/private.json',
                      'cat files/reports/request.json; id', 'cat files/reports/../../other.json'):
        with pytest.raises(ValueError, match='DIAGNOSTICS_COMMAND_NOT_ALLOWED'):
            adb.own(forbidden)


def test_signed_apk_uid_mismatch_fails_before_any_write(monkeypatch):
    adb = harness.Adb('adb', 'serial', 17)
    commands = []
    def root(command, **kwargs):
        commands.append(command)
        return SimpleNamespace(returncode=0, stdout=b'10312\n')
    monkeypatch.setattr(adb, 'root', root)
    with pytest.raises(ValueError, match='DIAGNOSTICS_UID_MISMATCH'):
        adb.enable_root_private_access()
    assert len(commands) == 1


def test_saved_identity_is_read_only_and_rejects_paths(monkeypatch):
    adb = harness.Adb('adb', 'serial', 17)
    with pytest.raises(ValueError, match='INVALID_SAVED_IDENTITY'):
        adb.saved_identity('id')
    adb.root_private = True
    commands = []
    def root(command, **kwargs):
        commands.append(command)
        return SimpleNamespace(returncode=0, stdout=b'{"schema_version":"0.4"}')
    monkeypatch.setattr(adb, 'root', root)
    assert adb.saved_identity('safe-id') == b'{"schema_version":"0.4"}'
    assert commands == ['cat /data/user/17/dev.cyclone.cloak/files/profiles/safe-id.json']
    for name in ('../other', 'id;id', 'id/file'):
        with pytest.raises(ValueError, match='INVALID_SAVED_IDENTITY'):
            adb.saved_identity(name)

def test_root_stdin_uses_eof_capable_non_pty_transport(monkeypatch):
    adb = harness.Adb('adb', 'serial', 17)
    calls = []
    def call(*args, **kwargs):
        calls.append((args, kwargs))
        marker = __import__('re').search(r'CLOAK_EVIDENCE_EXIT_[a-f0-9]+', args[-1])[0]
        return SimpleNamespace(returncode=0, stdout=b'\r\n'+marker.encode()+b'=0\r\n')
    monkeypatch.setattr(adb, 'call', call)
    adb.root('cat > diagnostics-private', data=b'{"private":"stdin only"}\n')
    args, kwargs = calls[0]
    assert args[:2] == ('shell', '-T')
    assert kwargs['data'] == b'{"private":"stdin only"}\n'
    assert 'stdin only' not in str(args)


def test_signed_run_as_has_actionable_error_without_automatic_root(monkeypatch):
    adb = harness.Adb('adb', 'serial', 17)
    calls = []
    def call(*args, **kwargs):
        calls.append(args)
        return SimpleNamespace(returncode=1, stdout=b'', stderr=b'run-as: package not debuggable')
    monkeypatch.setattr(adb, 'call', call)
    with pytest.raises(ValueError, match='SIGNED_APK_REQUIRES_EXPLICIT_PRIVATE_ACCESS'):
        adb.own('mkdir -p files/inputs')
    assert len(calls) == 1
    assert 'su' not in calls[0]
