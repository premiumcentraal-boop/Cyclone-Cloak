# Reconciliation regression coverage

Local baseline: `v0.10.0-alpha.1`, checked on 2026-10-11. The connector contract,
transport methods, scopes, binding schema and identity storage format are unchanged.

`ReconciliationBehaviorTest` exercises production planning/reconciliation/sync
against in-memory local records and Cyclone-shaped envelopes. Its fake connector
returns independent JSON read responses, as serialized connector replies do.

| Case | Expected behavior tested |
|---|---|
| Android user changes | Follow the same profile ID to the new user, retain identity/revision/timestamp, republish once; repeated sync performs no new write. |
| Removed profiles | A fresh removal event plus absence from the current snapshot removes only that profile's bindings; identity files and unrelated records remain. Snapshot absence alone retains records and warns. A stale removal event cannot delete a currently present/restored profile. |
| Trashed profiles | Retain local records, remote value and pushed bookkeeping; warn, avoid config calls, and resume without rewriting the value when restored. |
| Missing app packages | Retain the local choice and previously written remote value; warn and resume when the package returns. An unavailable app list or unknown Android user also pauses safely. |
| Duplicate local records | Do not choose an identity or clear an existing remote tuple. Retain records and warn, including on first-run sweeping. Equal timestamp ties survive parsing and serialization. Existing legacy migration still selects an unambiguously newer record. |
| Rate limit | Retry a write once after 1,100 ms backoff. Exhausted retries warn and retain records, prior remote value and bookkeeping for the next sync. |
| Failed Cyclone writes | Failed value/status/clear writes preserve local choices and surface an issue. A successful value with failed status retries only status; a failed clear retains bookkeeping until clearing succeeds. |
| Successful write and read-back | A strict tuple/envelope read returns the expected value and health; modifying a returned JSON object cannot modify remote storage. The next sync reads without rewriting. A failed subsequent read warns and retains bookkeeping. |

Small corrections exposed by these tests: an absent profile is no longer proof
of permanent deletion; duplicate ties are retained for review; deferred logical
bindings are excluded from remote cleanup and first-run sweeping; failed clears
now report a per-binding issue. Explicit disable/unbind and confirmed removal
continue to clear known tuples. An ambiguous orphan can persist indefinitely if
its removal event was missed; review/remove it explicitly after reconnecting.

Read-back here means an explicit connector read in the regression test and the
normal next-sync read. This sprint does not introduce a second verification read
inside every production write or claim on-device connector/runtime verification.

Commands:

```powershell
gradle -p android :app:testDebugUnitTest
gradle -p android :app:assembleDebug :module:packageModule
uv run --with pytest -- python -m pytest forge/tests
```

Local results: **61 JVM tests passed**, including 18 focused cases plus existing
`CycloneSyncTest`, `CycloneVectorsTest` and `CloakBindingStoreTest`. Production
Kotlin sources and tests were compiled directly with Kotlin 2.0.20/JUnit 4.13.2
against the built app classes and real `org.json`. These are local JVM checks.
The normal Android test target remains blocked before test execution at
`:app:copyRobolectricRuntime` by uncached
`android-all-instrumented-14-robolectric-10818077-i7.jar`. **138 Python tests passed**.
App and module targets build locally; no warnings. No phone tests were run;
[the deferred checklist](DEFERRED-DEVICE-CHECKS.md) remains in effect.
