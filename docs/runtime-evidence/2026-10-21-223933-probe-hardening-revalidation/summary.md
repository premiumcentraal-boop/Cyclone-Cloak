# Probe hardening revalidation — INCOMPLETE, stop gate reached

## Device result

The rebuilt diagnostics APK installed successfully. Its installed SHA-256 matched
for users 0 and 17: `f742a9f212f2b61c3f17ae4eeb08a959433aa938ac319237b5daf88dde191147`.
Both users were running and unlocked at preflight. Only user 0 was captured.

The first user-0 capture returned an enriched structured failure:

- reason: `CAPTURE_FAILED`
- exception: `java.lang.NullPointerException`
- stage: `capture.runtime_envelope`
- top frame: `RuntimeEnvelopeReport.collect`, line 142
- next frame: `ProbeActivity$Capture.run`, line 183

Line 142 maps the optional SIM-state expectation using nested integer/null
conditional expressions, before construction of `envelope.telephony.simState`.
The stack localizes the exception to this expression, not the filesystem reader.
The failing row field records the collection stage because no SIM row existed
when the exception occurred. This code was not changed after the failed capture.
Raw exception text is deliberately replaced with `REDACTED_EXCEPTION_MESSAGE`;
class, bounded stack class/method/line and collection stage are retained.

The partial filesystem section completed and passed the unchanged strict schema:

| Surface | Status | Reason | Lines |
| --- | --- | --- | --- |
| `/system/build.prop` | BLOCKED | PERMISSION_DENIED | unavailable |
| `/proc/version` | BLOCKED | PERMISSION_DENIED | unavailable |
| `/proc/cpuinfo` | OBSERVED | OBSERVATION_ONLY_NO_EXPECTATION | 81 |
| `/proc/self/maps` | OBSERVED | OBSERVATION_ONLY_NO_EXPECTATION | 3301 |

Total: **2 OBSERVED, 2 BLOCKED, 0 MISSING** for this partial filesystem section.
No filesystem row is PASS or VERIFIED. This is not a successful full capture.
User 17 and determinism captures were NOT RUN following the failure stop gate.
No further code fixes or device retries followed the failed revalidation.

Cleanup: diagnostics uninstall succeeded; package paths absent for users 0/17.
Production APK/version/stable permission snapshots matched before/after.
No module install, root shell, reboot, production APK install/launch, binding
change, permission grant/reset, AppOps edit or user switch was performed.
Private production stores and module files were not independently audited.

## Local hardening

The pre-existing filesystem collector and tests were preserved byte-for-byte.
A new `FilesystemReadGuard` wraps it: null/absent/malformed streams become explicit
row limitations. Open/read/close are subject to a 3-second deadline and a bounded
worker pool. A non-interruptible kernel read can strand a worker, but cannot grow
threads indefinitely; capacity exhaustion is explicit. The existing observation
schema is unchanged: timeout rows are MISSING/READ_UNAVAILABLE, with precise
READ_TIMEOUT details in the separate `filesystem_reader_failures` report section.

`ProbeReadDeadline` adds corresponding bounded deadlines to legacy, Reality and
linked read operations. These do not cover arbitrary Android service setup outside
row reader callbacks. Such setup exceptions still produce a failed envelope.
Failure evidence contains no raw file contents, arbitrary exception messages or
source filenames. The existing capture-generation atomic-write guard is retained.
Filesystem results are collected before optional extensions and preserved on a
later capture failure.

## Tests and exact commands

From `C:/Users/Agent/Cyclone/cloak-repo`:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:testDiagnosticsDebugUnitTest --tests 'dev.cyclone.cloak.probe.FilesystemReadGuardTest' --offline --no-daemon --console=plain
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
git diff --check
uv run --offline --with jsonschema -- python -X utf8 C:/Users/Agent/AppData/Local/Temp/cloak_probe_hardening_revalidation.py
```

The initial red run: 3 tests, 1 failure (null stream produces NullPointerException).
Final green: **134 standard probe tests, 134 diagnostics probe tests, 8 host tests**.
Diagnostics APK build successful, no compiler warnings. LF/CRLF Git advisories only.
No production app/module/forge/schema full suite was rerun: scope is probe-only.
Observation-schema conformance was checked against the device's partial section.

## Scope, source and remaining work

Current HEAD: `d4f0a5cb5f4d70bacaeb0b8ecdff184eff154150`; APK includes uncommitted
observation prerequisites plus this hardening. Not a release artifact. The existing
module's binary provenance was not asserted. Pre-existing tracked changes outside
`android/probe` are identical to the baseline patch; see scope-preservation.json.

New helpers/tests and four tracked probe-source edits are listed in
scope-preservation.json. `hardening-only.patch` is relative to the pre-run working
tree, preserving the earlier uncommitted observation work. Source copies and both
red/green logs are included. Changes are left uncommitted: committing all current
probe files would also include pre-existing untracked observation prerequisites.
Nothing was staged, pushed, tagged or released.

Next run may test and repair the now-localized optional SIM-state expression and
repeat user-scoped captures. That fix was intentionally not attempted under this
handoff's stop gate. No platform verdict, parity verification or broader coverage
claim is made. All prior evidence remains intact.
