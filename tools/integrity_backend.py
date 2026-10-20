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


def sign_envelope(payload, key_id, private_key):
    from cryptography.hazmat.primitives import hashes
    from cryptography.hazmat.primitives.asymmetric import padding, rsa
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


def main(argv=None):
    parser = argparse.ArgumentParser(description="Explicitly send one retained token to Google and sign safe verdict fields.")
    parser.add_argument("--bundle", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--provider", required=True)
    parser.add_argument("--key-id", required=True)
    parser.add_argument("--signing-key", required=True, type=Path)
    parser.add_argument("--confirm-send", required=True, action="store_true")
    args = parser.parse_args(argv)
    try:
        from cryptography.hazmat.primitives.serialization import load_pem_private_key
        check(re.fullmatch(ID, args.provider) and re.fullmatch(ID, args.key_id), "INVALID_PROVIDER")
        check(not args.output.exists() and args.output.parent.is_dir(), "OUTPUT_EXISTS_OR_UNAVAILABLE")
        with args.bundle.open("rb") as stream:
            b = parse_bundle(stream.read(MAX_BYTES + 1), datetime.now(timezone.utc))
        with args.signing_key.open("rb") as stream:
            pem = stream.read(32769)
        check(len(pem) <= 32768, "SIGNING_KEY")
        password = os.environ.get("CYCLONE_SIGNING_KEY_PASSWORD")
        key = load_pem_private_key(pem, password=password.encode() if password else None)
        from cryptography.hazmat.primitives.asymmetric import rsa
        check(isinstance(key, rsa.RSAPrivateKey) and 2048 <= key.key_size <= 8192, "SIGNING_KEY")
        decoded = google_decode(b["token"])
        payload = safe_payload(b, decoded, args.provider, datetime.now(timezone.utc))
        write_new(args.output, sign_envelope(payload, args.key_id, key))
        print("SIGNED_BACKEND_ASSERTION_WRITTEN")
        return 0
    except Exception:
        # Third-party errors may contain HTTP bodies, tokens, paths or key details.
        print("INTEGRITY_EXCHANGE_FAILED: no authenticated verdict written")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
