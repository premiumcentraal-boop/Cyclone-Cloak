# Alpha.27 published artifact verification — 2026-10-19

Release: [v0.11.0-alpha.27](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.11.0-alpha.27),
marked prerelease, not draft. Annotated tag resolves to
`79c2f86dba420cf46233a48d92889f6d6e8babfb`.

- [Normal CI](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39254225924): passed.
- [Signed validation](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39254226690): passed before tagging.
- [Release CI](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39255232998): passed; signed build and artifact verifier succeeded.

The first validation attempt stopped because the Forge CI environment omitted
`jsonschema`, required by the new evidence-harness tests. Commit `79c2f86` adds
that dependency to normal CI, signed validation and release CI. No product code
was changed by that correction. Both final release workflows run the new
diagnostics tests/lint and build its separately signed APK.

## Downloaded release assets

| Asset | Bytes | SHA-256 |
| --- | ---: | --- |
| `app-release.apk` | 43,986,455 | `2fd83e2cbaf031ea563990e8a6e54154067351f51f074d25d9f4592c013d282e` |
| `cyclone-cloak-0.11.0-alpha.27.zip` | 1,683,427 | `fc10038d69a1c4d23e7bc1ee1c9f3b47fc3a5c497c58804814840bd21a0a2b59` |
| `playEvidence-release.apk` | 3,437,173 | `5e58b57e7d394ecccb4d5bb589df3e8130022df5b5abd10537dabbfcd0c08868` |

All **11** downloaded assets match GitHub's reported size and SHA-256. The eight
attached schema/documentation files match exact Git blobs at the release commit
(comparison avoids Windows checkout line-ending conversion).

Both APKs pass `apksigner verify --print-certs`. Their signing certificate SHA-256
is `17ca3617b0cbaf250d2ef03e945b319e57695de6d977c652ded243ad5656f553`,
identical to the downloaded alpha.26 companion certificate. No signer was added.

Actual companion manifest: `dev.cyclone.cloak`, version `0.11.0-alpha.27`, code
**51**. Module `module.prop`: `v0.11.0-alpha.27`, code **51**. Both expected ARM
ELF payloads and ZIP CRCs validate. The companion's embedded
`assets/cyclone-cloak-module.zip` is byte-identical to the published module ZIP.

Optional diagnostics manifest: `dev.cyclone.cloak.playevidence`, version
`1.0-diagnostics`, code **1**. It is a separate package, not the production APK.
Permission dump contains only its package declaration: **zero permissions**.
Production permission declarations match the downloaded alpha.26 APK exactly.

The older local `aapt dump badging` reader could not resolve the diagnostics
APK's framework icon; its package/version were independently read using
`apkanalyzer apk summary` and `aapt2 dump xmltree`. Signature, CRC and permission
checks passed. Physical installation/launcher behavior is still unverified.

## Commands and provenance

```powershell
gh workflow run signed-validation.yml --ref main -f tag=v0.11.0-alpha.27
gh run download 39254226690 -n signed-validation-79c2f86dba420cf46233a48d92889f6d6e8babfb -D <candidate-directory>
git tag -a v0.11.0-alpha.27 79c2f86dba420cf46233a48d92889f6d6e8babfb -m "Cyclone Cloak v0.11.0-alpha.27 local Play readiness"
git push origin v0.11.0-alpha.27
gh release download v0.11.0-alpha.27 -D <published-directory>
gh api repos/premiumcentraal-boop/Cyclone-Cloak/releases/tags/v0.11.0-alpha.27
```

Verification uses SDK `apksigner`, `aapt dump permissions`, `aapt2 dump xmltree`,
`apkanalyzer apk summary`, SHA-256 and ZIP entry comparisons. Candidate and
published artifacts are checked independently; separate signed builds may have
different ZIP timestamps. The **published** digests above are the download reference.

Local metadata gate:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:assembleDebug :app:assembleRelease :module:packageModule :playEvidence:testDebugUnitTest :playEvidence:assembleRelease :playEvidence:lintDebug --offline --no-daemon --console=plain
uv run --offline -- python -X utf8 tools/verify_release.py --tag v0.11.0-alpha.27 --artifacts
```

The existing release key remains in CI only. Local release builds are unsigned;
the published APKs come from the successful signed release workflow.

## Test scope and accepted limits

Local baseline: **2,015 tests passed** (app 692, module 40, Play evidence 20,
standard and diagnostics probes 109 each, forge 907, schema 131, host probe 7).
Signed and release CI reran all test targets, the new diagnostics lint, signed
builds and the existing release artifact verifier successfully.

The full legacy probe lint gate remains **not green**: 9 errors / 12 warnings per
flavor, as disclosed in the release notes. This was not suppressed or relabelled
as passing. The owner explicitly approved release with those findings and absent
device evidence, conditional on signed CI passing.

No device was attached. No app/module installation, permission change, account
action, production binding mutation or new physical evidence occurred. Real
Play/Google capture, production import UI and service observations remain
**UNVERIFIED**. Play Protect and actual Google settings access are not measured;
Integrity token receipt is not a decoded verdict. No platform certification,
broader compatibility or permanent trust is claimed.
