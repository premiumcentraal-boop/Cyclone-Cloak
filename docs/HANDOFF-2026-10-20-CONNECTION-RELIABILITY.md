# Connection and large-library reliability — 2026-10-20

Base: Cloak main a51c6d1; published alpha.27/code 51. This is a repair candidate,
not a new release or a claim of complete device compatibility.

## Changes

- Report a live approved connector handshake and registry immediately, while the
  existing serialized reconciliation continues. Cached state cannot establish a
  connection. Health explicitly warns when reconciliation is still running.
- Keep single-flight reconnect behavior, preserve Connected during a coalesced
  follow-up, and still surface a later failure instead of hiding it.
- Stop describing every BAD_REQUEST as a missing app. Capacity and payload
  rejection use the same code; retain local records and show an honest reason.
- Put Check & repair near the top of Health. Paginate readiness checks in groups
  of twenty; verdicts, counts and copied diagnostics still cover every check.
- Make intentionally permission-blocked probe reads lint-clean and guard two
  Android 30 APIs for the probe's Android 29 minimum. No permission changes.

## Dependency repair

The device has 327 enabled local bindings but only 256 Cyclone configuration
records. Cyclone's installed APK and current dev source enforce a 256-record
quota per connector installation. A separate Cyclone feature branch raises the
bounded quota to 8,192 and preserves the existing files, validation, namespace
isolation and error envelope. No existing records are rewritten by that change.
This is evidence of capacity pressure, not proof that every historical
BAD_REQUEST or Magisk timeout had that cause.

## Verification

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :playEvidence:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :app:lintDebug :playEvidence:lintDebug :probe:lintStandardDebug :probe:lintDiagnosticsDebug --offline --no-daemon --console=plain
uv run --offline --with pytest --with jsonschema -- python -X utf8 -m pytest forge/tests forge/schema_tests -q
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
git diff --check
```

Final local gate: **2,033 passed** (app 703, module 40, Play diagnostics 20,
standard probe 109, diagnostics probe 109, forge/schema 1,045, host probe 7).
Debug assembly passed; lint has zero errors. Existing warnings: app 19 and each
probe variant 12. These include style suggestions and deliberate diagnostic
hardware/private-API reads. Cloak compilation emitted no warnings.

Cyclone candidate: **2,909 mobile tests passed**, debug assembly and metadata
guards passed. Existing Kotlin deprecation warnings remain in that repository.
The first candidate command omitted ANDROID_HOME and failed to locate the SDK;
rerunning with `ANDROID_HOME=C:/Users/Agent/android-sdk` passed. The Windows CRLF
source-guard correction was tested in the full mobile suite.

Signed CI passed for both candidates, without publishing:

- Cloak `37818061fc2b307b4dd65185bfc74d84a9ce4268`:
  https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39352373058
- Cyclone `7e001f4ee80dbc329464b04b8a730df5c4762793` on
  `codex/connector-capacity-reliability`:
  https://github.com/premiumcentraal-boop/Cyclone/actions/runs/39352440392

Existing Play readiness remains UNKNOWN where required evidence is unavailable;
this repair does not turn missing observations into passes.

## Device evidence

`docs/runtime-evidence/2026-10-20-011343-connection-reliability/` contains the
before/intermediate/final audits, signed and installed APK provenance and repair
diagnostics. Both APK updates used their established signing identities and
preserved data. Cloak remains alpha.27/code 51 as an explicitly identified test
candidate; Cyclone is the signed owner-test alpha.144.dev2/code 299. The existing
alpha.27/code 51 module was not replaced.

Normal Health repair completed publication on both users. The profile-side
repair changed local assignments according to the existing Main-wins mirror
policy: user 17 had locally selected identity
`192f47b0-2c97-5d5e-8e59-461e9dc53abb`, while Main was already publishing
`e8d45c1b-a4ff-5e8e-904f-f27d9861f0ca` for the same 327 targets. User 17 now
mirrors that Main identity. Saved identity files and effective published bodies
were preserved; local binding files are intentionally not byte-identical.
Publisher 17's temporary/redundant share and the merged index can be rewritten by
normal publication. Main's publisher share and its effective entries are checked
separately; a raw index rewrite is not reported as byte-for-byte preservation.

Android killed background Cyclone processes on user switches (exit reason
`killBackgroundUserProcessesWithAudioRecordPermission`) and during the APK
update. The resulting DeadObjectException is recorded rather than disguised as
a missing app. Verification lets each copy finish while its Android user is
active. No user, credential, permission or AppOps reset was performed.

No module hooks, publication scripts, binding schemas, permissions or profile
bodies changed. No release or module update is part of this candidate run.

## First-run cleanup follow-up

After both connector stores reached 327 ready records, Main remained busy because
lost/first-run bookkeeping scanned every bindable profile's app inventory,
including profiles with no local bindings. Each remote read is comparatively
expensive. Main eventually settled to Connected / Ready, 1,644 pass, zero warning
and zero fail; this was delayed completion, not invalid profile data.

Main now limits that discovery sweep to profiles represented by its local
bindings. Disabled local bindings still qualify, allowing their stale tuples to
be cleared. Previously tracked tuples retain the existing cleanup path. Unknown
records in unrelated profiles are preserved instead of scanned or deleted.
Three regression tests cover unrelated-profile preservation without reads,
empty-library preservation, and cleanup for disabled bindings. Existing
same-profile stale cleanup remains tested.
