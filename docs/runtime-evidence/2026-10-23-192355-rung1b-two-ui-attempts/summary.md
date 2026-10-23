# Rung 1b coherent v3 final — measured completion

Result: **COMPLETE_WITH_LIMITATIONS**. Location UI resolved on the first newly
authorized tap; two cold user-17 captures and a settled third completed with
coherent fixture C under the unchanged loaded alpha.28 candidate. No release,
tag, commit, push, module install/rebuild or reboot. No platform verdict.

## Captures and determinism

| Cold capture | PID | Legacy PASS/OBSERVED/BLOCKED/FAIL | Envelope PASS/OBSERVED/MISSING/FAIL | Linked PASS/FAIL |
| --- | --- | --- | --- | --- |
| user17 C 1 | 28316 | 62/25/2/0 | 8/3/5/0 | 13/0 |
| user17 C 2 | 28760 | 62/25/2/0 | 8/3/5/0 | 13/0 |
| user17 C settled 3 | 29223 | 62/25/2/0 | 8/3/5/0 | 13/0 |

Each Device Reality section: 14 PASS / 17 OBSERVED / 4 MISSING / 8 UNMAPPED.
All three have distinct PIDs, correct Android user 17, 89 unique legacy rows,
matching STARTED/COMPLETED request markers, valid filesystem schema, and the same
raw fixture hash. Canonical published-body hash matched independently each time.
Report label is `external`, with explicit C source/hash provenance; it is not
silently labelled A. Publication/verification JSON is preserved per capture.

Retained actual digests are identical across all three for every PASS extension
row: 8 envelope, 14 Reality and 13 linked. All 62 legacy PASS rows use exact MATCH
against the same fixed profile expectations. Raw legacy actuals are redacted;
their exact-comparison stability is supported by repeated MATCH, not independent
retained actual-value hashes. No type-only/optional observation is called stable
by virtue of passing a type check. See determinism.json.

**user-17 legacy determinism: CONFIRMED post-grants**.
No counts from earlier failed or incoherent runs are relabelled. Lower PASS versus
older synthetic fixtures reflects truthful omission of unobservable identifiers,
not newly fabricated host values. All 25 OBSERVED remain OBSERVED. The two legacy
BLOCKED rows are camera.characteristics and sensors.list, each with exact reason
PROFILE_SURFACE_UNAVAILABLE. Filesystem permission-denied rows remain separate
BLOCKED/NOT_COMPARABLE observations, not silently added to legacy PASS counts.

## Evidence-scoped path ledger

Of the original 17 PARTIAL paths, 8 are measured APPLIED in this ledger (7 new,
SIM state reaffirmed), 9 remain PARTIAL; zero VERIFIED promotions. No generated
runtime contract status or raw report row was changed.

| Paths | Measured rows | Outcome |
| --- | --- | --- |
| carrier.values.sim_state | both envelope SIM-state readers | APPLIED reaffirmed, PASS x3 |
| compute.values.gpu | envelope.compute.gpuRenderer (real EGL/GLES surface) | APPLIED, PASS x3 |
| packages.values.installed plus [].package / [].version_code / [].version_name | full canonical package/version aggregate | four APPLIED paths, PASS x3 |
| webview.values.provider / .version | matching envelope and Reality readers | two APPLIED paths, PASS x3 |
| sensors.values.inventory and six child paths | inventory reader | PARTIAL, PROFILE_SURFACE_UNAVAILABLE |
| memory.values.free_storage_gib | availableBytes reader | PARTIAL, PROFILE_SURFACE_UNAVAILABLE |
| webview.values.user_agent | default UA passes; concrete instance reader missing | PARTIAL; instance reader UNVERIFIED |

All paths above have the canonical `reality.` prefix (see the full paths in
evidence-ledger-promotions.json). Package aggregate comparison includes all three
child fields rather than separately fabricating row results. The generic Reality
GPU row remains UNMAPPED; the concrete envelope reader is separately identified.
These are measured matches on this artifact and host-authored C, not proof of
counterfactual override effectiveness, cross-user isolation or general enforcement.
Default-UA PASS does not establish concrete instance-UA coverage.

## UI completion and prior smoke

The previous failed v3 directories are untouched. In this new authorized resume,
the exact current While-using-app button was observed at [133,1483][947,1609].
One standard adb input tap at (540,1546) cleared location within 2.71 seconds;
the second authorized attempt was unnecessary. The observed nearby Allow control
at (540,1423) also resolved. Final and pre-third hierarchies showed the probe report,
with no pending permission dialog. Permission setup is extra retained activity,
not counted among the three measured cold captures. No alternate tap, force-close
of the permission controller, shell grant or AppOps write was used. The usual
diagnostics force-stop before each measured cold capture was retained.

The preceding final-resume user-0 smoke already completed with this same APK:
PID15193, 82 PASS / 7 OBSERVED legacy, 13 linked PASS. Its full evidence remains at
../2026-10-23-191424-rung1b-coherent-v3-final/. This resume did not relabel or rerun
that smoke. The optional missing-SIM-serial branch now also completes in all three
real C captures, with subscriber ID OBSERVED/NO_PROFILE_EXPECTATION.

## Provenance, audits and cleanup

Main remains d4f0a5cb5f4d70bacaeb0b8ecdff184eff154150. Reused passing diagnostics APK
SHA-256: `283239cf2d25c3f20d1534232a64ef830cf1433d3823665598d188937c1b091a`.
Both installed-user hashes matched; archived diagnostics-measured.apk is identical.
Coherent C raw SHA-256:
`c78353910306cda7b0897f3310d2637a7568ec4925de8d63822645cf8a9e8f66`.
Full schema/coherence validation passes with one host-date PATCH_AFTER_BUILD warning.
The approved `large` display class is a compatibility classification, not a new
physical diagonal or Android screenLayout measurement. Missing hardware facts and
identifiers are not invented. Shared WebView UA validation and sensor-rule mirror
changes are the already-tested dirty prerequisites; no new product code here.

Loaded candidate provenance is the previously validated ZIP 8fd58b4ad0ea60ec06460b0ecaa0b9cea8e56467437975103d04392958a68795,
with ARM64 d7dd105446a229f981e5cdd1aed862a13e3c7d04220f04fb08eadde175bb15c1.
Current local ZIP differs and was NOT installed. Installed immutable files match
the previous manifest and before/after; mutable durable load-log bytes are observed
separately. All three durable records match package/user/PID/packaged ABI digest.
The records prove bound pre-specialization, not platform attestation or all hooks.

Temporary overlays for both probe users removed through the existing lock/CAS
cleanup; all temporary profile bodies removed. Diagnostics globally uninstalled;
CE, DE and external probe directories absent for users 0/17. UI scratch removed.
User 0 active. Persisted production APK, grants, stable AppOps, accessible binding/
index bytes, published bodies and publisher shares compare exactly equal. Normal
diagnostics-only UI grants were retained as evidence before uninstall; no production
permission change, account operation, production launch or installation occurred.

## Tests, commands and limitations

Prior combined v3 gate: app 784, module 80, standard probe 148, diagnostics probe148,
forge/schema 1303, host probe34, all passed. Diagnostics build passed. Those build
logs and copied test counts are explicitly labelled prior local gate. Current
shared UA regression recheck: 8 passed. Whitespace/cached diff checks captured;
nothing staged. Exact device argv/exit evidence is commands.jsonl; run-only scripts
and source/prerequisite patch are included. No new compiler run/warnings here;
prior Android debug app exposed a 16 KB libprobe alignment warning in the previous
user0 setup, documented there, not presented as release readiness.

## Open questions — exact retained instance-UA evidence

Verbatim report fields on all three cold captures:
`id=envelope.webview.instanceUa`, `status=MISSING`, `reason=RuntimeException`.
No full RUNTIME_EXCEPTION stack was present in the bounded retained target-PID
buffers. Therefore no stack or underlying cause is invented, and no historical
stack is quoted as a current capture. Instance UA remains UNVERIFIED in the ledger
and raw MISSING in the report; no UA workaround or further fix attempted.

Free-space and sensor descriptor readers remain PROFILE_SURFACE_UNAVAILABLE under
this unchanged loaded artifact. Camera/DRM limitations also remain in the envelope.
No physical hardware, platform verdict, trust, broad compatibility, release, or
VERIFIED claim is made. This is measured local equality on the recorded artifact.
