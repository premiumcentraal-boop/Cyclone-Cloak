# RC2 publication handoff — 2026-10-12

Published [v0.10.0-rc.2](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.10.0-rc.2) as a prerelease from
`b9d8606bff6a90abbc7f993d4f6ccc553465b43e`. App and module agree on RC2/code23.
Signed APK and module ZIP are attached. No physical-device tests or reboot ran.

## Changes and result

Committed accumulated authorized development and RC2 metadata in `8288c4c` and
pushed to main. Personal local AGENTS/brief/engine-report/mission files were left
untracked. First release run `38179826813` failed one of 275 app tests because
the generation progress label contained a corrupted ellipsis. Its test was
retained. `b9d8606` repaired punctuation in ProfileGenerationCard.kt,
CloakViewModel.kt and IdentitiesScreen.kt and removed a trailing blank line in
tools/run-local-profile-tests.ps1. No schema, synchronization or runtime behavior
changed for this repair.

Replacement [release run](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38180047567) completed successfully in 4m14s:
185 Python tests; full app/module unit and Compose tests (1m43s); signed release
APK and module ZIP build (1m30s); metadata, ARM ELF and exact embedded-ZIP checks;
then both assets uploaded. Main CI `38180046646` also passed. The local
Robolectric dependency blocker remains a local limitation, not an unresolved
release-CI gate. No test was deleted or bypassed.

Documentation updated after verified publication: README, ARCHITECTURE,
COMPAT_SURFACE, CHECKPOINT-2026-10-05, CURRENT-STATE, RC2 release/upgrade notes,
and this handoff. Preparation handoff remains a historical pre-publication report.
Release metadata changed in the preparation commit; code23/RC2 was retained.

## Assets

| Asset | Size | SHA-256 from GitHub |
| --- | --- | --- |
| app-release.apk (signed) | 42,702,210 bytes | eb322e797cd2d1028c4cc2bcfc806118cb7bdfb335656da08a42bf8f16714b77 |
| cyclone-cloak-0.10.0-rc.2.zip | 1,560,998 bytes | c4a75515cbebb341cd7131cdbd5c8893fee7ede96259d70cee60a335ce6b9227 |

## Commands

```powershell
git fetch origin --tags
git rev-list --left-right --count HEAD...origin/main
git commit -m "Prepare RC2 with coherence, profile backups and readiness diagnostics"
git push origin main
gh workflow run release.yml --ref main -f tag=v0.10.0-rc.2
gh run watch 38179826813 --interval 15 --exit-status
gh run view 38179826813 --log-failed
git commit -m "Fix corrupted progress-label punctuation caught by release screen tests"
git push origin main
gh workflow run release.yml --ref main -f tag=v0.10.0-rc.2
gh run watch 38180047567 --interval 20 --exit-status
gh run view 38180047567 --json status,conclusion,url
gh run view 38180047567 --log
gh run view 38180046646 --json status,conclusion
gh release view v0.10.0-rc.2 --json url,isDraft,isPrerelease,assets
git fetch origin --tags
git rev-parse v0.10.0-rc.2
git -c core.safecrlf=false diff --check
```

Workflow's exact tests/build/checks:

```sh
uv run --with pytest -- python -m pytest forge/tests -q
gradle -p android :app:testDebugUnitTest :module:testDebugUnitTest --no-daemon
gradle -p android :app:assembleRelease :module:packageModule --no-daemon
python3 tools/verify_release.py --tag v0.10.0-rc.2 --artifacts
```

## Warnings, assumptions and follow-up

No compiler/build warnings in the successful release log. Git printed routine
LF/CRLF notices during staging. Workflow tooling warned about deprecated Node20
and setup-java v4/Node APIs, unmatched uv cache glob, and forthcoming Ubuntu
runner migration. These did not block the run.

Publication was explicitly authorized by the user. RC2 remains a prerelease;
physical-device behavior, signed upgrades, post-reboot activation, Android Toybox
publication, multi-user checks and Downloads provider behavior remain unverified.
Reboot-dependent checks stay deferred in DEFERRED-DEVICE-CHECKS.md. Historical
runtime measurements do not verify RC2 or full device compatibility.
