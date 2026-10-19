# Alpha.26 Play Surface Contract release handoff

Published prerelease: https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.11.0-alpha.26

Release commit: `ca285b8c98bc5fc83a00229006f16c950c91a680`; feature commit: `7d2c6a26fedae39e79e6bcb56a8acb64c1a46e4d`. App/module `0.11.0-alpha.26` / code `50`.

## Changes

Play Surface Contract v1 adds an optional profile section, shared Kotlin/Python schemas and validation, explicit account-slot/package conflicts, and separate hash-bound local readiness records. Missing or mismatched evidence remains UNKNOWN. Existing profiles remain compatible without migration. This is a contract foundation, not account setup, service enforcement or a platform verdict.

Implementation files, public APIs and limitations: [implementation handoff](HANDOFF-2026-10-19-PLAY-SURFACE-CONTRACT-V1.md) and [contract guide](PLAY-SURFACE-CONTRACT-V1.md). Release changes: app/module packaging metadata, README, architecture/current-state headers, release notes and workflow attachment paths only. Runtime, connector, publication, binding storage and permissions are unchanged.

## Verification

1,880 local tests passed, zero failures/errors/skips: app 608; module 40; each probe flavor 109; forge 876; schema 131; host probe 7. Debug/release assembly, probe builds and module packaging passed. Lint/format, generated-rule, release metadata and diff checks passed. No compiler warnings. Local release output is unsigned; existing CI secrets sign the validated public build.

- Main CI: https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39232277001
- Signed validation: https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39232287345
- Release CI: https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39233055932

Existing release certificate SHA-256: `17ca3617b0cbaf250d2ef03e945b319e57695de6d977c652ded243ad5656f553`. Signature verification passed. Versions/codes and tag match; APK embeds the exact published module ZIP. Published sizes/SHA-256 match GitHub metadata. Supporting assets match the tag bytes. Signed-validation and release payload comparison passed. Module native libraries match the alpha.25 signed artifact.

## Assets

| Asset | Bytes | SHA-256 |
| --- | ---: | --- |
| app-release.apk | 43806231 | `c27c7a3c1a95617f323f8b9539dc2c56e24dab907db39cc6ab1e380ec7848707` |
| cyclone-cloak-0.11.0-alpha.26.zip | 1683427 | `a2e932b9bf0bbefa6022d590fc68f6606ebdf3cd36b41d63d6c13ebf0e38382f` |
| HANDOFF-2026-10-19-PLAY-SURFACE-CONTRACT-V1.md | 5245 | `6a653e2cbfa52a43e4c8772f417163f0fb8b1db05ee0c886f12fef577d21c8d0` |
| play-readiness.schema.json | 272 | `d134d2336b91921efe3fa204f7bc1bfe21bbd0a6289d6066bdb529764990935a` |
| PLAY-SURFACE-CONTRACT-V1.md | 6562 | `8f351e6736c1862d9ef9d9b805764f48d564a07dd29d83d99c152498d4446c52` |
| play-surface.schema.json | 11005 | `9474c58d6d922c349f38f9b567b42136c013fcaeac95675521762485100c588d` |

Receipts: [public asset checks](release-verification/v0.11.0-alpha.26/published-assets-verification.json), [signing and packaging](release-verification/v0.11.0-alpha.26/release-artifact-verification.json), [checksums](release-verification/v0.11.0-alpha.26/SHA256SUMS).

## Exact local commands

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :app:assembleRelease :module:packageModule :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --offline --with pytest -- python -X utf8 -m pytest forge/tests -q
uv run --offline --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests -q
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run --offline -- python -X utf8 tools/verify_release.py --tag v0.11.0-alpha.26 --artifacts
git diff --check
git diff --cached --check
uv run --offline -- python tools/generate_play_surface.py --check
uv run --offline --with ruff -- ruff check forge/cloak_forge/play_surface.py forge/tests/test_play_surface.py forge/schema_tests/test_play_surface_schema.py tools/generate_play_surface.py
uv run --offline --with ruff -- ruff format --check forge/cloak_forge/play_surface.py forge/tests/test_play_surface.py forge/schema_tests/test_play_surface_schema.py tools/generate_play_surface.py
```

Published assets were downloaded from the tag release; Android build-tools 35.0.0 `apksigner verify --verbose --print-certs` verified signing and `aapt dump badging` checked app versions. Python zipfile/hashlib checked embedded module, source attachments, archive payloads and digests. These checks are recorded in the receipts.

## Limitations

No device actions or new physical verification. No platform verdict. No account provisioning, service isolation, observation acquisition, persistence or UI is added. Hash binding compares supplied exact-byte digests; provider authentication and report-signature verification remain future work. Existing physical evidence remains scoped to the measured artifact. Unrelated handoffs and old evidence were not staged or changed.
