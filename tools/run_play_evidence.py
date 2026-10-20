"""User-initiated, diagnostics-only Play evidence export. Never installs or grants anything."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shlex
import subprocess
import time
import uuid
import zipfile
from collections import Counter
from datetime import UTC, datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "dev.cyclone.cloak.playevidence"
MAX_BYTES = 131072


def _pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("DUPLICATE_JSON_KEY")
        result[key] = value
    return result


def load_json(raw: bytes, limit=MAX_BYTES):
    if not 0 < len(raw) <= limit:
        raise ValueError("REPORT_SIZE_INVALID")
    return json.loads(
        raw.decode("utf-8"),
        object_pairs_hook=_pairs,
        parse_constant=lambda _: (_ for _ in ()).throw(ValueError("NONFINITE_JSON")),
    )


def validate_report(raw: bytes, expected=None):
    """Closed schema plus semantic checks; never retain malformed raw payloads."""
    import jsonschema

    report = load_json(raw)
    schema = json.loads(
        (ROOT / "schema/play-evidence-report.schema.json").read_text(encoding="utf-8")
    )
    jsonschema.Draft202012Validator(
        schema, format_checker=jsonschema.FormatChecker()
    ).validate(report)
    if type(report["format"]) is not int or type(report["android_user_id"]) is not int:
        raise ValueError("INVALID_INTEGER_TYPE")
    for key, pattern in (
        ("request_id", r"[A-Za-z0-9_-]{1,80}"),
        ("profile_id", r"[A-Za-z0-9][A-Za-z0-9._:-]{0,95}"),
        ("profile_hash", r"[0-9a-f]{64}"),
        ("artifact_hash", r"[0-9a-f]{64}"),
        ("timestamp", r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z"),
    ):
        if not re.fullmatch(pattern, report[key]):
            raise ValueError("INVALID_BINDING")
    if expected and any(report.get(key) != value for key, value in expected.items()):
        raise ValueError("REPORT_BINDING_MISMATCH")
    rows = report["rows"]
    by_id = {r["id"]: r for r in rows}
    for row in rows:
        reason, meta = row["reason"], row["metadata"]
        if type(row["duration_ms"]) is not int or any(
            type(meta[k]) is not int
            for k in ("api_code", "version_code", "android_user_id")
            if k in meta
        ):
            raise ValueError("INVALID_INTEGER_TYPE")
        if any(
            not re.fullmatch(r"[0-9a-f]{64}", meta[k])
            for k in ("actual_profile_hash", "response_digest", "request_hash")
            if k in meta
        ):
            raise ValueError("INVALID_DIGEST")
        if (
            reason == "PROFILE_HASH_MATCHED"
            and meta.get("actual_profile_hash") != report["profile_hash"]
        ):
            raise ValueError("PROFILE_HASH_MISMATCH")
        if reason == "PROFILE_HASH_MISMATCH" and meta.get("actual_profile_hash") in (
            None,
            report["profile_hash"],
        ):
            raise ValueError("INVALID_PROFILE_FAILURE")
        if (
            reason == "USER_MATCHED"
            and meta.get("android_user_id") != report["android_user_id"]
        ):
            raise ValueError("ANDROID_USER_MISMATCH")
        if reason in ("SERVICE_AVAILABLE", "SERVICE_UNAVAILABLE") and (
            "api_code" not in meta
            or (meta["api_code"] == 0) != (reason == "SERVICE_AVAILABLE")
        ):
            raise ValueError("INVALID_SERVICE_CLASSIFICATION")
        if reason in ("VISIBLE_ACCOUNT", "NO_CALLER_VISIBLE_ACCOUNT") and meta.get(
            "visible_account"
        ) is not (reason == "VISIBLE_ACCOUNT"):
            raise ValueError("INVALID_ACCOUNT_CLASSIFICATION")
        if reason in ("PACKAGE_OBSERVED", "PROVIDER_OBSERVED", "PACKAGE_DISABLED"):
            if (
                not {"package_name", "version_name", "version_code", "enabled"}
                <= meta.keys()
            ):
                raise ValueError("INCOMPLETE_PACKAGE_METADATA")
            if meta["enabled"] is not (reason != "PACKAGE_DISABLED"):
                raise ValueError("INVALID_PACKAGE_CLASSIFICATION")
            name = (
                row["id"].removeprefix("package.")
                if row["id"].startswith("package.")
                else (
                    "com.android.vending"
                    if row["id"] == "play_store.availability"
                    else None
                )
            )
            if (
                not isinstance(meta["package_name"], str)
                or name
                and meta["package_name"] != name
            ):
                raise ValueError("PACKAGE_MISMATCH")
        if reason == "TOKEN_RECEIVED":
            if (
                not {"response_digest", "received_at", "request_hash", "provider_id"}
                <= meta.keys()
            ):
                raise ValueError("INCOMPLETE_API_RECEIPT")
            if meta["received_at"] > report["timestamp"]:
                raise ValueError("FUTURE_API_RECEIPT")
        # Metadata keys are scoped to the reader, not merely globally allowlisted.
        allowed = set()
        if row["id"].startswith("package.") or row["id"] in (
            "play_store.availability",
            "web_provider.state",
        ):
            allowed = {
                "package_name",
                "version_name",
                "version_code",
                "enabled",
                "installer",
                "installer_reason",
            }
        elif row["id"] == "play_services.availability":
            allowed = {"api_code"}
        elif row["id"] == "account.association":
            allowed = {"visible_account"}
        elif row["id"] == "process.android_user":
            allowed = {"android_user_id"}
        elif row["id"] == "profile.binding":
            allowed = {"actual_profile_hash"}
        elif row["id"] == "integrity.api":
            allowed = {
                "api_code",
                "response_digest",
                "received_at",
                "request_hash",
                "provider_id",
            }
        if set(meta) - allowed:
            raise ValueError("UNRELATED_METADATA")
    warnings = sorted(
        {r["reason"] for r in rows if r["status"] in ("BLOCKED", "MISSING", "NOT_RUN")}
    )
    if report["warnings"] != warnings:
        raise ValueError("WARNINGS_MISMATCH")

    def package(row_id, name=None):
        r = by_id[row_id]
        state = "unknown"
        if r["status"] == "BLOCKED" and r["reason"] == "PERMISSION_DENIED":
            state = "permission_denied"
        elif r["reason"] == "PACKAGE_DISABLED":
            state = "disabled"
        elif r["status"] == "OBSERVED" and r["metadata"].get("enabled") is True:
            state = "enabled"
        return {
            "package_name": name or r["metadata"].get("package_name"),
            "state": state,
        }

    projected = {
        key: report[key]
        for key in (
            "format",
            "profile_id",
            "profile_hash",
            "artifact_hash",
            "android_user_id",
            "timestamp",
        )
    }
    projected.update(
        play_services=package(
            "package.com.google.android.gms", "com.google.android.gms"
        ),
        play_store=package("package.com.android.vending", "com.android.vending"),
        web_provider=package("web_provider.state"),
        play_protect="unknown",
        google_settings_access="unknown",
        google_account={
            "VISIBLE_ACCOUNT": "associated",
            "PERMISSION_DENIED": "permission_denied",
        }.get(by_id["account.association"]["reason"], "unknown"),
        integrity_api="available"
        if by_id["integrity.api"]["reason"] == "TOKEN_RECEIVED"
        else "unknown",
        warnings=(
            ["PERMISSION_LIMITATION"]
            if any(r["reason"] == "PERMISSION_DENIED" for r in rows)
            else []
        )
        + (
            ["PACKAGE_LIMITATION"]
            if any(
                r["status"] == "BLOCKED" and r["reason"] != "PERMISSION_DENIED"
                for r in rows
            )
            else []
        ),
        failures=["LOCAL_CHECK_FAILED"]
        if any(r["status"] == "FAIL" for r in rows)
        else [],
    )
    if by_id["integrity.api"]["reason"] == "TOKEN_RECEIVED":
        meta = by_id["integrity.api"]["metadata"]
        projected["integrity_response"] = {
            k: meta[k] for k in ("response_digest", "received_at", "provider_id")
        }
    if report["readiness_report"] != projected:
        raise ValueError("READINESS_PROJECTION_MISMATCH")
    return report


def write_json(path: Path, value):
    # Exclusive file creation: evidence is never overwritten.
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(value, stream, indent=2, ensure_ascii=True)
        stream.write("\n")


def bundle(output: Path, report, provenance, cleanup):
    write_json(output / "report.json", report)
    write_json(output / "readiness-report.json", report["readiness_report"])
    write_json(output / "provenance.json", provenance)
    write_json(output / "cleanup.json", cleanup)
    counts = Counter(r["status"] for r in report["rows"])
    text = "Play/Google evidence · local observations only\n\n"
    text += (
        "Result: "
        + ("CHECK_FAILURES" if counts["FAIL"] else "EVIDENCE_CAPTURED")
        + "\n"
    )
    text += (
        "Counts: "
        + ", ".join(
            f"{s}={counts[s]}"
            for s in ("PASS", "FAIL", "OBSERVED", "BLOCKED", "MISSING", "NOT_RUN")
        )
        + "\n"
    )
    text += "Warnings: " + ", ".join(report["warnings"]) + "\n"
    text += "Profile binding: supplied file hash; artifact binding: caller-supplied digest.\n"
    text += "Caller visibility limits apply. No platform verdict is claimed.\n"
    (output / "summary.md").write_text(text, encoding="utf-8")
    path = output / "evidence-bundle.zip"
    with zipfile.ZipFile(path, "x", zipfile.ZIP_DEFLATED) as archive:
        for name in (
            "report.json",
            "readiness-report.json",
            "provenance.json",
            "cleanup.json",
            "summary.md",
        ):
            archive.write(output / name, name)
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    (output / "evidence-bundle.sha256").write_text(
        digest + "  evidence-bundle.zip\n", encoding="ascii"
    )
    return counts, digest


class Adb:
    def __init__(self, executable, serial, user):
        self.prefix = [executable, "-s", serial]
        self.user = user
        self.root_private = False

    def root(self, command, data=None, check=True):
        # exec-out preserves exact profile/report bytes. It does not reliably expose
        # the remote exit status, so require our own terminal status marker.
        marker = "CLOAK_EVIDENCE_EXIT_" + uuid.uuid4().hex
        wrapped = command + f'\ncode=$?\nprintf "\\n{marker}=%s\\n" "$code"'
        # Shell v2 forwards stdin EOF; exec-out can leave a remote `cat` waiting
        # forever after local stdin closes. Disable PTY to retain exact bytes.
        arguments = (("shell", "-T", "su", "--mount-master", "-c", shlex.quote(wrapped))
                     if data is not None else ("exec-out", "su", "--mount-master", "-c", wrapped))
        result = self.call(*arguments, data=data, check=False)
        body, separator, status = result.stdout.rpartition(("\n" + marker + "=").encode())
        if result.returncode or not separator or not re.fullmatch(rb"[0-9]+(?:\r?\n)?", status):
            raise RuntimeError("DIAGNOSTICS_PRIVATE_ACCESS_FAILED")
        result.stdout, result.returncode = body, int(status.strip())
        if check and result.returncode:
            raise RuntimeError("DIAGNOSTICS_PRIVATE_ACCESS_FAILED")
        return result

    def enable_root_private_access(self):
        base = f"/data/user/{self.user}/{PACKAGE}"
        uid = self.root(f"stat -c %u {base}").stdout.decode().strip()
        if not uid.isdigit() or int(uid) // 100000 != self.user or not 10000 <= int(uid) % 100000 < 20000:
            raise ValueError("DIAGNOSTICS_UID_MISMATCH")
        # Create only diagnostics-private directories, with the actual app UID and
        # its SELinux label, before the app attempts an atomic report write.
        self.root(f"mkdir -p {base}/files/inputs && "
                  f"chown {uid}:{uid} {base}/files {base}/files/inputs && "
                  f"chmod 700 {base}/files {base}/files/inputs && "
                  f"restorecon -F {base}/files && restorecon -RF {base}/files/inputs")
        self.private_uid = uid
        self.root_private = True

    def saved_identity(self, identity):
        if not self.root_private or not re.fullmatch(r"[A-Za-z0-9_-]{1,96}", identity):
            raise ValueError("INVALID_SAVED_IDENTITY")
        return self.root(f"cat /data/user/{self.user}/dev.cyclone.cloak/files/profiles/{identity}.json").stdout

    def call(self, *args, data=None, timeout=20, check=True):
        result = subprocess.run(
            self.prefix + list(args),
            input=data,
            capture_output=True,
            timeout=timeout,
            check=False,
        )
        if check and result.returncode:
            raise RuntimeError(
                "ADB_COMMAND_FAILED"
            )  # Never echo untrusted device output/profile bodies.
        return result

    def own(self, command, data=None, check=True):
        if self.root_private:
            allowed = (r"sha256sum /[A-Za-z0-9_./=+~-]+", r"mkdir -p files/inputs",
                       r"(?:cat >|cat|rm -f|test ! -e) files/(?:inputs|reports)/[A-Za-z0-9_-]{1,80}\.json")
            if not any(re.fullmatch(pattern, command) for pattern in allowed):
                raise ValueError("DIAGNOSTICS_COMMAND_NOT_ALLOWED")
            base = f"/data/user/{self.user}/{PACKAGE}"
            if command.startswith("cat > "):
                target = base + "/" + command.removeprefix("cat > ")
                command += (f" && chown {self.private_uid}:{self.private_uid} {target}"
                            f" && chmod 600 {target} && restorecon -F {target}")
            return self.root(f"cd {shlex.quote(base)} && {command}", data=data, check=check)
        result = self.call(
            "shell",
            "run-as",
            PACKAGE,
            "--user",
            str(self.user),
            "sh",
            "-c",
            "'" + command + "'",
            data=data,
            check=False,
        )
        if result.returncode and b"not debuggable" in result.stderr:
            raise ValueError("SIGNED_APK_REQUIRES_EXPLICIT_PRIVATE_ACCESS")
        if check and result.returncode:
            raise RuntimeError("ADB_COMMAND_FAILED")
        return result


def capture(args, output: Path):
    saved_identity = getattr(args, "saved_identity", None)
    if (
        not all((args.profile or saved_identity, args.profile_id, args.artifact_digest, args.serial))
        or args.user is None
    ):
        raise ValueError("DEVICE_BINDING_REQUIRED")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._:-]{0,95}", args.profile_id):
        raise ValueError("INVALID_PROFILE_ID")
    if (
        not re.fullmatch(r"[0-9a-f]{64}", args.artifact_digest)
        or not 0 <= args.user <= 21474
    ):
        raise ValueError("INVALID_BINDING")
    adb = Adb(args.adb, args.serial, args.user)
    access = getattr(args, "private_access", "run-as")
    if access == "root":
        adb.enable_root_private_access()
    if saved_identity and (access != "root" or args.profile):
        raise ValueError("SAVED_IDENTITY_REQUIRES_ROOT_ACCESS")
    raw_profile = adb.saved_identity(saved_identity) if saved_identity else args.profile.read_bytes()
    if load_json(raw_profile, 262144).get("schema_version") not in (
        "0.2",
        "0.3",
        "0.4",
    ):
        raise ValueError("INVALID_PROFILE_INPUT")
    request = str(uuid.uuid4())
    expected = {
        "request_id": request,
        "profile_id": args.profile_id,
        "profile_hash": hashlib.sha256(raw_profile).hexdigest(),
        "artifact_hash": args.artifact_digest,
        "android_user_id": args.user,
    }
    provenance = {
        "format": 1,
        "mode": "device_user_initiated",
        "binding": expected,
        "integrity_requested_by_runner": False,
        "wait_for_user_integrity_request": args.wait_integrity,
        "private_access": access,
        "profile_source": "saved_identity_read_only" if saved_identity else "local_file",
    }
    cleanup = {"input_removed": False, "report_removed": False}
    staged = False
    try:
        paths = (
            adb.call("shell", "pm", "path", "--user", str(args.user), PACKAGE)
            .stdout.decode()
            .splitlines()
        )
        apk_paths = [line[8:].strip() for line in paths if line.startswith("package:")]
        if len(apk_paths) != 1 or not re.fullmatch(
            r"/[A-Za-z0-9_./=+~-]+", apk_paths[0]
        ):
            raise ValueError("DIAGNOSTICS_APK_NOT_INSTALLED_OR_SPLIT")
        digest = adb.own("sha256sum " + apk_paths[0]).stdout.decode().split()[0]
        if not re.fullmatch(r"[0-9a-f]{64}", digest):
            raise ValueError("INVALID_INSTALLED_APK_HASH")
        provenance.update(diagnostics_apk_sha256=digest, diagnostics_package=PACKAGE)
        adb.own("mkdir -p files/inputs")
        staged = True
        adb.own(f"cat > files/inputs/{request}.json", data=raw_profile)
        adb.call(
            "shell",
            "am",
            "start",
            "--activity-clear-top",
            "--activity-single-top",
            "--user",
            str(args.user),
            "-n",
            PACKAGE + "/.EvidenceActivity",
            "--es",
            "request_id",
            request,
            "--es",
            "profile_id",
            args.profile_id,
            "--es",
            "profile_hash",
            expected["profile_hash"],
            "--es",
            "artifact_hash",
            args.artifact_digest,
            "--ei",
            "android_user_id",
            str(args.user),
        )
        print(
            "On the phone, tap Collect local evidence (or explicitly request Integrity). Waiting for your report.",
            flush=True,
        )
        deadline = time.monotonic() + args.timeout
        while time.monotonic() < deadline:
            result = adb.own(f"cat files/reports/{request}.json", check=False)
            if result.returncode == 0 and result.stdout:
                report = validate_report(result.stdout, expected)
                row = next(r for r in report["rows"] if r["id"] == "integrity.api")
                if not args.wait_integrity or row["reason"] != "USER_NOT_REQUESTED":
                    return report, provenance, cleanup
            time.sleep(1)
        raise TimeoutError("USER_REPORT_TIMEOUT")
    finally:
        if staged:
            # Only our random, fixed-format request files, under this diagnostics UID.
            for kind, key in (
                ("inputs", "input_removed"),
                ("reports", "report_removed"),
            ):
                try:
                    adb.own(f"rm -f files/{kind}/{request}.json")
                    cleanup[key] = (
                        adb.own(
                            f"test ! -e files/{kind}/{request}.json", check=False
                        ).returncode
                        == 0
                    )
                except (RuntimeError, subprocess.TimeoutExpired):
                    cleanup[key] = False
        write_json(
            output / "capture-attempt.json",
            {"provenance": provenance, "cleanup": cleanup},
        )


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--report",
        type=Path,
        help="Validate/bundle an already exported report without device access",
    )
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial")
    parser.add_argument("--user", type=int)
    parser.add_argument("--profile", type=Path)
    parser.add_argument("--saved-identity", help="Read this saved identity in memory only; requires explicit root private access")
    parser.add_argument("--private-access", choices=("run-as", "root"), default="run-as",
                        help="Signed APKs disable run-as. Root is opt-in and scoped to diagnostics-private files and the selected identity read.")
    parser.add_argument("--profile-id")
    parser.add_argument("--artifact-digest")
    parser.add_argument(
        "--wait-integrity",
        action="store_true",
        help="Wait for an explicit on-device API request, never trigger it",
    )
    parser.add_argument("--timeout", type=int, default=240)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)
    if not 1 <= args.timeout <= 900:
        parser.error("timeout must be 1..900 seconds")
    output = args.output or ROOT / "docs/runtime-evidence" / (
        datetime.now(UTC).strftime("%Y-%m-%d-%H%M%S")
        + "-play-evidence-"
        + uuid.uuid4().hex[:8]
    )
    output.mkdir(parents=True, exist_ok=False)
    try:
        if args.report:
            report = validate_report(args.report.read_bytes())
            provenance, cleanup = (
                {
                    "format": 1,
                    "mode": "offline_validation",
                    "device_binding_verified": False,
                },
                {"not_applicable": True},
            )
        else:
            report, provenance, cleanup = capture(args, output)
            if not all(cleanup.values()):
                raise RuntimeError("DIAGNOSTICS_CLEANUP_FAILED")
        _, digest = bundle(output, report, provenance, cleanup)
        print(f"Evidence: {output}\nSHA-256: {digest}")
        return 1 if any(r["status"] == "FAIL" for r in report["rows"]) else 0
    except Exception as error:  # noqa: BLE001 -- boundary preserves only fixed codes, never private exception text
        # Fixed error type only. Never persist arbitrary exception messages or rejected payloads.
        code = (
            str(error)
            if isinstance(error, (ValueError, RuntimeError, TimeoutError))
            and re.fullmatch(r"[A-Z_]{1,64}", str(error))
            else "INVALID_EVIDENCE_OR_DEVICE_ERROR"
        )
        write_json(
            output / "failure.json",
            {"status": "FAILED", "reason": code, "error_type": type(error).__name__},
        )
        print(f"Stopped: {code}. Evidence: {output}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
