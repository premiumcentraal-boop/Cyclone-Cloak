# Cyclone Cloak 0.11.0-alpha.12 — connection lifecycle and binding runs

App and module packaging version code: **36**.

## Connection lifecycle

- Process start, APK self-update, activity resume, Cyclone registry wake, Cyclone package changes and completion of the existing module check/repair path trigger reconnection.
- A process-owned single-flight coordinator coalesces rapid triggers and uses bounded exponential backoff with jitter (five attempts per cycle). Exhausted retries and approval, installation or handshake problems show actionable reasons.
- The last known registry appears immediately with a Last seen timestamp while live verification runs. Cached profiles are display-only; cached data does not grant binding authorization or drive reconciliation.
- An always-visible connection chip opens status details and Retry. Existing handshake information identifies which side needs updating when the version evidence permits it.
- No boot permission was added. **Post-boot reconnection depends on the existing Cyclone wake or the next app launch/resume.** External module changes are observed through subsequent wake/launch/resume; there is no new module broadcast or background service. Later broadcast-triggered retries remain best-effort while Android keeps the process alive.

## Binding run history and debug bundles

- Health → Runs records bind batches, reconciliation retries, enable/disable and remove operations. Expand a row to inspect targets, Android users, packages, device profile references, outcome, reason codes, duration and observed publication phases.
- A separate bounded local journal retains up to 200 runs, with size-limited file rotation and interruption recovery. Failed and needs-attention runs are visually distinct and offer reconciliation retry without recreating historical records.
- Download debug bundle exports one run or the last 24 hours through Android's standard share sheet. ZIPs contain run records, time-windowed diagnostics, a current binding metadata projection, observed module-state hash, connector handshake metadata and versions.
- Exports exclude profile bodies, mirrored identity summaries, seeds, secret identifiers, credentials and private content. The module-state hash describes the last observed diagnostic state, not a measured installed binary or a fresh root-only publication-tree read. Retention gaps and snapshot timing are explicit.

## Compatibility and verification

No connector protocol, binding logic, publication script, existing profile/binding storage schema, runtime/module behavior or permission changes. Module metadata changes only to match release packaging. The registry cache and run journal are separate local diagnostics records. **No platform verdict is claimed.**

Local release checks: **497 app tests, 12 module tests, 227 forge tests and 33 schema tests**, plus debug build and `git diff --check`. The standard release CI reruns all suites, signs the APK and verifies version/code and exact embedded module ZIP agreement before publishing.

**Device verification is pending. No device actions were performed for this release.** Earlier evidence does not verify this artifact. Pending checks: update-restart auto-reconnect; chip connected/reconnecting/update/install/approval/error states; cached display during live verification; the share chooser and saved ZIP contents on a real device. Share-chooser device verification remains unverified.

## Upgrade

Use the normal signed APK/module update flow, retaining app data and saved profiles. No profile or binding migration/reset is required. Existing saved profiles remain compatible. No device restart was performed as part of this release run.

Assets: `app-release.apk`, `cyclone-cloak-0.11.0-alpha.12.zip`.
