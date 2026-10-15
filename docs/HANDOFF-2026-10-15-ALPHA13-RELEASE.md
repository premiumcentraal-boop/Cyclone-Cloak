# Alpha.13 release handoff — 2026-10-15

Published prerelease: [v0.11.0-alpha.13](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.11.0-alpha.13).
App/module version `0.11.0-alpha.13`, code **37**.

## Commits and scope

- `3fdff98`: reconnect resilience and recoverable binding-run evidence. Full changed-file list and behavior details: [implementation handoff](HANDOFF-2026-10-15-CONNECTOR-RESILIENCE-IMPLEMENTED.md).
- `61cf2ca73b0502b52e325cfbe86a0486c1f475e4`: release/tag target. Updates app/module packaging metadata, README, architecture/current-state status and release notes.
- This handoff records the completed release after the tag. It supersedes the implementation handoff's historical statement that changes were local and uncommitted.

No connector protocol, profile/binding schema, storage layout, publication script,
runtime/module behavior or permission changes. The journal gains optional local
diagnostic fields in its existing envelope. Unrelated untracked handoffs and user
17 evidence remain unchanged and unstaged. No platform verdict is claimed.

## Checks and exact commands

From the repository in PowerShell:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug --offline --no-daemon
uv run --with pytest -- python -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
uv run -- python tools/verify_release.py --tag v0.11.0-alpha.13
git diff --check
```

Passed: **515 app, 12 module, 227 forge, 33 schema tests**, debug build and
whitespace/metadata checks. The preceding implementation run forced all 96
Gradle tasks with `--rerun-tasks`; Android results had zero failures/skips with
WSL publication coverage enabled. Checks passed again after versioning. No local
compiler warnings. Git emitted a line-ending normalization notice.

```powershell
git push origin main
gh workflow run signed-validation.yml --ref main -f tag=v0.11.0-alpha.13
gh run watch 38730831725 --exit-status --interval 20
gh run watch 38730831780 --exit-status --interval 20
gh run download 38730831725 --name signed-validation-61cf2ca73b0502b52e325cfbe86a0486c1f475e4 --dir "$env:TEMP/cloak-alpha13-release-20261015/signed-validation"
git tag -a v0.11.0-alpha.13 61cf2ca73b0502b52e325cfbe86a0486c1f475e4 -m 'Cyclone Cloak v0.11.0-alpha.13'
git push origin v0.11.0-alpha.13
gh run watch 38731522284 --exit-status --interval 20
gh release download v0.11.0-alpha.13 --dir "$env:TEMP/cloak-alpha13-release-20261015/published"
gh api repos/premiumcentraal-boop/Cyclone-Cloak/releases/tags/v0.11.0-alpha.13
```

[Main CI](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38730831780),
[signed validation](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38730831725),
and [release CI](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38731522284)
all passed on `61cf2ca`. Signed validation and signature checks completed before
tag creation. Release CI reran all suites, then built and verified:

```text
gradle -p android :app:assembleRelease :module:packageModule --no-daemon
python3 tools/verify_release.py --tag v0.11.0-alpha.13 --artifacts
```

CI annotations concern existing Actions/Node deprecations, missing cache lockfile
patterns and the upcoming Ubuntu runner migration. They did not fail the gates.

## Published artifacts

| Asset | Bytes | SHA-256 |
| --- | ---: | --- |
| app-release.apk | 43,343,710 | `185ff33855fbe59d7b5266689d101107b7714c6c5ddc68ee02a682e836886858` |
| cyclone-cloak-0.11.0-alpha.13.zip | 1,677,629 | `527ae5bf696ad716f1af6d5aa6f54da79b819a3c88f785eceacb153a2dfe5c19` |

Downloaded bytes and SHA-256 match GitHub's published asset metadata. APK
signature verification passed; signer SHA-256 is
`17ca3617b0cbaf250d2ef03e945b319e57695de6d977c652ded243ad5656f553`,
matching the actual published alpha.12 APK for normal updates. Actual APK
package/version/code and ZIP module metadata agree. Embedded
`assets/cyclone-cloak-module.zip` exactly matches the published module ZIP. ZIP
CRC and ARM64/ARMv7 ELF header checks passed. APK permissions match alpha.12.

Verification commands, with `$taskPublished` set to
`"$env:TEMP/cloak-alpha13-release-20261015/published"`:

```powershell
& 'C:/Users/Agent/android-sdk/build-tools/35.0.0/apksigner.bat' verify --verbose --print-certs "$taskPublished/app-release.apk"
& 'C:/Users/Agent/android-sdk/build-tools/35.0.0/aapt.exe' dump badging "$taskPublished/app-release.apk"
& 'C:/Users/Agent/android-sdk/build-tools/35.0.0/aapt.exe' dump permissions "$taskPublished/app-release.apk"
uv run -- python "$env:TEMP/cloak-alpha13-release-20261015/verify_assets.py" $taskPublished --github "$env:TEMP/cloak-alpha13-release-20261015/github-release.json"
```

The helper, downloaded artifacts and JSON verification reports are under
`%TEMP%/cloak-alpha13-release-20261015/`. The separate signed-validation build
has different archive hashes; the table above describes published assets only.

## Pending device verification

No device actions were performed. Verify separately:

1. Update-restart reconnect latency, cached display and actionable status.
2. Approval expiry and retry without recreating unchanged saved bindings.
3. Active/interrupted run history and retained selections in user 17.
4. Share chooser and redacted seven-file per-run/24-hour ZIP contents.

Root-free reconnect does not refresh root-backed Main-binding mirrors; explicit
repair/binding retains that path. Earlier device evidence does not verify this
artifact. No phone installation success or platform compatibility verdict is
claimed by the signing/packaging checks.
