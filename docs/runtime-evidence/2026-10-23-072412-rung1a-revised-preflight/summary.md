# Rung 1a coverage-gap closure revised

Result: **STOPPED — REQUIRED_FIXTURE_C_ABSENT**. No physical capture attempted. No failure was papered over by substituting fixture B or constructing expected facts from the host.

## Artifact and build preflight

- Current main: d4f0a5cb5f4d70bacaeb0b8ecdff184eff154150 plus existing working-tree prerequisites, preserved unchanged.
- Candidate ZIP verified: 8fd58b4ad0ea60ec06460b0ecaa0b9cea8e56467437975103d04392958a68795, 2022032 bytes, matching the supplied digest.
- Every packaged immutable module file matches its installed file. Installed ARM64: d7dd105446a229f981e5cdd1aed862a13e3c7d04220f04fb08eadde175bb15c1. File agreement alone is not in-process attestation. No module install/reboot performed in this run.
- Users 0 and 17 both RUNNING_UNLOCKED.
- Diagnostics assembleDiagnosticsDebug **BUILD SUCCESSFUL** (15 seconds, 38 tasks: 4 executed/34 up-to-date). APK SHA-256: 6c9fc8f36045a760c53f1711e8cdaff4777bf52766f930b31eafa25d4abab2b5.
- New `envelope.webview.instanceUa` reader exists in working-tree source and the rebuilt APK was produced from that tree. No physical result is claimed.

## Exact prerequisite discrepancy

The handoff states schema-0.4 fixture C is already prepared. No `profile-c.json` exists anywhere below this checkout, and rebuilt APK assets contain only:

| Asset | Schema | Reality | SHA-256 |
| --- | --- | --- | --- |
| assets/profile-b.json | 0.3 | False | db4f661468d72dfd9f766c6a2b761a2fb9cd9141bc1ff61124a05d93c8b1a631 |
| assets/profile-a.json | 0.3 | False | da878a8a5c51ef62324fd977d85c06fd830fa9c3896a1060621dbfc4e02efa98 |

`RealityRuntimeTable.kt` and `catalog/reality-runtime-fields.json` describe canonical paths/surfaces/status, not an expectation-bearing profile. They cannot substitute for a validated fixture. The standalone runner also selects A for user0/B for17; the probe recognizes A/B/external labels, not C as an asset label. Existing external-profile transport may be usable with a supplied validated C body, but no such body was available. No code changes or fixture fabrication were authorized.

## Measurement, audit and cleanup

All **17 PARTIAL paths remain PARTIAL**. New captures: 0. New matched expectations: 0. Promotions: 0. SIM/operator determinism: NOT_RUN, not a permanent block. No UI permission request or grant, diagnostics installation/uninstallation, user switch, temporary publication or profile file was performed. Diagnostics package paths are absent for both users.

Persisted before/after snapshots match exactly: production APK SHA-256, companion permission grants/stable AppOps, accessible companion binding/index files, published bodies and shares. Device commands and exit codes retained in commands.jsonl; snapshots, module per-file digests, fixture inventory and build log retained. No production source change, tag, release, commit or push. Existing unrelated dirty files and prior evidence preserved. This is no platform verdict or coverage claim.

## Exact local command

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
```

No test suites were run: no code was changed; the requested measurement stopped at its missing-fixture prerequisite.

## Open questions

- Supply the exact validated schema-0.4 fixture C body and its SHA-256/provenance. Where was it prepared? Neither the source asset nor a generated fixture is present.
- Confirm C's expected report label and the existing external-profile runner invocation. The current standalone runner still uses B for17; it must not silently relabel or substitute B.
- Fixture A is also schema0.3 without Reality; rows lacking expectations on A must remain observations, not treated as measured PARTIAL closure.
- Explicit grant flow must use only manifest-declared UI permissions. READ_PHONE_STATE is not declared; do not infer a permanent SIM block or fabricate an expectation from permission availability.
