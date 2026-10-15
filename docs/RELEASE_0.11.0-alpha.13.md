# Cyclone Cloak 0.11.0-alpha.13 — connector resilience and run recovery

App and module packaging version code: **37**.

## Connection

- Reconnect uses the existing single-flight coordinator, bounded backoff and registry cache. It opts out of root reads/publication, including on resume and manual reconnect, so connector verification does not wait for a separate Magisk approval.
- Status details give one next step. Retry is hidden while reconnecting; expired connector confirmation explicitly waits for user action rather than repeating confirmation automatically.
- Explicit repair and binding operations retain their privileged-session path. Post-boot reconnect still depends on Cyclone wake or the next app launch/resume; no new permission or background service was added.

## Binding run evidence

- Health → Runs shows active attempts and approval-wait guidance. Checkpoints preserve selections and evidence after local saves, publication and synchronization.
- Interrupted attempts recover as needs-attention, retaining the first observed failure. Recovery time is an observation time, not an exact crash timestamp. A failed recovery append retains pending evidence for another attempt.
- Retry reuses unchanged saved bindings. Unsaved selections return to review; a still-valid in-memory acknowledgement can be reused. Changed or ambiguous targets require review.
- Final publication failure, connector failure or missing reconciliation evidence cannot be reported as ready.
- Per-run and 24-hour redacted ZIPs now include `reconciliation.json` with historical counts and fixed failure codes. Existing files retain run records, filtered events, binding metadata, observed module-state hash, handshake observations and versions. Profile bodies, seeds, credentials and raw shell/connector output are excluded.

## Compatibility and upgrade

No connector protocol, profile/binding schema, binding identity, publication script, storage layout, runtime/module behavior or permission changes. The existing local journal's schema-version-1 envelope gains optional diagnostic fields; old records remain readable without migration. Module metadata changes only for matching release packaging. **Local evidence is not a platform verdict.**

Update the signed APK and matching module through the normal update flow, retaining app data. No reset or profile migration is required. This release run performs no device actions.

## Verification and limitations

Local checks passed: **515 app tests, 12 module tests, 227 forge tests, 33 schema tests**, debug build and `git diff --check`. Android tests had zero skips with WSL shell coverage enabled. Signed validation and release CI rerun the suites and verify version/code and exact embedded-module agreement before publishing.

**Device verification remains UNVERIFIED for this artifact.** Pending checks: update-restart reconnect latency and status guidance; approval expiry and retry with retained selections; active/interrupted run history; share chooser and seven-file ZIP contents on user 17. Earlier device evidence does not automatically verify this artifact. Root-free reconnect does not refresh root-backed Main-binding mirrors; explicit repair/binding retains that path. Bounded journal/event retention can leave evidence gaps.

Assets: `app-release.apk`, `cyclone-cloak-0.11.0-alpha.13.zip`.
