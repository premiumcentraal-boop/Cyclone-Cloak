# Alpha.14 cycle fix — 2026-10-16

## Cause and scope

The supplied alpha.13 evidence records a successful privilege preflight and a
roughly 30-second publication timeout. The brief's 25-second call budget was not
the only limit: an active `PrivilegedSession` ignored that argument and retained
its shared 30-second deadline. Raising only the call to 120 seconds could not fix
that path. The merge also spawned copy, removal and basename processes per app.

The Health SIGSEGV evidence supports companion self-hooking as a cause: removing
its published self-entry stopped the observed crash. That experiment is not
proof of every crash's cause or verification of this new binary.

## Changes

- `root/CloakRootDoctor.kt`: preserve the existing local 120-second publication
  mitigation, honor it in a granted batch, record available phase markers on
  timeout, classify post-root timeout as publication failure, batch directory
  copies once per publisher, and keep Main's whole-key replacement semantics.
- `root/PrivilegedSession.kt`: retain the 30-second approval preflight; anchor the
  longer granted-work deadline to the privilege grant rather than resetting it
  per command. Expired sessions cannot resume commands. Granted script waits do
  not generate approval-wait notifications. One shell remains shared by the batch.
- `module/src/main/cpp/cloak.cpp`, `runtime_scope.hpp`: reject the exact companion
  package before profile lookup and all hooks, for every Android user. Do not
  remove or rewrite stored/published self-bindings. The separate probe stays scoped.
- Root tests: granted-work deadline, expiry, no deadline reset, timeout reason,
  327-target merge with another publisher, constant copy count, whole-key override.
- `forge/tests/test_runtime_scope.py`: source integration regression guard;
  constexpr package cases additionally compile in both NDK builds.
- Matching app/module metadata: `0.11.0-alpha.14`, code **38**.
- README, ARCHITECTURE, CURRENT-STATE, STATE_LAYOUT and alpha.14 release notes.

No profile contents, identity semantics, connector contract, storage formats,
binding records/history or permissions changed. Lock inode handling, publisher
precedence and recovery protocol remain intact. Raw captured evidence and older
untracked handoffs were preserved and excluded from commits.

## Changed files

- `README.md`
- `android/app/build.gradle`
- `android/app/src/main/java/dev/cyclone/cloak/root/CloakRootDoctor.kt`
- `android/app/src/main/java/dev/cyclone/cloak/root/PrivilegedSession.kt`
- `android/app/src/test/java/dev/cyclone/cloak/root/CloakPublishMergeTest.kt`
- `android/app/src/test/java/dev/cyclone/cloak/root/CloakRootDoctorTest.kt`
- `android/app/src/test/java/dev/cyclone/cloak/root/PrivilegedSessionTest.kt`
- `android/module/module.prop`
- `android/module/src/main/cpp/cloak.cpp`
- `android/module/src/main/cpp/runtime_scope.hpp`
- `docs/ARCHITECTURE.md`
- `docs/CURRENT-STATE.md`
- `docs/RELEASE_0.11.0-alpha.14.md`
- `docs/STATE_LAYOUT.md`
- `forge/tests/test_runtime_scope.py`
- `docs/HANDOFF-2026-10-16-ALPHA14-CYCLE-FIX.md`

## Exact local commands

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug :module:packageModule --offline --no-daemon
uv run --with pytest -- python -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
uv run -- python tools/verify_release.py --tag v0.11.0-alpha.14
git diff --check
```

Final results: **522 app, 12 module, 229 forge, 33 schema tests passed**;
Android zero failures/errors/skips. Debug APK and ARM64/ARMv7 module ZIP built.
The first Android run exposed a new test's use of a strict duplicate-key JSON
parser on the existing Main-precedence index. Corrected the assertion to inspect
the chosen profile file, as the existing precedence test does, then reran all
Android targets successfully. The final Gradle run took 1m54s.

The 327-target regression retained the other publisher and asserted exactly three
copy calls: one staging copy and two publisher merges. Local WSL test timing is
not Android device timing. No compiler warnings were reported. Git emitted its
normal LF-to-CRLF working-copy normalization notices for native files.

Feature commit: `c302bc6`. Release metadata commit: `67431595fb7c09402eabb725eb792e32af6e2396`.

## Device follow-up (UNVERIFIED)

No device installs, root commands, index edits, data clearing or restarts were
performed in this release run. After normal installation of the signed matching
pair, follow the module manager's restart requirement, then verify:

1. Large user-17 batch finishes publication as COMPLETED or RECOVERED after grant.
2. Existing Main/other publisher bindings remain in the index.
3. Slow publication does not claim approval expired; genuine preflight expiry
   still aborts before binding work.
4. Health stays open with an existing companion self-binding; the native guard
   ignores it without deleting records.
5. Binding run history and exported phase evidence survive both success/failure.

A finite 120-second budget may still expire on unusually slow devices. No
platform verdict or full compatibility claim is made. Source/compile-time tests
cannot establish on-device Health stability.

## Signed build and release verification

Main CI: [38825979857](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38825979857), **success**.
Signed validation: [38825988226](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38825988226), **success**.
Release workflow: [38826563977](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38826563977), **success**.

The validation APK passed `apksigner verify --verbose --print-certs`, version/code
checks, ZIP integrity, ARM64/ARMv7 ELF class checks and exact embedded module ZIP
comparison. The certificate matches the prior signed releases:
`17ca3617b0cbaf250d2ef03e945b319e57695de6d977c652ded243ad5656f553`.
Published prerelease: [v0.11.0-alpha.14](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.11.0-alpha.14).
Tag target: `67431595fb7c09402eabb725eb792e32af6e2396`.

Downloaded published assets passed signature/certificate, version/code, ZIP CRC,
ARM64/ARMv7 ELF class and exact embedded module byte checks. Their SHA-256 and
sizes match GitHub's asset metadata. The APK permission set exactly matches
alpha.13. The launcher resolves in packaged metadata to
`dev.cyclone.cloak.MainActivity`, minimum SDK 29, target SDK 36. These are packaging
checks, not a claim of a successful phone installation.

| Asset | Bytes | SHA-256 |
| --- | ---: | --- |
| `app-release.apk` | 43,345,126 | `6428338c93c8bb43a0c35dbc06b74e74e5b4e2efa5363cb457bcffd0314f72c8` |
| `cyclone-cloak-0.11.0-alpha.14.zip` | 1,678,672 | `a0349f8103155fc3cd048853b11c3166853c9075a1ccb6a133b90f1bc9196db2` |

The validation and release archive hashes differ because they are separate
builds; the published APK embeds its own published module ZIP exactly.

Local downloaded copies and verification outputs:
`%TEMP%/cloak-alpha14-release-20261016/published/`.

CI advisories remain for deprecated Node 20 action declarations/setup-java v4,
Node punycode/url.parse dependencies, missing UV cache lockfile patterns and the
upcoming Ubuntu runner image change. No application/native compiler warnings
were observed. Workflows and release verification logic were not changed.


Signed CI uses the existing signing configuration without exposing or changing
key material:

```text
gradle -p android :app:assembleRelease :module:packageModule --no-daemon
python3 tools/verify_release.py --tag v0.11.0-alpha.14 --artifacts
```

Verification commands also include `apksigner verify --verbose --print-certs`,
`aapt dump badging`, `aapt dump permissions`, `Get-FileHash -Algorithm SHA256`,
ZIP CRC/ELF inspection, and exact embedded ZIP byte comparison. Both workflows
run the same forge/schema and Android suites before artifact verification.
