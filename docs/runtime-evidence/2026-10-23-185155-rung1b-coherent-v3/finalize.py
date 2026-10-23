"""Run-only evidence finalization; no device actions."""
from pathlib import Path
import collections
import hashlib
import json
import subprocess
import zipfile

O = Path(__file__).resolve().parent
R = O.parents[2]

def read(name):
    return json.loads((O / name).read_text(encoding="utf-8-sig"))

def save(name, value):
    (O / name).write_text(json.dumps(value, indent=2) + "\n", encoding="utf8")

def module_files(name):
    return {path: digest for digest, path in
            (line.split(None, 1) for line in read(name)["files"].splitlines())}

before = module_files("module-before.json")
after = module_files("module-after.json")
changed = sorted(k for k in before.keys() | after.keys() if before.get(k) != after.get(k))
save("module-comparison.json", {
    "immutable_equal": all(before.get(k) == after.get(k) for k in before.keys() | after.keys()
                           if not k.endswith("/load-records.jsonl")),
    "raw_changed_paths": changed,
    "mutable_load_log_observed_separately": True,
    "no_module_install_or_reboot": True,
})
launch = read("user-17-capture-10-launch.json")
markers = read("user-17-capture-10-markers.json")
request = launch["request_id"]
save("capture-stop-diagnosis.json", {
    "result": "NOT_VALIDATED",
    "phase": "user-17-capture-10-permission-setup-retest",
    "reason": "PROBE_REPORT_TIMEOUT",
    "request_id": request,
    "current_request_start_seen": bool(markers.get("start") and markers["start"].get("request_id") == request),
    "current_request_end_seen": bool(markers.get("end") and markers["end"].get("request_id") == request),
    "older_markers_preserved_not_counted": True,
    "permission_dialog_observed": "LOCATION",
    "post_tap_dialog_still_present": True,
    "measured_triplet_completed": 0,
    "permission_setup_attempts": [0, 10],
    "no_retry_after_timeout": True,
})
save("new-applied-promotions.json", {
    "APPLIED": [], "VERIFIED": [],
    "instance_ua_ledger_state": "UNVERIFIED",
    "raw_instance_ua_status_not_reclassified": True,
    "reason": "No completed coherent-v3 measurement; prior evidence remains historical.",
})
summary = """# Rung 1b coherent v3 — capture gate stop

Result: **NOT_VALIDATED**. Fixture C now passes schema and full coherence validation,
but the requested three cold user-17 measurements were **NOT RUN**. No APPLIED or
VERIFIED promotions. This evidence is local diagnostics only, not a platform verdict.

## Fixture and code

The genuine default WebView UA (Version/4.0, Chrome, optional Build and `; wv`)
is accepted by shared Python/Kotlin/schema validation while existing Chrome UAs
remain supported. Model and Android-release consistency still apply. Existing
profile bytes/storage are not migrated. Regenerated Kotlin RealityRules mirrors
the already-updated sensor schema. Source changes remain unstaged/uncommitted.

Coherent C asset SHA-256:
`c78353910306cda7b0897f3310d2637a7568ec4925de8d63822645cf8a9e8f66`.
All available base facts and Reality facts derive from the preserved real host
export. Missing identifiers stay absent. The approved display class `large` is
a manager-approved compatibility classification from measured 1080x2400 @420dpi,
not a physical diagonal measurement or Android screenLayout observation. Full
validation has zero errors and one PATCH_AFTER_BUILD warning from the host's dates.
See fixture-validation.json and authoring-provenance.json; raw authoring export is
retained as explicitly requested and must not be treated as a public redacted report.

## Device gate and preserved failures

Setup capture 0 produced a structured CAPTURE_FAILED/NullPointerException after
operator.simCountry, at ProbeActivity.collect line 453. The next statement tried
to dereference absent identifiers.sim_serial to derive an expected subscriber ID.
The last-completed-row label does not establish a SIM-country reader failure.
Its failed report and matching STARTED/FAILED markers remain unchanged.

The optional-expectation fix was completed: missing/short SIM serial now supplies
no subscriber expectation, preserving OBSERVED/BLOCKED semantics. Focused tests
cover absent/short serial and unchanged available-serial derivation. The rebuilt
diagnostics APK was installed once for this retest; both users' installed hashes
matched (optional-subscriber-reinstall.json). The original APK provenance is retained
separately in artifact-provenance.json; it is not relabelled as the retest artifact.

Retest setup capture 10 timed out after the runner's 60-second budget. Launch
returned exit 0 and PID 6969 appeared. The location permission controller was
visible in the UI hierarchy; screenshot pixels were black. The normal UI
While-using-app button was tapped once, but the next hierarchy still showed that
dialog. No shell grant or AppOps write was used. The readable private markers
belonged to the older request/PID 32520 and **do not prove this retest started**.
No matching new report appeared. See capture-stop-diagnosis.json, launch/marker
JSON, permissions-dialog-2.xml and bounded setup-retest-target-logcat.log.

This is the stop gate, not a successful permissions setup or measured capture.
No further launch, permission tap, speculative variant or UA investigation was run.
The settled triplet, legacy counts, retained-PASS digest determinism and new
PARTIAL-to-APPLIED conversion are unavailable. Prior historical counts are not
reclassified. Instance UA remains **UNVERIFIED** in the ledger; prior raw exception
classes/statuses are unchanged, and no new instance-UA stack was reached here.

## Cleanup and preservation

Temporary user-17 overlay removed through the existing CAS/lock cleanup path.
Both users' diagnostics APK/private/DE/external directories are absent; scratch
UI XML removed. User 0 active. Production APK, all accessible binding/index files,
published bodies, publisher shares, production permission grants and stable AppOps
match the persisted before snapshot exactly (audit-comparison.json).

The loaded candidate module was neither installed nor rebuilt/rebooted by this run.
Immutable files match before/after; its durable load log is mutable and recorded
separately in module-comparison.json. Current local ZIP differs from the previously
loaded candidate and was **not installed**. See exact hashes in artifact-provenance,
module-before/after and optional-subscriber-reinstall JSON. No production app launch,
account action, commit, push, release, tag or version bump.

## Local verification

- App: **784 passed**; module: **80 passed**.
- Probe: **148 passed per flavor**, standard and diagnostics; diagnostics APK built.
- Forge/schema combined: **1,303 passed** after fixing generated Reality rule drift.
- Probe host tests: **34 passed**.
- Initial rule-drift failure and successful retest logs both preserved.
- No compiler warning/error lines in final probe build; Git LF/CRLF notices are
  repository line-ending notices. See diff-check logs for final whitespace gate.

Exact commands are in exact-commands.txt; device argv/exit records in commands.jsonl.
The run-only patch includes dirty prerequisite changes for provenance, not a clean
feature commit. No assertion that every patch line was authored in this run.

## Open questions

The normal location dialog remained pending despite the single observed button tap.
This run does not diagnose the black screenshot or permission controller behavior.
The manager must authorize a new setup/capture lane if further measurements are
desired. No broad compatibility or trust claim follows from the local gates.
"""
(O / "summary.md").write_text(summary, encoding="utf8")
commands = r"""JAVA_HOME=C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot
CLOAK_TEST_WSL=1
%TEMP%/cloak-build/gradle-8.11.1/bin/gradle.bat -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testDiagnosticsDebugUnitTest :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests forge/schema_tests -q
uv run --offline -- python -X utf8 tools/generate_reality_rules.py --check
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
%TEMP%/cloak-build/gradle-8.11.1/bin/gradle.bat -p android :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --offline --with jsonschema -- python -X utf8 docs/runtime-evidence/2026-10-23-185155-rung1b-coherent-v3/run_ops.py install
uv run --offline --with jsonschema -- python -X utf8 docs/runtime-evidence/2026-10-23-185155-rung1b-coherent-v3/run_ops.py capture 17 0
# Diagnostics-only reinstall after optional subscriber expectation fix: argv/hashes in commands.jsonl and optional-subscriber-reinstall.json.
uv run --offline --with jsonschema -- python -X utf8 docs/runtime-evidence/2026-10-23-185155-rung1b-coherent-v3/run_ops.py capture 17 10
# Read-only UI hierarchy/screenshot and one normal location-button tap; see permission-ui-observation.json.
uv run --offline --with jsonschema -- python -X utf8 docs/runtime-evidence/2026-10-23-185155-rung1b-coherent-v3/run_ops.py cleanup
git diff --check
git diff --cached --check
"""
(O / "exact-commands.txt").write_text(commands, encoding="utf8")
for args, name in [(["git", "diff", "--binary"], "run-only-dirty-prerequisites.patch"),
                   (["git", "status", "--short"], "git-status-final.txt"),
                   (["git", "rev-parse", "HEAD"], "git-head.txt")]:
    result = subprocess.run(args, cwd=R, capture_output=True)
    (O / name).write_bytes(result.stdout)

sources = ["android/probe/src/main/assets/profile-c.json",
           "android/probe/src/test/java/dev/cyclone/cloak/probe/OptionalExpectationsTest.java",
           "forge/tests/test_webview_ua.py"]
for source in sources:
    target = O / "untracked-source-provenance" / source
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes((R / source).read_bytes())
print("STOP_SUMMARY_AND_PROVENANCE_WRITTEN")
