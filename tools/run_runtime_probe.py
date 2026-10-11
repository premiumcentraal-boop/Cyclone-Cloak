"""Measure the installed module using only a temporary binding for the probe app.

Does not install/update/reboot the module. Existing app bindings are preserved.
Reports contain hashes instead of raw observed host identifiers.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shlex
import subprocess
import tempfile
import time

PACKAGE = "dev.cyclone.cloak.probe"
ROOT = "/data/adb/cyclone_cloak/state-v1"
REPO = Path(__file__).resolve().parents[1]


def edited_index(index, user, key, profile_id, owned_keys):
    """Modify one test tuple only; refuse to replace someone else's binding."""
    result = json.loads(json.dumps(index))
    if result.get("schemaVersion") != 2 or not isinstance(result.get("entries"), dict):
        raise ValueError("Unsupported index format")
    entries = result["entries"]
    tuple_key = f"{user}/{PACKAGE}"
    existing = entries.get(tuple_key)
    if existing is not None and existing.get("key") not in owned_keys:
        raise ValueError("Probe package already has a binding; refusing to replace it")
    if key is None:
        entries.pop(tuple_key, None)
    else:
        entries[tuple_key] = {"profileId": "runtime-probe", "cloakProfileId": profile_id, "key": key}
    return result


def redact(report):
    report = json.loads(json.dumps(report))
    for row in report["rows"]:
        if "actual" in row:
            encoded = json.dumps(row.pop("actual"), sort_keys=True, ensure_ascii=True).encode()
            row["actual_sha256"] = hashlib.sha256(encoded).hexdigest()
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--user", type=int, required=True)
    parser.add_argument("--apk", type=Path, default=REPO / "android/probe/build/outputs/apk/debug/probe-debug.apk")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.user < 0:
        parser.error("--user must be nonnegative")

    def adb(*command, check=True):
        return subprocess.run([args.adb, "-s", args.serial, *command], check=check,
                              capture_output=True, text=True, encoding="utf-8", timeout=40)

    def shell(command, check=True):
        return adb("shell", command, check=check)

    def root(command):
        return shell("su -c " + shlex.quote("set -eu; " + command)).stdout

    def current_index():
        return json.loads(root(f"cat {ROOT}/index.json"))

    profiles = {f: json.loads((REPO / f"android/probe/src/main/assets/profile-{f}.json").read_text()) for f in ("a", "b")}
    # A fresh nonce avoids collisions with production profile directories.
    nonce = str(time.time_ns())
    keys = {f: hashlib.sha256(f"runtime-probe:{nonce}:{args.user}:{f}".encode()).hexdigest() for f in profiles}
    owned = set(keys.values())
    metadata = {
        "measured_at_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "host_api": shell("getprop ro.build.version.sdk").stdout.strip(),
        "host_model": shell("getprop ro.product.model").stdout.strip(),
        "module_properties": root("cat /data/adb/modules/cyclone_cloak/module.prop").strip(),
        "module_arm64_sha256": root("sha256sum /data/adb/modules/cyclone_cloak/zygisk/arm64-v8a.so").split()[0],
    }
    edited_index(current_index(), args.user, None, None, owned)  # refuse existing probe binding before install
    installed_before = bool(shell(f"pm path --user {args.user} {PACKAGE}", check=False).stdout.strip())
    adb("install", "--user", str(args.user), "-r", str(args.apk.resolve()))
    stage = f"/data/local/tmp/cloak-runtime-probe-{nonce}"
    shell(f"mkdir {stage}; chmod 700 {stage}")
    created = []
    results = []
    bound = False
    with tempfile.TemporaryDirectory(prefix="cloak-runtime-probe-") as local:
        local = Path(local)

        def bind(fixture):
            nonlocal bound
            index = edited_index(current_index(), args.user, keys[fixture] if fixture else None,
                                 profiles[fixture]["id"] if fixture else None, owned)
            (local / "index.json").write_text(json.dumps(index), encoding="utf-8")
            adb("push", str(local / "index.json"), f"{stage}/index.json")
            # Set before mutation so finally also handles uncertain command completion.
            if fixture is not None:
                bound = True
            root(f"cp {stage}/index.json {ROOT}/index.probe.tmp; chmod 600 {ROOT}/index.probe.tmp; "
                 f"mv {ROOT}/index.probe.tmp {ROOT}/index.json")
            bound = fixture is not None

        def run(name, fixture):
            shell(f"am force-stop --user {args.user} {PACKAGE}")
            shell(f"run-as {PACKAGE} --user {args.user} rm -f files/report.json", check=False)
            launch = shell(f"am start --user {args.user} -n {PACKAGE}/.ProbeActivity --es fixture {fixture}")
            if "Error" in launch.stdout or "Exception" in launch.stderr:
                raise RuntimeError("Probe launch failed: " + launch.stdout + launch.stderr)
            deadline = time.monotonic() + 45
            while time.monotonic() < deadline:
                output = shell(f"run-as {PACKAGE} --user {args.user} cat files/report.json", check=False)
                if output.returncode == 0:
                    report = json.loads(output.stdout)
                    if report["fixture"] != fixture or report["uid"] // 100000 != args.user:
                        raise RuntimeError("Report fixture/user does not match launch")
                    report["run"] = name
                    results.append(redact(report))
                    print(f"{name}: " + ", ".join(f"{s}={sum(r['status'] == s for r in report['rows'])}"
                                                  for s in ("PASS", "FAIL", "BLOCKED", "OBSERVED")), flush=True)
                    return
                time.sleep(1)
            raise RuntimeError("Probe produced no report within 45 seconds; inspect app crash logs")

        try:
            for fixture, profile in profiles.items():
                (local / "profile.json").write_text(json.dumps(profile), encoding="utf-8")
                adb("push", str(local / "profile.json"), f"{stage}/profile.json")
                root(f"test ! -e {ROOT}/{keys[fixture]} && mkdir {ROOT}/{keys[fixture]}")
                created.append(keys[fixture])
                root(f"cp {stage}/profile.json {ROOT}/{keys[fixture]}/profile.json; "
                     f"chmod 700 {ROOT}/{keys[fixture]}; chmod 600 {ROOT}/{keys[fixture]}/profile.json")
            run("unbound-before", "a")
            bind("a")
            run("bound-a", "a")
            run("bound-a-repeat", "a")
            bind("b")
            run("bound-b", "b")
            bind(None)
            run("unbound-after", "a")
        finally:
            # Preserve concurrent changes to other entries; remove only our tuple.
            if bound:
                bind(None)
            shell(f"am force-stop --user {args.user} {PACKAGE}", check=False)
            for key in created:
                root(f"rm -f {ROOT}/{key}/profile.json; rmdir {ROOT}/{key}")
            root(f"rm -f {stage}/index.json {stage}/profile.json; rmdir {stage}")
            if not installed_before:
                adb("shell", f"pm uninstall --user {args.user} {PACKAGE}", check=False)
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(json.dumps({"format": 1, "user": args.user, "metadata": metadata,
                                              "artifact": metadata["module_arm64_sha256"], "runs": results}, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
