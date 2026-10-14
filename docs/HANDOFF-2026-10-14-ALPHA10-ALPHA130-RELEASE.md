# Companion presence release and bootstrap acknowledgement repair

## Releases and commits

- Cloak: [v0.11.0-alpha.10](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.11.0-alpha.10), app/module code 34, commit `c803760a4394ea42259477710522bfe2f35e74f7`.
- Cyclone: [Alpha 130](https://github.com/premiumcentraal-boop/Cyclone/releases/tag/v5.0.0-alpha.130.dev1) (`v5.0.0-alpha.130.dev1`), code 284, released commit `9058cd1ec055b7a03db316272952713d7c46cffb`, branch `codex/profile-inventory-alpha128`. Provisioning was committed separately as `8992e0b7`.

Cloak's [normal CI](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38586945674)
and [release CI](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38586966429)
passed. Published assets:

| Asset | Bytes | SHA-256 |
|---|---:|---|
| `app-release.apk` | 43,195,534 | `229599b8e30efe9b5fe9978e5bb855777b35239238e939752f37d93439167952` |
| `cyclone-cloak-0.11.0-alpha.10.zip` | 1,677,628 | `6bccfeb7b331acf12662fef1be8c848aff8959621ce07c0cba577a63f825735c` |

The downloaded APK passed `apksigner verify`, reports versionName alpha.10 /
versionCode 34, and embeds the exact released module ZIP. The module's properties
also report alpha.10/code 34. Signer SHA-256:
`17ca3617b0cbaf250d2ef03e945b319e57695de6d977c652ded243ad5656f553`.

Cyclone's [Mobile CI](https://github.com/premiumcentraal-boop/Cyclone/actions/runs/38587874815)
and [standard publish workflow](https://github.com/premiumcentraal-boop/Cyclone/actions/runs/38587875877)
both **passed** for the exact commit above. The public release is a prerelease,
not a draft. Its downloaded `release-manifest.json` confirms the source SHA and
Mobile CI run ID above, with signing state `ROTATED_RELEASE_KEY_WITH_LINEAGE`.
The unchanged signer/lineage gate passed before publishing.

Cyclone APK: `Cyclone-5.0.0-alpha.130.dev1.apk`, **59,727,160 bytes**,
SHA-256 `c71fb884302fbd6d77d67fd57ca342294734aa97132c1b2031e82fa910eb25ca`.
The standard release also includes Windows/Glass packages, the connector kit,
checksums, source provenance and signing/update-compatibility records.

## Acknowledgement cause and repair

The source-confirmed collection defects were a failure response containing only
`{"ok":false}`, a collector accepting only matching success, direct non-atomic
file writes, and an original six-second polling sleep budget. Negative replies,
partial JSON, missing files and transient read failures all became the same
generic “couldn't verify” timeout. Unit tests reproduce a receiver taking seven
seconds while already unlocked, and show the repaired retry accepts its matching
reply without changing the install/enable/start sequence.

The receiver now commits a complete reply atomically and retains user/nonce on
failure once the transfer is read. Optional fixed stage/reasonCode metadata
explains failures. Collection retries within a 30-second/150-attempt budget,
rejects wrong-user/stale/late replies, and surfaces a matching failure immediately.
The normal retry uses a fresh nonce. Safe collection summaries contain only
`ok`, `user`, `stage`, `reasonCode`. Original successful three-field replies remain
compatible. No connector wire protocol, binding, reboot, reset or clear-data
behavior is changed. The exact earlier phone-side failure stage remains
unverified without a device.

See the Cyclone worktree's `docs/HANDOFF_BOOTSTRAP_ACK_2026-10-14.md` for the full
source analysis. The companion-only file list is in
[the preceding handoff](HANDOFF-2026-10-14-COMPANION-PRESENCE.md).

## Changed files

Cloak: companion presence model/probe/card, `CloakViewModel.kt`, `HealthScreen.kt`,
three test files and their handoff; app/module metadata, README, architecture,
current-state and alpha.10 release notes. This report records release results.
Both older untracked handoffs remain unstaged and unchanged.

Cyclone: `ProfileCornerstones.kt` and its provisioning test; new
`ProfileBootstrapAcknowledgement.kt`, `ProfileBootstrapRuntime.kt`,
`ProfileBootstrapService.kt` and the acknowledgement test; app/release metadata,
alpha.130 notes and bootstrap handoff. Existing Mobile CI/V5 publish triggers
now include the selected branch. Exact-commit, artifact, signer/lineage and
environment approval checks were preserved.

## Tests and exact commands

| Suite | Result |
|---|---|
| Cloak app | 444 passed, zero failures/errors/skips |
| Cloak module | 12 passed, zero failures/errors/skips |
| Forge | 227 passed |
| Schema | 33 passed |
| Cloak debug build | Passed, no compiler warnings |
| Cyclone provisioning baseline | 2,640 passed |
| Cyclone final Android | 2,652 passed, zero failures/errors/skips; 12 acknowledgement tests |
| Cyclone selected guards | 40 passed |
| Both whitespace checks; release/product metadata checks | Passed |

Cloak, PowerShell:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android --offline --no-daemon :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug
uv run --with pytest -- python -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
uv run -- python tools/verify_release.py --tag v0.11.0-alpha.10
git diff --check
```

Cyclone worktree:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p apps/mobile --no-daemon :app:testDebugUnitTest
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p apps/mobile --offline --no-daemon :app:testDebugUnitTest
uv run -- python scripts/ci/release_versions.py --check
uv run -- python scripts/ci/mobile_product_guard.py
uv run --with pytest -- python -m pytest scripts/ci/tests/test_mobile_workflows.py scripts/ci/tests/test_release_versions.py scripts/ci/tests/test_mobile_product_guard.py scripts/ci/tests/test_profile_lifecycle_guard.py scripts/ci/tests/test_profile_room_guard.py scripts/ci/tests/test_connector_guard.py -q
git diff --check
```

Local logs: `$env:TEMP/cloak-alpha10-checks.log`,
`$env:TEMP/cloak-alpha10-final.log`,
`$env:TEMP/cyclone-provisioning-release-checks.log`,
`$env:TEMP/cyclone-alpha130-final.log`. The first acknowledgement-test draft failed
compilation because Cyclone has no Robolectric dependency. The final tests use
pure JVM file cycles and Java atomic moves, without adding dependencies. Existing
unrelated Cyclone deprecations remain; none was reported in the changed
acknowledgement/provisioning code.

## Device result

`C:/Users/Agent/android-sdk/platform-tools/adb.exe devices` repeatedly returned
an empty list. No installation, profile switch/unlock, reboot, data clear or
binding change was performed. No diagnostics export exists and no bundle was
fabricated or attached. Device verification remains **UNVERIFIED**.
The semantic phone-status tool also reported `DEVICE_DISCONNECTED` from the
Cyclone gateway, confirming that UI verification was unavailable.

When the phone is available, update both apps normally, open/unlock a profile
through Cyclone's UI, confirm receiving-profile verification, then open that
profile's Cloak → Health → Reconnect → Check companion copies. Its own row can
verify private-directory write access. Export the redacted diagnostics bundle
and attach the real evidence to the release notes. These checks are local
evidence, not a platform verdict.
