# Alpha.12 release handoff — 2026-10-15

Published prerelease: [v0.11.0-alpha.12](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.11.0-alpha.12). App/module version `0.11.0-alpha.12`, code **36**.

## Commits and changed files

- `6c6b2bc`: Sprint A, connector lifecycle, retry coordinator, registry cache, handshake messages, connection chip and startup/service integration; three test files and its handoff. Shared manifest/view-model changes were separated from Sprint B in the index.
- `e57bf77`: Sprint B, bounded binding run journal, diagnostic capture, result assessment, bundle assembly/share provider, Health Runs UI and operation instrumentation; five test files and its handoff.
- `5d0c3aaf512f3c2a76a3462dd5ce20bb6d4834f1`: release commit/tag target; app build metadata, module packaging metadata, README, architecture/current-state headers and `docs/RELEASE_0.11.0-alpha.12.md`.
- This handoff records the completed release after the tag. The two older untracked COMPANION-UI-REDESIGN and CYCLONE-PROFILE-APPS handoffs remain unchanged and unstaged.

Full Sprint A/B file lists are in their respective handoffs. No connector protocol, binding logic, publication scripts, existing profile/binding schemas, runtime behavior or permissions changed. The cache and journal are separate local diagnostic records. No platform verdict is claimed.

## Checks and exact commands

From the repository in PowerShell:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug --rerun-tasks --offline --no-daemon
uv run --with pytest -- python -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
git diff --check
```

All passed: **497 app, 12 module, 227 forge, 33 schema tests**; no skipped local Android tests. Debug build succeeded. The Android command was repeated after versioning without `--rerun-tasks`; it also passed. No compiler warnings; Git emitted line-ending normalization notices.

```powershell
uv run -- python tools/verify_release.py --tag v0.11.0-alpha.12
git push origin main
gh workflow run signed-validation.yml --ref main -f tag=v0.11.0-alpha.12
gh run watch 38646934690 --exit-status --interval 20
gh run download 38646934690 --name signed-validation-5d0c3aaf512f3c2a76a3462dd5ce20bb6d4834f1 --dir "$env:TEMP/cloak-alpha12-release-20261015/signed-validation"
git tag -a v0.11.0-alpha.12 5d0c3aaf512f3c2a76a3462dd5ce20bb6d4834f1 -m 'Cyclone Cloak v0.11.0-alpha.12'
git push origin v0.11.0-alpha.12
gh run watch 38647488951 --exit-status --interval 20
gh release download v0.11.0-alpha.12 --dir "$env:TEMP/cloak-alpha12-release-20261015/published"
& 'C:/Users/Agent/android-sdk/build-tools/35.0.0/apksigner.bat' verify --verbose --print-certs "$env:TEMP/cloak-alpha12-release-20261015/published/app-release.apk"
```

Main CI [38646934456](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38646934456), signed validation [38646934690](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38646934690), and release CI [38647488951](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38647488951) all passed on the tag target. The signed gate passed before the tag was pushed. Release CI reran all suites, then ran:

```text
gradle -p android :app:assembleRelease :module:packageModule --no-daemon
python3 tools/verify_release.py --tag v0.11.0-alpha.12 --artifacts
```

CI annotations concern existing Actions/Node deprecations, cache dependency patterns and the upcoming Ubuntu runner migration; they did not fail the build.

## Published asset verification

| Asset | Bytes | SHA-256 |
|---|---:|---|
| app-release.apk | 43,327,326 | `e6050fa0dc14d268d8244893c96dd941a08b1ca93f1ffe86edadd70289963faf` |
| cyclone-cloak-0.11.0-alpha.12.zip | 1,677,629 | `49fdcb480022a702c0cfde5288e3a501d11b71614b1e386953813306a9c1abeb` |

Downloaded assets match GitHub's SHA-256 and byte counts. APK signature verification passed (v2); signer certificate SHA-256 is `17ca3617b0cbaf250d2ef03e945b319e57695de6d977c652ded243ad5656f553`, matching alpha.11 for updates. Actual APK package/version/code and module ZIP metadata agree. APK `assets/cyclone-cloak-module.zip` exactly matches the published module ZIP. ZIP CRC and ARM64/ARMv7 ELF checks passed. Merged APK permissions match alpha.11.

The temporary verification helper and JSON results are under `%TEMP%/cloak-alpha12-release-20261015/`; it checks metadata, embedded ZIP bytes, ELF headers, CRC, sizes and published GitHub digests. The earlier signed-validation build has different archive hashes because it is a separate build; the table above is exclusively the downloaded published release.

## Pending device verification

No device work performed. Verify separately:

1. Update-restart reconnects automatically and cached registry/Last seen displays during live verification.
2. Connection chip states and actionable error details work on the phone.
3. Per-run and 24-hour export opens the standard share chooser and saves a readable ZIP.
4. Real-device bundle contents have the expected time window, run records, redacted binding projection, observed module-state hash, handshake and versions, without profile bodies or secrets.

Post-boot reconnect depends on Cyclone waking Cloak or the next app launch/resume; no boot permission was added. The exported module-state hash represents the last observed diagnostic state, not the installed binary or a fresh root-only publication-tree read.
