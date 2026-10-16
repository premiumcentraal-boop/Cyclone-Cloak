# Alpha.16 diagnostics-quality release — 2026-10-17

Release: v0.11.0-alpha.16, app/module code 40. Diagnostics-only: production
runtime, connector, binding, publication, storage and module behavior unchanged.

## Changed files

Probe build/flavor manifest, DiagnosticsProbeActivity, ProbeActivity,
AdvertisingIdRead, ProbeReportPolicy and their Java/host tests; tools/profile_check.py
and forge/tests/test_profile_check.py. Original redacted evidence and a separate
PROVENANCE-NOTE.md are checked in. Release metadata, README, ARCHITECTURE,
CURRENT-STATE, release notes and the two signed workflows describe/gate this release.
Unrelated pre-existing handoffs and evidence remain unstaged.

## Exact local checks

PowerShell environment: JAVA_HOME=C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot;
CLOAK_TEST_WSL=1. Gradle executable: $env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat.

```powershell
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :app:assembleRelease :module:packageModule :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --with pytest -- python -X utf8 -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests -q
uv run -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run -- python -X utf8 tools/verify_release.py --tag v0.11.0-alpha.16 --artifacts
git diff --check
git diff --cached --check
```

PASS: 522 app, 12 module, 15 probe tests per flavor (30), 293 forge, 33 schema,
7 probe host tests: 897 executions, zero failures/skips. Debug/release and both
probe APK builds passed; module packaging/ELF/embedded-ZIP/version checks passed.
Local release APK is unsigned; protected CI supplies the existing signing key.
No compiler warnings in the captured Gradle log; Git emitted LF/CRLF notices.

## Evidence scope and gaps

Original evidence bundle SHA-256:
d80f3be6c014e611a266bd44f3a96751f86ad2295f3f83928ad356649a52138b.
Three alpha.15/code39 device captures: each 89 rows, 77 PASS, 12 OBSERVED/not
covered, zero warnings/FAIL/BLOCKED/MISSING. Fixture labels a/a/b match hashes.
327 production entries and audited files preserved. No new device work in this
release run. The unscoped manifest permission map is not a per-user grant record;
read PROVENANCE-NOTE.md. Alpha.16 physical behavior, denial paths, optional
expectations, multi-user transitions and arbitrary apps remain unverified here.
No platform verdict is claimed.

## Publication

Published prerelease: https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.11.0-alpha.16

- Feature commit: 72a5855.
- Release/tag commit: 4843a3a51f551c1b4f6df5552f60a60039aecc02.
- Main CI: https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38981871576 — success.
- Signed validation: https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38981876645 — success before tagging.
- Release CI: https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38982430553 — success.

Downloaded published assets (all SHA-256 and sizes match GitHub records):

| Asset | Bytes | SHA-256 |
| --- | ---: | --- |
| app-release.apk | 43345122 | 27dd71933fe01211f1021841668237eb8bf51da4df0d9d7d21a1c89a04f41a82 |
| cyclone-cloak-0.11.0-alpha.16.zip | 1678671 | 1ab8551f5941c7fc3fb9692cb1d2592e19e01b56bdc9a710844407f0f36d0f88 |
| evidence-bundle.zip | 19530 | d80f3be6c014e611a266bd44f3a96751f86ad2295f3f83928ad356649a52138b |
| PROVENANCE-NOTE.md | 1387 | c38d34cde08f0c0a4f7bd97e8bf9399a02e403beeaeee15024771e3d37bc37fe |

Published APK: apksigner verify PASS (v2), one existing RSA signer, certificate
SHA-256 17ca3617b0cbaf250d2ef03e945b319e57695de6d977c652ded243ad5656f553.
aapt confirms dev.cyclone.cloak / 0.11.0-alpha.16 / 40; module.prop agrees.
APK embedded ZIP matches published module byte-for-byte; both ELF ABIs verified.
Evidence ZIP and provenance note match checked-in files byte-for-byte.
Signed-validation and release builds have different ZIP/APK hashes due to separate
builds; both independently passed packaging/signature/version checks.

Additional exact commands (paths relative to repository unless absolute):

```powershell
git push origin main
gh workflow run signed-validation.yml --ref main -f tag=v0.11.0-alpha.16
gh run download 38981876645 --name signed-validation-4843a3a51f551c1b4f6df5552f60a60039aecc02 --dir "$env:TEMP/cloak-alpha16-signed-validation"
git tag -a v0.11.0-alpha.16 4843a3a51f551c1b4f6df5552f60a60039aecc02 -m "Cyclone Cloak v0.11.0-alpha.16 diagnostics quality"
git push origin v0.11.0-alpha.16
gh release download v0.11.0-alpha.16 --dir "$env:TEMP/cloak-alpha16-published"
# gh downloaded the smaller assets but stalled on the APK; cancelled, then:
curl.exe --fail --location --max-time 120 --retry 1 --silent --show-error --output "$env:TEMP/cloak-alpha16-published/app-release.apk" "https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/download/v0.11.0-alpha.16/app-release.apk?verify=alpha16-20261017"
gh release view v0.11.0-alpha.16 --json url,isDraft,isPrerelease,tagName,assets
& 'C:/Users/Agent/android-sdk/build-tools/35.0.0/apksigner.bat' verify --verbose --print-certs "$env:TEMP/cloak-alpha16-published/app-release.apk"
& 'C:/Users/Agent/android-sdk/build-tools/35.0.0/aapt.exe' dump badging "$env:TEMP/cloak-alpha16-published/app-release.apk"
gh run view 38982430553 --log
```

The same apksigner/aapt checks ran against the signed-validation APK before tagging.
Python stdlib ZIP/hash assertions checked every asset digest/size, module properties,
ELF headers, embedded ZIP, original evidence/provenance equality and prerelease state.

Warnings: no compiler warnings in local or release build output. Existing CI actions
emit setup-java v4, Node 20 and punycode deprecation warnings. Dependency/action upgrades
are left for a separate maintenance run. No device actions performed in this release run.
