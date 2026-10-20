"""Explicit owner-operated Google decode exchange. Never log tokens or response bodies.

Run with uv --with google-auth --with requests --with cryptography. Importing this
module performs no I/O or networking. No offline/unsigned verdict input is accepted.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile
from datetime import datetime, timezone

PACKAGE = "dev.cyclone.cloak.playevidence"
MAX_BYTES = 65536
ID = r"[A-Za-z0-9][A-Za-z0-9._:-]{0,95}"
HASH = r"[0-9a-f]{64}"
DEVICE = {"MEETS_BASIC_INTEGRITY", "MEETS_DEVICE_INTEGRITY", "MEETS_STRONG_INTEGRITY", "MEETS_VIRTUAL_INTEGRITY"}


def check(condition, code):
    if not condition:
        raise ValueError(code)


def digest(value):
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def pairs(items):
    result = {}
    for key, value in items:
        check(key not in result, "DUPLICATE_FIELD")
        result[key] = value
    return result


def strict_json(raw, limit=MAX_BYTES):
    check(0 < len(raw) <= limit, "INPUT_SIZE")
    try:
        return json.loads(raw, object_pairs_hook=pairs,
                          parse_constant=lambda _: (_ for _ in ()).throw(ValueError("INVALID_JSON")))
    except (UnicodeError, json.JSONDecodeError, RecursionError):
        raise ValueError("INVALID_JSON") from None


def timestamp(value):
    check(isinstance(value, str) and re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z", value), "INVALID_TIMESTAMP")
    try:
        return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    except ValueError:
        raise ValueError("INVALID_TIMESTAMP") from None


def request_hash(bundle):
    return digest("\n".join(str(bundle[k]) for k in (
        "request_id", "profile_id", "profile_hash", "android_user_id", "artifact_hash")))


def parse_bundle(raw, now):
    root = strict_json(raw)
    check(type(root) is dict and set(root) == {"format", "request_hash", "bundle"}, "BUNDLE_FIELDS")
    check(type(root["format"]) is int and root["format"] == 1, "BUNDLE_FORMAT")
    b = root["bundle"]
    check(type(b) is dict and set(b) == {"request_id", "profile_id", "profile_hash", "artifact_hash",
          "android_user_id", "received_at", "token_digest", "token"}, "BUNDLE_FIELDS")
    for key, pattern in (("request_id", r"[A-Za-z0-9_-]{1,80}"), ("profile_id", ID),
                         ("profile_hash", HASH), ("artifact_hash", HASH), ("token_digest", HASH),
                         ("token", r"[A-Za-z0-9._~+/=-]{1,60000}")):
        check(type(b[key]) is str and re.fullmatch(pattern, b[key]), "BUNDLE_VALUE")
    check(type(b["android_user_id"]) is int and 0 <= b["android_user_id"] <= 21474, "BUNDLE_USER")
    check(digest(b["token"]) == b["token_digest"], "BUNDLE_TOKEN_DIGEST")
    check(root["request_hash"] == request_hash(b), "BUNDLE_REQUEST_HASH")
    age = (now - timestamp(b["received_at"])).total_seconds()
    check(0 <= age <= 86400, "BUNDLE_FRESHNESS")
    return b


def safe_payload(b, decoded, provider, now):
    """Only a successful authenticated Google decode response may reach this boundary."""
    check(type(provider) is str and re.fullmatch(ID, provider), "PROVIDER_ID")
    try:
        token = decoded["tokenPayloadExternal"]
        request = token["requestDetails"]
        check(request["requestPackageName"] == PACKAGE, "DECODE_PACKAGE_MISMATCH")
        check(request["requestHash"] == request_hash(b), "DECODE_REQUEST_MISMATCH")
        millis = request["timestampMillis"]
        check(type(millis) is str and re.fullmatch(r"[0-9]{1,16}", millis), "DECODE_TIMESTAMP")
        issued = datetime.fromtimestamp(int(millis) / 1000, timezone.utc)
        receipt = timestamp(b["received_at"])
        # Android importer timestamps use seconds. Permit only the receipt's own second.
        check(0 <= (now - issued).total_seconds() <= 86400 and
              issued.replace(microsecond=0) <= receipt <= now, "DECODE_TIMESTAMP")
        app = token["appIntegrity"]
        recognition = app["appRecognitionVerdict"]
        check(recognition in {"PLAY_RECOGNIZED", "UNRECOGNIZED_VERSION", "UNEVALUATED"}, "DECODE_APP_VERDICT")
        if "packageName" in app:
            check(app["packageName"] == PACKAGE, "DECODE_PACKAGE_MISMATCH")
        licensing = token["accountDetails"]["appLicensingVerdict"]
        check(licensing in {"LICENSED", "UNLICENSED", "UNEVALUATED"}, "DECODE_LICENSE_VERDICT")
        devices = token["deviceIntegrity"]["deviceRecognitionVerdict"]
        check(type(devices) is list and len(devices) <= 4 and all(type(v) is str and v in DEVICE for v in devices)
              and len(set(devices)) == len(devices), "DECODE_DEVICE_VERDICT")
    except (KeyError, TypeError, OverflowError, OSError):
        raise ValueError("DECODE_INCOMPLETE") from None
    return {"format": 1, "provider": provider,
            **{k: b[k] for k in ("request_id", "profile_id", "profile_hash", "artifact_hash", "android_user_id")},
            "request_hash": request_hash(b), "timestamp": issued.strftime("%Y-%m-%dT%H:%M:%SZ"),
            "decoded_at": now.strftime("%Y-%m-%dT%H:%M:%SZ"), "app_recognition": recognition,
            "app_licensing": licensing, "device_recognition": devices}


def google_decode(token):
    # ADC and credentials live solely on the owner's backend, never in the APK.
    import google.auth
    from google.auth.transport.requests import AuthorizedSession
    credentials, _ = google.auth.default(scopes=["https://www.googleapis.com/auth/playintegrity"])
    with AuthorizedSession(credentials, max_refresh_attempts=0) as session:
        # Do not replay a decode request automatically. Google may clear replayed verdicts.
        with session.post(f"https://playintegrity.googleapis.com/v1/{PACKAGE}:decodeIntegrityToken",
                          json={"integrity_token": token}, timeout=60, allow_redirects=False, stream=True) as response:
            check(response.status_code == 200, "GOOGLE_DECODE_FAILED")
            chunks = bytearray()
            for part in response.iter_content(4096):
                chunks.extend(part)
                check(len(chunks) <= MAX_BYTES, "GOOGLE_RESPONSE_SIZE")
            return strict_json(bytes(chunks))


def validate_payload(payload):
    """Closed safe payload contract shared by signing and offline verification."""
    check(type(payload) is dict and set(payload) == {"format", "provider", "request_id", "request_hash",
          "profile_id", "profile_hash", "artifact_hash", "android_user_id", "timestamp", "decoded_at",
          "app_recognition", "app_licensing", "device_recognition"}, "SIGNING_FIELDS")
    check(type(payload["format"]) is int and payload["format"] == 1, "SIGNING_FIELDS")
    for name, pattern in (("provider", ID), ("request_id", ID), ("profile_id", ID),
                          ("request_hash", HASH), ("profile_hash", HASH), ("artifact_hash", HASH)):
        check(type(payload[name]) is str and re.fullmatch(pattern, payload[name]), "SIGNING_FIELDS")
    check(type(payload["android_user_id"]) is int and 0 <= payload["android_user_id"] <= 21474,
          "SIGNING_FIELDS")
    check(payload["request_hash"] == request_hash(payload), "SIGNING_FIELDS")
    check(timestamp(payload["timestamp"]) <= timestamp(payload["decoded_at"]), "SIGNING_FIELDS")
    check(type(payload["app_recognition"]) is str and payload["app_recognition"] in
          {"PLAY_RECOGNIZED", "UNRECOGNIZED_VERSION", "UNEVALUATED"}, "SIGNING_FIELDS")
    check(type(payload["app_licensing"]) is str and payload["app_licensing"] in
          {"LICENSED", "UNLICENSED", "UNEVALUATED"}, "SIGNING_FIELDS")
    devices = payload["device_recognition"]
    check(type(devices) is list and len(devices) <= 4 and
          all(type(v) is str and v in DEVICE for v in devices) and len(set(devices)) == len(devices), "SIGNING_FIELDS")


def sign_envelope(payload, key_id, private_key):
    from cryptography.hazmat.primitives import hashes
    from cryptography.hazmat.primitives.asymmetric import padding, rsa
    validate_payload(payload)
    check(isinstance(private_key, rsa.RSAPrivateKey) and 2048 <= private_key.key_size <= 8192, "SIGNING_KEY")
    check(type(key_id) is str and re.fullmatch(ID, key_id), "KEY_ID")
    raw = json.dumps(payload, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode()
    signature = private_key.sign(raw, padding.PKCS1v15(), hashes.SHA256())
    return {"format": 1, "algorithm": "RS256", "provider": payload["provider"], "key_id": key_id,
            "payload": base64.b64encode(raw).decode(), "signature": base64.b64encode(signature).decode()}


def write_new(path, value):
    """Atomic publication, fail if an output already exists; never overwrite old evidence."""
    path = Path(path)
    descriptor, temp = tempfile.mkstemp(dir=path.parent, prefix=".integrity-")
    try:
        with os.fdopen(descriptor, "wb") as out:
            out.write(json.dumps(value, sort_keys=True, separators=(",", ":")).encode())
            out.flush()
            os.fsync(out.fileno())
        os.link(temp, path)
    finally:
        os.unlink(temp)


def validate_identity(provider, key_id):
    check(type(provider) is str and re.fullmatch(ID, provider) and
          type(key_id) is str and re.fullmatch(ID, key_id), "INVALID_PROVIDER")


def validate_signing_key(key):
    from cryptography.hazmat.primitives.asymmetric import rsa
    check(isinstance(key, rsa.RSAPrivateKey) and 2048 <= key.key_size <= 8192, "SIGNING_KEY")
    return key


def load_signing_key(path):
    from cryptography.hazmat.primitives.serialization import load_pem_private_key
    check(path is not None, "SIGNING_KEY")
    with Path(path).open("rb") as stream:
        pem = stream.read(32769)
    check(len(pem) <= 32768, "SIGNING_KEY")
    password = os.environ.get("CYCLONE_SIGNING_KEY_PASSWORD")
    return validate_signing_key(load_pem_private_key(pem, password=password.encode() if password else None))


def public_pin(key, provider, key_id):
    from cryptography.hazmat.primitives import serialization
    validate_identity(provider, key_id)
    validate_signing_key(key)
    raw = key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    return {"provider": provider, "key_id": key_id, "public_key_sha256": hashlib.sha256(raw).hexdigest(),
            "public_key": base64.b64encode(raw).decode("ascii")}


def canonical_base64(value):
    check(type(value) is str, "INVALID_BASE64")
    raw = base64.b64decode(value, validate=True)
    check(base64.b64encode(raw).decode("ascii") == value, "INVALID_BASE64")
    return raw


def check_pin_file(path, expected):
    from cryptography.hazmat.primitives.serialization import load_der_public_key
    from cryptography.hazmat.primitives.asymmetric import rsa
    with Path(path).open("rb") as stream:
        root = strict_json(stream.read(16385), 16384)
    check(type(root) is dict and set(root) == {"format", "keys"} and
          type(root["format"]) is int and root["format"] == 1, "PIN_FORMAT")
    entries = root["keys"]
    check(type(entries) is list and 1 <= len(entries) <= 8, "PIN_FORMAT")
    seen, matched = set(), False
    for entry in entries:
        check(type(entry) is dict and set(entry) == {"provider", "key_id", "public_key"}, "PIN_FIELDS")
        validate_identity(entry["provider"], entry["key_id"])
        identity = entry["provider"], entry["key_id"]
        check(identity not in seen, "PIN_DUPLICATE")
        seen.add(identity)
        raw = canonical_base64(entry["public_key"])
        key = load_der_public_key(raw)
        check(isinstance(key, rsa.RSAPublicKey) and 2048 <= key.key_size <= 8192, "PIN_KEY")
        if identity == (expected["provider"], expected["key_id"]):
            check(entry["public_key"] == expected["public_key"], "PIN_MISMATCH")
            matched = True
    check(matched, "PIN_MISSING")


def check_dependencies():
    import cryptography
    import google.auth
    from google.auth.transport.requests import AuthorizedSession
    from cryptography.hazmat.primitives.asymmetric import rsa
    # Imports only. Never resolve ADC, inspect credential files, or refresh credentials.
    check(all((cryptography, google.auth, AuthorizedSession, rsa)), "DEPENDENCIES")


def check_output_directory(directory):
    check(Path(directory).is_dir(), "OUTPUT_DIRECTORY")
    # Empty private temporary file, removed on close; no bundle/key data written.
    with tempfile.TemporaryFile(dir=directory):
        pass


def doctor(args):
    """Checks return fixed names/states only. No paths, exceptions, or secrets."""
    results = []

    def run(name, action):
        try:
            value = action()
            results.append({"check": name, "status": "PASS"})
            return value
        except Exception:
            results.append({"check": name, "status": "FAIL"})
            return None

    run("dependencies", check_dependencies)
    run("provider_key_id", lambda: validate_identity(args.provider, args.key_id))
    key = run("signing_key", lambda: load_signing_key(args.signing_key))
    pin = run("public_key", lambda: public_pin(key, args.provider, args.key_id))
    if args.pin_file:
        run("public_pin", lambda: check_pin_file(args.pin_file, pin))
    else:
        results.append({"check": "public_pin", "status": "NOT_REQUESTED"})
    if args.bundle:
        def read_bundle():
            with args.bundle.open("rb") as stream:
                parse_bundle(stream.read(MAX_BYTES + 1), datetime.now(timezone.utc))
        run("verification_bundle", read_bundle)
    else:
        results.append({"check": "verification_bundle", "status": "NOT_REQUESTED"})
    def output():
        if args.output:
            check(not args.output.exists(), "OUTPUT_EXISTS")
        check_output_directory(args.output_dir or (args.output.parent if args.output else Path.cwd()))
    run("output_directory", output)
    return {"result": "INTEGRITY_DOCTOR_FAILED" if any(r["status"] == "FAIL" for r in results) else "INTEGRITY_DOCTOR_PASS",
            "checks": results, "network": "NOT_RUN"}


def parse_signed_envelope(raw, public_key, provider, key_id, expected, now):
    """Pinned RS256 + closed contract + request binding, shared with contract tests.

    This verifies a backend assertion, never an offline Google signature. A key
    must be supplied explicitly; no default provider is trusted.
    """
    from cryptography.hazmat.primitives import hashes
    from cryptography.hazmat.primitives.asymmetric import padding, rsa
    validate_identity(provider, key_id)
    outer = strict_json(raw, 32768)
    check(type(outer) is dict and set(outer) == {"format", "algorithm", "provider", "key_id", "payload", "signature"}, "ENVELOPE_FIELDS")
    check(type(outer["format"]) is int and outer["format"] == 1 and outer["algorithm"] == "RS256", "ENVELOPE_FORMAT")
    check(outer["provider"] == provider and outer["key_id"] == key_id, "ENVELOPE_PROVIDER")
    check(isinstance(public_key, rsa.RSAPublicKey) and 2048 <= public_key.key_size <= 8192, "ENVELOPE_KEY")
    check(type(outer["payload"]) is str and 4 <= len(outer["payload"]) <= 24000 and
          type(outer["signature"]) is str and 344 <= len(outer["signature"]) <= 2048, "ENVELOPE_SIZE")
    payload_bytes = canonical_base64(outer["payload"])
    public_key.verify(canonical_base64(outer["signature"]), payload_bytes, padding.PKCS1v15(), hashes.SHA256())
    # Strict UTF-8 matches Kotlin's fail-closed decoder (JSON auto-detects UTF-16 otherwise).
    payload = strict_json(payload_bytes.decode("utf-8"))
    validate_payload(payload)
    check(payload["provider"] == provider, "ENVELOPE_PROVIDER")
    for field in ("request_id", "profile_id", "profile_hash", "artifact_hash", "android_user_id"):
        check(payload[field] == expected[field], "ENVELOPE_BINDING")
    check(payload["request_hash"] == request_hash(expected), "ENVELOPE_BINDING")
    for field in ("timestamp", "decoded_at"):
        check(0 <= (now - timestamp(payload[field])).total_seconds() <= 86400, "ENVELOPE_FRESHNESS")
    receipt = timestamp(expected["received_at"])
    check(0 <= (now - receipt).total_seconds() <= 86400 and
          timestamp(payload["timestamp"]) <= receipt <= timestamp(payload["decoded_at"]), "ENVELOPE_RECEIPT")
    return payload


def synthetic_roundtrip(now=None):
    """Internal test artifact, ephemeral key in RAM only. Never real evidence."""
    from cryptography.hazmat.primitives.asymmetric import rsa
    now = (now or datetime.now(timezone.utc)).replace(microsecond=0)
    fixture = Path(__file__).resolve().parents[1] / "forge/tests/fixtures/integrity_decode_synthetic.json"
    with fixture.open("rb") as stream:
        decoded = strict_json(stream.read(MAX_BYTES + 1))
    check(decoded.get("synthetic_only") is True, "SYNTHETIC_FIXTURE_REQUIRED")
    binding = decoded.pop("synthetic_binding")
    binding.update(received_at=now.strftime("%Y-%m-%dT%H:%M:%SZ"), token="synthetic-only-not-a-google-token")
    binding["token_digest"] = digest(binding["token"])
    bundle = {"format": 1, "request_hash": request_hash(binding), "bundle": binding}
    parsed = parse_bundle(json.dumps(bundle), now)
    # Rebase only the synthetic fixture clock, not its package/hash/verdict values.
    decoded["tokenPayloadExternal"]["requestDetails"]["timestampMillis"] = str(int(now.timestamp()) * 1000)
    payload = safe_payload(parsed, decoded, "synthetic.self-test", now)
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    envelope = sign_envelope(payload, "ephemeral", key)
    verified = parse_signed_envelope(json.dumps(envelope), key.public_key(), "synthetic.self-test", "ephemeral", parsed, now)
    check(verified == payload and "token" not in verified and "token_digest" not in verified, "SYNTHETIC_ALLOWLIST")
    # Negative checks use the same production parsers, never the live decode path.
    invalid_bundle = dict(bundle, request_hash="0" * 64)
    try:
        parse_bundle(json.dumps(invalid_bundle), now)
    except ValueError:
        pass
    else:
        raise ValueError("SYNTHETIC_HASH_REJECTION")
    invalid_envelope = dict(envelope, signature=base64.b64encode(bytes(256)).decode())
    try:
        parse_signed_envelope(json.dumps(invalid_envelope), key.public_key(), "synthetic.self-test", "ephemeral", parsed, now)
    except Exception:
        pass
    else:
        raise ValueError("SYNTHETIC_SIGNATURE_REJECTION")
    pin = public_pin(key, "synthetic.self-test", "ephemeral")
    # Only public synthetic artifacts leave this internal helper; never the private key/token.
    return {"envelope": envelope, "public_pin": pin,
            "payload_sha256": hashlib.sha256(canonical_base64(envelope["payload"])).hexdigest(), "checks": 8}


def self_test():
    artifact = synthetic_roundtrip()
    return {"result": "SYNTHETIC_ONLY_SELF_TEST_PASS", "checks": artifact["checks"],
            "payload_sha256": artifact["payload_sha256"],
            "public_key_sha256": artifact["public_pin"]["public_key_sha256"]}


class SafeArgumentParser(argparse.ArgumentParser):
    def error(self, message):
        # argparse's default error can echo arbitrary command-line values.
        raise ValueError("CLI_ARGUMENTS")


def main(argv=None):
    parser = SafeArgumentParser(description="Owner-controlled Integrity exchange and offline operational checks.")
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--doctor", action="store_true", help="Local preflight only; never checks ADC or calls Google")
    modes.add_argument("--self-test", action="store_true", help="Offline synthetic-only roundtrip, ephemeral key")
    modes.add_argument("--show-pin", action="store_true", help="Print derived public key metadata only")
    parser.add_argument("--bundle", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--output-dir", type=Path, help="Doctor write-test directory (default: current directory)")
    parser.add_argument("--pin-file", type=Path, help="Optional existing format-1 public trust configuration")
    parser.add_argument("--provider")
    parser.add_argument("--key-id")
    parser.add_argument("--signing-key", type=Path)
    parser.add_argument("--confirm-send", action="store_true")
    try:
        args = parser.parse_args(argv)
    except ValueError:
        print("INTEGRITY_ARGUMENTS_FAILED: invalid arguments")
        return 2
    mode = "SELF_TEST" if args.self_test else "SHOW_PIN" if args.show_pin else "DOCTOR" if args.doctor else "EXCHANGE"
    try:
        if args.self_test:
            check(not any((args.bundle, args.output, args.output_dir, args.pin_file, args.provider,
                           args.key_id, args.signing_key, args.confirm_send)), "MODE_ARGUMENTS")
            print(json.dumps(self_test(), sort_keys=True))
            return 0
        if args.doctor:
            check(not args.confirm_send, "MODE_ARGUMENTS")
            result = doctor(args)
            print(json.dumps(result, sort_keys=True))
            return 0 if result["result"] == "INTEGRITY_DOCTOR_PASS" else 1
        validate_identity(args.provider, args.key_id)
        if args.show_pin:
            check(not any((args.bundle, args.output, args.output_dir, args.pin_file, args.confirm_send)), "MODE_ARGUMENTS")
            print(json.dumps(public_pin(load_signing_key(args.signing_key), args.provider, args.key_id), sort_keys=True))
            return 0
        check(args.confirm_send and args.bundle and args.output and args.signing_key
              and not args.output_dir and not args.pin_file, "EXCHANGE_ARGUMENTS")
        check(not args.output.exists() and args.output.parent.is_dir(), "OUTPUT_EXISTS_OR_UNAVAILABLE")
        with args.bundle.open("rb") as stream:
            b = parse_bundle(stream.read(MAX_BYTES + 1), datetime.now(timezone.utc))
        key = load_signing_key(args.signing_key)
        decoded = google_decode(b["token"])
        payload = safe_payload(b, decoded, args.provider, datetime.now(timezone.utc))
        write_new(args.output, sign_envelope(payload, args.key_id, key))
        print("SIGNED_BACKEND_ASSERTION_WRITTEN")
        return 0
    except Exception:
        # Never print exception text: third-party errors may contain secrets or HTTP bodies.
        failures = {"EXCHANGE": "INTEGRITY_EXCHANGE_FAILED: no authenticated verdict written",
                    "DOCTOR": "INTEGRITY_DOCTOR_FAILED: local checks failed",
                    "SHOW_PIN": "INTEGRITY_SHOW_PIN_FAILED: public pin unavailable",
                    "SELF_TEST": "SYNTHETIC_ONLY_SELF_TEST_FAILED"}
        print(failures[mode])
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
