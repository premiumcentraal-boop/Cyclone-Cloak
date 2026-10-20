"""Synthetic exchange only: never contacts Google, devices, or credential providers."""
import base64
import importlib.util
import json
from datetime import datetime, timezone
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("integrity_backend", ROOT / "tools/integrity_backend.py")
backend = importlib.util.module_from_spec(spec)
spec.loader.exec_module(backend)
NOW = datetime(2026, 10, 20, 12, tzinfo=timezone.utc)


def bundle():
    b = dict(request_id="request-1", profile_id="profile-1", profile_hash="a" * 64,
             artifact_hash="b" * 64, android_user_id=17, received_at="2026-10-20T12:00:00Z",
             token="synthetic-token", token_digest=backend.digest("synthetic-token"))
    return dict(format=1, request_hash=backend.request_hash(b), bundle=b)


def decoded(b):
    return {"tokenPayloadExternal": {
        "requestDetails": {"requestPackageName": backend.PACKAGE, "requestHash": backend.request_hash(b),
                           "timestampMillis": str(int(NOW.timestamp() * 1000))},
        "appIntegrity": {"appRecognitionVerdict": "PLAY_RECOGNIZED", "packageName": backend.PACKAGE},
        "accountDetails": {"appLicensingVerdict": "LICENSED"},
        "deviceIntegrity": {"deviceRecognitionVerdict": ["MEETS_DEVICE_INTEGRITY"]}}}


def test_bundle_valid_and_hash_formula():
    b = backend.parse_bundle(json.dumps(bundle()), NOW)
    assert backend.request_hash(b) == backend.digest("request-1\nprofile-1\n" + "a"*64 + "\n17\n" + "b"*64)


@pytest.mark.parametrize("field", list(bundle()["bundle"]))
def test_incomplete_bundle(field):
    obj = bundle()
    del obj["bundle"][field]
    with pytest.raises(ValueError):
        backend.parse_bundle(json.dumps(obj), NOW)


@pytest.mark.parametrize("mutation", ["token", "hash", "user", "extra", "future", "stale", "bool", "version"])
def test_tampering(mutation):
    obj = bundle()
    if mutation == "token": obj["bundle"]["token"] = "tampered"
    if mutation == "hash": obj["request_hash"] = "f" * 64
    if mutation == "user": obj["bundle"]["android_user_id"] = 0
    if mutation == "extra": obj["bundle"]["verdict"] = "PASS"
    if mutation == "future": obj["bundle"]["received_at"] = "2026-10-21T12:00:00Z"
    if mutation == "stale": obj["bundle"]["received_at"] = "2026-10-18T12:00:00Z"
    if mutation == "bool": obj["format"] = True
    if mutation == "version": obj["format"] = 2
    with pytest.raises(ValueError): backend.parse_bundle(json.dumps(obj), NOW)


@pytest.mark.parametrize("raw", [b"", b"{", b"x"*65537, b'{"format":1,"format":1}', b'NaN', b'[]'], ids=["empty", "truncated", "oversized", "duplicate", "nonfinite", "array"])
def test_malformed(raw):
    with pytest.raises(ValueError): backend.parse_bundle(raw, NOW)


@pytest.mark.parametrize("mutation", ["package", "app_package", "request", "missing", "future", "stale", "unknown", "duplicate"])
def test_decode_rejections(mutation):
    b = bundle()["bundle"]
    result = decoded(b)
    t = result["tokenPayloadExternal"]
    if mutation == "package": t["requestDetails"]["requestPackageName"] = "unrelated.app"
    if mutation == "app_package": t["appIntegrity"]["packageName"] = "unrelated.app"
    if mutation == "request": t["requestDetails"]["requestHash"] = "f"*64
    if mutation == "missing": del t["accountDetails"]
    if mutation == "future": t["requestDetails"]["timestampMillis"] = str(int(NOW.timestamp()*1000)+2000)
    if mutation == "stale": t["requestDetails"]["timestampMillis"] = "1"
    if mutation == "unknown": t["deviceIntegrity"]["deviceRecognitionVerdict"] = ["INVENTED"]
    if mutation == "duplicate": t["deviceIntegrity"]["deviceRecognitionVerdict"] *= 2
    with pytest.raises(ValueError): backend.safe_payload(b, result, "owner", NOW)


def test_safe_extraction_and_signed_schema():
    from cryptography.hazmat.primitives.asymmetric import rsa, padding
    from cryptography.hazmat.primitives import hashes
    import jsonschema
    b = bundle()["bundle"]
    result = decoded(b)
    result["tokenPayloadExternal"]["ignored"] = {"rawToken": "do-not-copy"}
    payload = backend.safe_payload(b, result, "owner", NOW)
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    envelope = backend.sign_envelope(payload, "ephemeral", key)
    schema = json.loads((ROOT / "schema/integrity-verdict.schema.json").read_text())
    jsonschema.validate(envelope, schema)
    jsonschema.validate(payload, {"$defs": schema["$defs"], "$ref": "#/$defs/payload"})
    raw = base64.b64decode(envelope["payload"])
    key.public_key().verify(base64.b64decode(envelope["signature"]), raw, padding.PKCS1v15(), hashes.SHA256())
    assert not any(v in json.dumps(envelope) + raw.decode() for v in ("synthetic-token", "do-not-copy", "token_digest"))
    assert set(payload) == set(schema["$defs"]["payload"]["required"])
    with pytest.raises(ValueError, match="SIGNING_FIELDS"):
        backend.sign_envelope({**payload, "token": "must-not-sign"}, "ephemeral", key)


def test_atomic_output_no_overwrite(tmp_path):
    target = tmp_path / "result.json"
    backend.write_new(target, {"safe": True})
    with pytest.raises(FileExistsError): backend.write_new(target, {"unsafe": True})
    assert json.loads(target.read_text()) == {"safe": True}
    assert list(tmp_path.iterdir()) == [target]


def test_cli_no_credentials_or_network_on_invalid_bundle(tmp_path, monkeypatch, capsys):
    source = tmp_path / "bundle.json"
    source.write_text('{"token":"SECRET-SYNTHETIC"}')
    monkeypatch.setattr(backend, "google_decode", lambda _: pytest.fail("Network called"))
    assert backend.main(["--bundle", str(source), "--output", str(tmp_path/"out.json"),
                         "--provider", "owner", "--key-id", "key", "--signing-key", str(tmp_path/"missing.pem"), "--confirm-send"]) == 1
    assert "SECRET" not in capsys.readouterr().out


def test_android_no_background_token_forwarding():
    activity = (ROOT / "android/playEvidence/src/main/java/dev/cyclone/cloak/playevidence/EvidenceActivity.kt").read_text()
    assert 'begin(true, false)' in activity and 'begin(true, true)' in activity
    assert 'ACTION_CREATE_DOCUMENT' in activity
    manifest = (ROOT / "android/playEvidence/src/main/AndroidManifest.xml").read_text()
    assert "uses-permission" not in manifest
    assert "google_decode(" not in activity


def test_verification_schema():
    import jsonschema
    schema = json.loads((ROOT / "schema/integrity-verification-bundle.schema.json").read_text())
    jsonschema.validate(bundle(), schema, format_checker=jsonschema.FormatChecker())
    invalid = bundle()
    invalid["bundle"]["account_name"] = "forbidden"
    with pytest.raises(jsonschema.ValidationError): jsonschema.validate(invalid, schema)


def test_cli_complete_exchange_once_with_ephemeral_key(tmp_path, monkeypatch, capsys):
    from cryptography.hazmat.primitives.asymmetric import rsa
    from cryptography.hazmat.primitives import serialization
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    private = tmp_path / "ephemeral.pem"
    private.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                         serialization.NoEncryption()))
    now = datetime.now(timezone.utc).replace(microsecond=0)
    obj = bundle()
    obj["bundle"]["received_at"] = now.strftime("%Y-%m-%dT%H:%M:%SZ")
    source, target = tmp_path / "bundle.json", tmp_path / "signed.json"
    source.write_text(json.dumps(obj))
    calls = []
    def fake_decode(token):
        calls.append(token)
        result = decoded(obj["bundle"])
        result["tokenPayloadExternal"]["requestDetails"]["timestampMillis"] = str(int(now.timestamp()*1000))
        return result
    monkeypatch.setattr(backend, "google_decode", fake_decode)
    assert backend.main(["--bundle", str(source), "--output", str(target), "--provider", "owner",
                         "--key-id", "key", "--signing-key", str(private), "--confirm-send"]) == 0
    assert calls == ["synthetic-token"]
    assert "synthetic-token" not in capsys.readouterr().out + target.read_text()
    result = json.loads(base64.b64decode(json.loads(target.read_text())["payload"]))
    assert result["profile_id"] == obj["bundle"]["profile_id"]


def test_cli_decode_failure_has_no_token_or_response_leak(tmp_path, monkeypatch, capsys):
    from cryptography.hazmat.primitives.asymmetric import rsa
    from cryptography.hazmat.primitives import serialization
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    private = tmp_path / "ephemeral.pem"
    private.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                         serialization.NoEncryption()))
    obj = bundle()
    obj["bundle"]["received_at"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    source, target = tmp_path / "bundle.json", tmp_path / "out.json"
    source.write_text(json.dumps(obj))
    def failure(_): raise RuntimeError("synthetic-token PRIVATE-RESPONSE")
    monkeypatch.setattr(backend, "google_decode", failure)
    assert backend.main(["--bundle", str(source), "--output", str(target), "--provider", "owner",
                         "--key-id", "key", "--signing-key", str(private), "--confirm-send"]) == 1
    output = capsys.readouterr()
    assert "synthetic-token" not in output.out + output.err
    assert "PRIVATE-RESPONSE" not in output.out + output.err
    assert not target.exists()
