# Rung 1a coverage-gap closure with fixture C

Result: **COMPLETE_WITH_LIMITATIONS**. One user0/A smoke capture and two cold user17/C captures completed with request/PID-bound STARTED and COMPLETED markers, 89 unique legacy rows and valid filesystem schemas. No source edits, module action, reboot, production app launch/install, permission/AppOps shell writes, commit, push, tag or release.

## Provenance

Current main d4f0a5cb5f4d70bacaeb0b8ecdff184eff154150 plus preserved working-tree prerequisites. Candidate ZIP SHA-256 8fd58b4ad0ea60ec06460b0ecaa0b9cea8e56467437975103d04392958a68795 (2,022,032 bytes). Every packaged immutable file matched installed file; installed ARM64 d7dd105446a229f981e5cdd1aed862a13e3c7d04220f04fb08eadde175bb15c1. This independently identifies installed files, not in-memory attestation. Manager-installed module left unchanged.

Diagnostics rebuilt successfully: 17 seconds, 38 tasks (7 executed/31 up-to-date). APK SHA-256 **2dc9ced8eecce966bc6e0f6f42f17824812c1ebb0bea889d8d798fd477797352**, verified from installed package paths in users 0/17. Packaged C exactly equals source: **2f7b201e99d58322efbf34663a91d30323f160e939b10882d133c2d3a441831a** (5,974 bytes).

The existing external-profile transport reads exact packaged C bytes supplied via run-as. Requested label `external` produces honest report label **external**, while launch/publication metadata identifies **fixture C** with its raw and canonical hashes. B is neither relabeled nor modified. Temporary probe-only index overlays used the existing kernel-lock/CAS helper; published body hashes match canonical source profiles. Report hashes match exact external-file bytes; raw-vs-canonical hashes are independently recorded, not assumed identical. All expectations are supplied facts, not observed host values.

## Captures

| User / fixture | Capture | PID | Legacy PASS / OBSERVED / BLOCKED | Envelope PASS / OBSERVED / MISSING |
| --- | --- | --- | --- | --- |
| 0/A | 1 | 25968 | 81/4/4 | 1/14/1 |
| 17/C | 1 | 26528 | 75/7/7 | 3/3/10 |
| 17/C | 2 | 31219 | 76/10/3 | 3/3/10 |

Legacy and envelope FAIL counts are zero in all captures. Missing/blocked rows remain explicit limitations. User0's A is schema0.3 without Reality and supplies no new Reality expectations.

## PARTIAL-path outcome

**1 measured APPLIED path / 16 remaining PARTIAL** in this ledger-only measurement classification; no contract/generated table changes and zero VERIFIED promotions. The applied path is `reality.carrier.values.sim_state`: both no-argument and slot0 readers returned PASS/HASH_MATCH_ONLY against ready=5, with digest ef2d127de37b942baad06145e54b0c619a1f22327b2ebbcfbec78f5564afe39d on both cold PIDs. This is scoped local SIM state application, not physical SIM readiness or a service verdict. The legacy `telephony.simState` remains OBSERVED/NO_PROFILE_EXPECTATION because the existing legacy external-profile row does not use the envelope expectation. All operator numeric/name/country and phone/modem count rows matched twice.

| Path group | Outcome | Exact remaining limit |
| --- | --- | --- |
| carrier.sim_state (1) | MATCH twice; measured APPLIED | No VERIFIED or real-SIM service claim |
| compute.gpu (1) | NOT_DISPLAYED; MISSING | PROFILE_SURFACE_UNAVAILABLE; C supplies SoC only, no GPU |
| memory.free_storage_gib (1) | NOT_DISPLAYED; MISSING | C supplies nominal RAM/storage only, no free-storage expectation |
| packages aggregate + leaves (4) | NOT_DISPLAYED; MISSING | C packages availability unknown, values empty |
| sensor inventory + leaves (7) | NOT_DISPLAYED; MISSING | C sensors availability unknown, values empty |
| webview.provider/version (2) | NOT_DISPLAYED; MISSING | C deliberately leaves installed provider/version unknown |
| webview.user_agent (1) | NOT_DISPLAYED; MISSING | Expectation exists; default and concrete instance readers returned AndroidRuntimeException on C. A instance reader returned RuntimeException. No exception message/stack retained by the fixed reader; no cause inferred or code fix attempted. |

Full per-path statuses, expectations/actual digests and reasons are in partial-path-outcomes.json. C did **not** contain every expectation described in the dispatch; missing facts were preserved rather than filled from the device.

## Permissions and determinism

User17's normal dialogs were approved through visible Android UI: precise location while using app, then nearby-device Allow. No shell grant or AppOps write. Screenshots and tap actions preserved. In C capture1, wifi.bssid/ssid and bluetooth.address/name still recorded precise permission-denial reasons; capture2 made those readable. Bluetooth address became PASS; other externally-dependent/no-expectation rows remain OBSERVED. Capture1 and2 span this permission transition, so full legacy report equality is **not** claimed. No third speculative capture was performed.

The remaining three legacy BLOCKED rows are camera.characteristics, sensors.list and packages.visible, each PROFILE_SURFACE_UNAVAILABLE from unknown C facts—not permanent platform permission blocks. Filesystem build.prop/proc.version remain their own BLOCKED permission limitations. The two SIM envelope expectations matched identical digests across distinct cold processes. All envelope result states/digests are stable across those captures; matching absent digests do not constitute successful measurements. Dynamic external observations remain observations.

## Audit / cleanup

Production persisted before/after snapshots equal: APK SHA, companion permission grants/stable AppOps, accessible binding/index stores, published profile bodies and shares. Temporary probe bindings/body directories and staging scratch removed; production index restored to **327 entries**. Diagnostics globally uninstalled; pm paths and private/DE/external probe directories absent for both users. User0 active, loaded candidate module retained without changes. The authorized diagnostics UI grants were scoped to the disposable package, which was removed. No production permission reset/exception was used.

No platform verdict, parity verification, permanent block or broader coverage claim.

## Commands

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --offline -- python -X utf8 <evidence>/run_ops.py install
uv run --offline --with jsonschema -- python -X utf8 <evidence>/run_ops.py capture 0 1
uv run --offline --with jsonschema -- python -X utf8 <evidence>/run_ops.py capture 17 1
# Normal UI-only grants: input tap 540 1545, then 540 1420; shown in ui-grants.json.
uv run --offline --with jsonschema -- python -X utf8 <evidence>/run_ops.py capture 17 2
uv run --offline -- python -X utf8 <evidence>/run_ops.py cleanup
git diff --check
```

Per-device argv/exit codes in commands.jsonl; exact inherited helper source and run-only orchestration retained. No test suite was rerun because no code changed; build, all capture/schema checks, artifact hashes, audits and cleanup checked.

## Open questions

- Source-supported GPU/free-space/package/provider/version/sensor expectations are still absent. They require explicit fixture work; do not fabricate inventory or installed versions.
- Both WebView UA readers fail on C. The report safely records AndroidRuntimeException only; targeted diagnostics/code work is needed to establish cause. No WebView row was promoted.
- A third capture after stable grants would be required for full identical-permission legacy determinism; this run preserves the transition instead of claiming it.
- User0 received the requested single smoke capture, so no user0 repeated Reality closure is claimed.
