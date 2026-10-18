# Alpha.25 Catalog Import Bridge release handoff

Published prerelease: https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.11.0-alpha.25

Release commit: `119df8398e224d2944a538f00a7171cf663414ad`; feature commit: `743bb60`. App/module: `0.11.0-alpha.25` / code `49`.

## Changes

Bounded offline hardware XML import, build-property attachment, field provenance and explicit conflict/completion gates feed the existing Device Factory. The complete fixture is synthetic. See [implementation files and public APIs](HANDOFF-2026-10-18-CATALOG-IMPORT-BRIDGE-V1.md) and [pipeline guide](CATALOG-IMPORT-BRIDGE-V1.md).

Release files: app build metadata, module packaging metadata, README, ARCHITECTURE, CURRENT-STATE, RELEASE_0.11.0-alpha.25.md, and release workflow asset list. Runtime, connector, binding, publication and existing profile storage/schema behavior are unchanged.

## Validation

1,767 local tests passed: app 598; module 40; each probe variant 109; forge 810; schema 94; host probe 7. Zero failures/skips. Debug/release assembly, both probe builds, module packaging, release metadata, lint/format and diff checks passed. No compiler warnings.

- Main CI: https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39168649006
- Signed validation: https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39168650095 (attempt 2 passed).
- Release CI: https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39169720576

First signed-validation attempt stalled during Android SDK setup and was cancelled; the identical workflow passed on retry. Local package-index timeouts were handled using cached dependencies for the same gates. The first public APK download had an HTTP/2 error; it was retried using HTTP/1.1.

Existing APK signer verified: `17ca3617b0cbaf250d2ef03e945b319e57695de6d977c652ded243ad5656f553`. Versions/codes match the tag. APK embeds the exact published module ZIP. Published sizes/digests match GitHub; supporting source assets match tag bytes. Module native libraries are byte-identical to alpha.24 signed CI. Signed-validation payload comparison passed.

## Published assets

| Asset | Bytes | SHA-256 |
| --- | ---: | --- |
| app-release.apk | 43789847 | `72a64e47a5218b4c581a54ea7090fa891423e29b26075e0620ea97b7be2fa536` |
| catalog-provenance.v1.schema.json | 10008 | `95c512ac9fa1faf724a4481c2102e0a0e9b2d7dd2353dfef0d35330348278445` |
| cyclone-cloak-0.11.0-alpha.25.zip | 1683428 | `a31cb246373a41f85ef02b0ccfd1fad01838d54975a811fc590255cacbde418a` |
| HANDOFF-2026-10-18-CATALOG-IMPORT-BRIDGE-V1.md | 7048 | `6fe8bcf463d33eb605c2e11a90fcdc9ade8812b3d4f33e3980d62821a1281561` |
| import-sources-v1.json | 4601 | `86c1a7cbb4e1a8db4fcc856181761b6c87d548bbab123497b3f3d1541f958c71` |
| synthetic-candidate.json | 63719 | `90587de21163d49f5f02527e365b940b259593b18d55b2c1762e97d4ba1e02a6` |

Verification receipts: [published assets](release-verification/v0.11.0-alpha.25/published-assets-verification.json), [signing and packaging](release-verification/v0.11.0-alpha.25/release-artifact-verification.json), [checksums](release-verification/v0.11.0-alpha.25/SHA256SUMS).

## Exact local commands

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :app:assembleRelease :module:packageModule :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --offline --with pytest -- python -X utf8 -m pytest forge/tests -q
uv run --offline --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests -q
uv run -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run --offline --with ruff -- ruff check forge/cloak_forge/catalog_provenance.py forge/cloak_forge/hardware_xml.py forge/tests/test_hardware_xml.py forge/schema_tests/test_catalog_import_schema.py
uv run --offline --with ruff -- ruff format --check forge/cloak_forge/catalog_provenance.py forge/cloak_forge/hardware_xml.py forge/tests/test_hardware_xml.py forge/schema_tests/test_catalog_import_schema.py
uv run --offline -- python -X utf8 tools/verify_release.py --tag v0.11.0-alpha.25 --artifacts
git diff --check
git diff --cached --check
```

Published artifacts: `gh release download v0.11.0-alpha.25`; signature: Android build-tools 35.0.0 `apksigner verify --verbose --print-certs`; versions: `aapt dump badging`. Python zipfile/hashlib verified archive payloads and SHA-256 values; receipts contain the results.

## Remaining limits

No device actions or new physical verification. No platform verdict. Approved-source page hashes do not pin hardware artifacts; real imports require artifact-specific license, model/SKU and firmware review. Only XML, build-property and review JSON adapters exist; no Android import UI or downloader. Unrelated untracked handoffs and old evidence remain untouched.
