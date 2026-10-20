"""Operator kit: synthetic data, ephemeral keys, and no Google/network requests."""
import base64
import importlib.util
import json
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import Mock

import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("integrity_operations_backend", ROOT / "tools/integrity_backend.py")
backend = importlib.util.module_from_spec(spec)
spec.loader.exec_module(backend)


@pytest.fixture
def setup(tmp_path, monkeypatch):
    monkeypatch.delenv("CYCLONE_SIGNING_KEY_PASSWORD", raising=False)
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    path = tmp_path / "ephemeral-private.pem"
    path.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                       serialization.NoEncryption()))
    # Dependency-import availability is tested separately. Never resolve owner ADC.
    monkeypatch.setattr(backend, "check_dependencies", lambda: None)
    monkeypatch.setattr(backend, "google_decode", lambda _: pytest.fail("Unexpected Google request"))
    args = ["--provider", "synthetic.owner", "--key-id", "ephemeral", "--signing-key", str(path)]
    return key, path, args


def pin_file(tmp_path, key):
    pin = backend.public_pin(key, "synthetic.owner", "ephemeral")
    path = tmp_path / "public-pin.json"
    path.write_text(json.dumps({"format": 1, "keys": [{k: pin[k] for k in ("provider", "key_id", "public_key")}]}))
    return path


def doctor_result(capsys):
    output = capsys.readouterr()
    assert output.err == ""
    return json.loads(output.out)


def test_doctor_pass_local_only(setup, tmp_path, capsys):
    _, path, args = setup
    before = set(tmp_path.iterdir())
    assert backend.main(["--doctor", *args, "--output-dir", str(tmp_path)]) == 0
    result = doctor_result(capsys)
    assert result["result"] == "INTEGRITY_DOCTOR_PASS" and result["network"] == "NOT_RUN"
    assert len(result["checks"]) == 7
    assert sum(r["status"] == "PASS" for r in result["checks"]) == 5
    assert str(path) not in json.dumps(result)
    assert set(tmp_path.iterdir()) == before


def test_doctor_checks_bundle_and_pin(setup, tmp_path, capsys):
    key, _, args = setup
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    body = dict(request_id="synthetic", profile_id="synthetic", profile_hash="a"*64,
                artifact_hash="b"*64, android_user_id=17, received_at=now,
                token="synthetic-secret-token", token_digest=backend.digest("synthetic-secret-token"))
    source = tmp_path / "bundle.json"
    source.write_text(json.dumps(dict(format=1, request_hash=backend.request_hash(body), bundle=body)))
    assert backend.main(["--doctor", *args, "--pin-file", str(pin_file(tmp_path, key)),
                         "--bundle", str(source), "--output-dir", str(tmp_path)]) == 0
    result = doctor_result(capsys)
    assert all(r["status"] == "PASS" for r in result["checks"])
    assert "synthetic-secret-token" not in json.dumps(result)


@pytest.mark.parametrize("mutation", ["missing", "malformed", "oversized"])
def test_doctor_bad_key_fixed_safe_failure(setup, tmp_path, mutation, capsys):
    _, path, args = setup
    if mutation == "missing": path.unlink()
    if mutation == "malformed": path.write_text("PRIVATE-SECRET-KEY-CONTENT")
    if mutation == "oversized": path.write_bytes(b"PRIVATE-SECRET"*3000)
    assert backend.main(["--doctor", *args, "--output-dir", str(tmp_path)]) == 1
    result = doctor_result(capsys)
    assert result["result"] == "INTEGRITY_DOCTOR_FAILED"
    assert {"check": "signing_key", "status": "FAIL"} in result["checks"]
    assert "PRIVATE-SECRET" not in json.dumps(result)


@pytest.mark.parametrize("size", [1024, 2047, 8193, 9216])
def test_rsa_size_boundaries_fail_closed(size):
    # No slow oversized key generation: exercise the same RSA type/size gate.
    key = Mock(spec=rsa.RSAPrivateKey)
    key.key_size = size
    with pytest.raises(ValueError, match="SIGNING_KEY"):
        backend.validate_signing_key(key)


def test_real_weak_rsa_key_is_rejected(setup, tmp_path, capsys):
    _, path, args = setup
    weak = rsa.generate_private_key(public_exponent=65537, key_size=1024)
    path.write_bytes(weak.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                      serialization.NoEncryption()))
    assert backend.main(["--doctor", *args]) == 1
    assert {"check": "signing_key", "status": "FAIL"} in doctor_result(capsys)["checks"]


@pytest.mark.parametrize("password", ["correct", "wrong", "missing"])
def test_encrypted_pem_password(setup, tmp_path, monkeypatch, capsys, password):
    key, path, args = setup
    path.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                       serialization.BestAvailableEncryption(b"synthetic-password")))
    if password != "missing":
        monkeypatch.setenv("CYCLONE_SIGNING_KEY_PASSWORD", "synthetic-password" if password == "correct" else "wrong-password")
    assert backend.main(["--doctor", *args, "--output-dir", str(tmp_path)]) == (0 if password == "correct" else 1)
    result = doctor_result(capsys)
    assert "synthetic-password" not in json.dumps(result) and "wrong-password" not in json.dumps(result)


@pytest.mark.parametrize("mutation", ["mismatch", "missing_identity", "malformed", "duplicate", "secret_field"])
def test_pin_rejections(setup, tmp_path, capsys, mutation):
    key, _, args = setup
    path = pin_file(tmp_path, key)
    obj = json.loads(path.read_text())
    if mutation == "mismatch":
        other = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        obj["keys"][0]["public_key"] = backend.public_pin(other, "synthetic.owner", "ephemeral")["public_key"]
    if mutation == "missing_identity": obj["keys"][0]["key_id"] = "other"
    if mutation == "duplicate": obj["keys"] *= 2
    if mutation == "secret_field": obj["keys"][0]["private_key"] = "PRIVATE-SECRET"
    path.write_text("{malformed" if mutation == "malformed" else json.dumps(obj))
    assert backend.main(["--doctor", *args, "--pin-file", str(path)]) == 1
    result = doctor_result(capsys)
    assert {"check": "public_pin", "status": "FAIL"} in result["checks"]
    assert "PRIVATE-SECRET" not in json.dumps(result)


@pytest.mark.parametrize("field", ["provider", "key-id"])
def test_invalid_identity(setup, field, capsys):
    _, _, args = setup
    args[args.index("--"+field)+1] = "not valid PRIVATE-SECRET"
    assert backend.main(["--doctor", *args]) == 1
    result = doctor_result(capsys)
    assert {"check": "provider_key_id", "status": "FAIL"} in result["checks"]
    assert "PRIVATE-SECRET" not in json.dumps(result)


def test_missing_dependency_is_fixed_check_failure(setup, monkeypatch, capsys):
    _, _, args = setup
    def missing(): raise ImportError("PRIVATE-TOKEN CREDENTIALS-PATH")
    monkeypatch.setattr(backend, "check_dependencies", missing)
    assert backend.main(["--doctor", *args]) == 1
    result = doctor_result(capsys)
    assert {"check": "dependencies", "status": "FAIL"} in result["checks"]
    assert "PRIVATE-TOKEN" not in json.dumps(result)


def test_unwritable_output_and_bad_bundle(setup, tmp_path, monkeypatch, capsys):
    _, _, args = setup
    source = tmp_path / "bad.json"
    source.write_text('{"token":"PRIVATE-TOKEN"}')
    def denied(_): raise PermissionError("PRIVATE-PATH")
    monkeypatch.setattr(backend, "check_output_directory", denied)
    assert backend.main(["--doctor", *args, "--bundle", str(source)]) == 1
    result = doctor_result(capsys)
    assert {"check": "verification_bundle", "status": "FAIL"} in result["checks"]
    assert {"check": "output_directory", "status": "FAIL"} in result["checks"]
    assert "PRIVATE" not in json.dumps(result)


def test_existing_output_is_preserved(setup, tmp_path, capsys):
    _, _, args = setup
    output = tmp_path / "existing.json"
    output.write_text("preserve")
    assert backend.main(["--doctor", *args, "--output", str(output)]) == 1
    assert {"check": "output_directory", "status": "FAIL"} in doctor_result(capsys)["checks"]
    assert output.read_text() == "preserve"


def test_show_pin_public_fields_only(setup, capsys):
    key, path, args = setup
    assert backend.main(["--show-pin", *args]) == 0
    result = json.loads(capsys.readouterr().out)
    assert set(result) == {"provider", "key_id", "public_key_sha256", "public_key"}
    assert result == backend.public_pin(key, "synthetic.owner", "ephemeral")
    loaded = serialization.load_der_public_key(base64.b64decode(result["public_key"]))
    assert loaded.public_numbers() == key.public_key().public_numbers()
    assert path.read_text() not in json.dumps(result) and "PRIVATE" not in json.dumps(result)


def test_show_pin_failure_is_fixed(setup, capsys):
    _, path, args = setup
    path.write_text("PRIVATE-SECRET")
    assert backend.main(["--show-pin", *args]) == 1
    assert capsys.readouterr().out == "INTEGRITY_SHOW_PIN_FAILED: public pin unavailable\n"


def test_self_test_offline_no_files_or_owner_keys(tmp_path, monkeypatch, capsys):
    monkeypatch.chdir(tmp_path)
    monkeypatch.setenv("CYCLONE_SIGNING_KEY_PASSWORD", "PRIVATE-PASSWORD")
    monkeypatch.setattr(backend, "google_decode", lambda _: pytest.fail("Network"))
    monkeypatch.setattr(backend, "load_signing_key", lambda _: pytest.fail("Owner key accessed"))
    assert backend.main(["--self-test"]) == 0
    output = capsys.readouterr()
    result = json.loads(output.out)
    assert result["result"] == "SYNTHETIC_ONLY_SELF_TEST_PASS" and result["checks"] == 8
    assert len(result["public_key_sha256"]) == len(result["payload_sha256"]) == 64
    assert not any(v in output.out + output.err for v in ["PRIVATE-PASSWORD", "synthetic-only-not-a-google-token", "tokenPayloadExternal", "PRIVATE KEY"])
    assert not list(tmp_path.iterdir())


def test_self_test_uses_pinned_parser_and_schema():
    import jsonschema
    artifact = backend.synthetic_roundtrip()
    public = serialization.load_der_public_key(base64.b64decode(artifact["public_pin"]["public_key"]))
    raw = json.dumps(artifact["envelope"])
    payload = json.loads(base64.b64decode(artifact["envelope"]["payload"]))
    expected = {**payload, "received_at": payload["timestamp"]}
    verified = backend.parse_signed_envelope(raw, public, "synthetic.self-test", "ephemeral", expected, datetime.now(timezone.utc))
    schema = json.loads((ROOT / "schema/integrity-verdict.schema.json").read_text())
    jsonschema.validate(artifact["envelope"], schema)
    jsonschema.validate(verified, {"$defs": schema["$defs"], "$ref": "#/$defs/payload"})
    assert verified["app_recognition"] == "UNEVALUATED"


@pytest.mark.parametrize("mode,expected", [
    ("--doctor", "INTEGRITY_DOCTOR_FAILED: local checks failed"),
    ("--self-test", "SYNTHETIC_ONLY_SELF_TEST_FAILED"),
    ("--show-pin", "INTEGRITY_SHOW_PIN_FAILED: public pin unavailable")])
def test_no_mode_can_accidentally_send(mode, expected, capsys, monkeypatch):
    monkeypatch.setattr(backend, "google_decode", lambda _: pytest.fail("Network"))
    assert backend.main([mode, "--confirm-send"]) == 1
    assert capsys.readouterr().out == expected + "\n"


def test_self_test_exception_is_fixed(monkeypatch, capsys):
    def failed(): raise RuntimeError("PRIVATE-TOKEN HTTP-BODY")
    monkeypatch.setattr(backend, "self_test", failed)
    assert backend.main(["--self-test"]) == 1
    assert capsys.readouterr().out == "SYNTHETIC_ONLY_SELF_TEST_FAILED\n"


@pytest.mark.parametrize("args", [["--doctor", "--self-test"], ["--doctor", "--token", "PRIVATE-TOKEN"], ["--doctor", "--signing-key"]])
def test_argument_failures_never_echo_values(args, capsys):
    assert backend.main(args) == 2
    output = capsys.readouterr()
    assert output.out == "INTEGRITY_ARGUMENTS_FAILED: invalid arguments\n" and output.err == ""


@pytest.mark.parametrize("field,value", [("android_user_id", 0), ("profile_id", "other"),
                                          ("profile_hash", "f"*64), ("artifact_hash", "f"*64), ("request_id", "other")])
def test_contract_parser_rejects_foreign_context(field, value):
    artifact = backend.synthetic_roundtrip()
    public = serialization.load_der_public_key(base64.b64decode(artifact["public_pin"]["public_key"]))
    payload = json.loads(base64.b64decode(artifact["envelope"]["payload"]))
    expected = {**payload, "received_at": payload["timestamp"], field: value}
    with pytest.raises(ValueError, match="ENVELOPE_BINDING"):
        backend.parse_signed_envelope(json.dumps(artifact["envelope"]), public, "synthetic.self-test", "ephemeral",
                                      expected, datetime.now(timezone.utc))
