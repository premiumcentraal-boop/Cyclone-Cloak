# Cyclone Cloak 0.11.0-alpha.11 — batch privileged-shell session

App and module packaging version code: **35**.

- Binding batches reuse one persistent privileged shell for publication and synchronous reconciliation/mirror checks.
- A root preflight runs before any binding mutation. Denied, expired or unavailable access aborts cleanly with instructions to approve Cyclone Cloak in Magisk.
- Root commands share a configurable session budget, default **30 seconds**, rather than separate approval waits per command.
- The existing progress surface shows approval waiting and resumes when the shell responds. Waiting indicates delayed shell response; it does not directly inspect a Magisk prompt.
- Profile copies show their own one-time approval hint: Magisk grants apply to the Android-user UID. Grant until reboot may reduce repeated prompts.
- Publication scripts and lock repair, profile contents, storage layout, connector protocol, runtime/module behavior and permissions are unchanged. Module metadata changes only to match release packaging. No platform verdict is claimed.

Local checks: **453 app tests, 12 module tests, 227 forge tests and 33 schema tests** passed, including nine session tests and real shell checks through WSL. Debug build passed. Standard release CI reruns tests, signs the APK and checks exact embedded-module agreement before publication.

**Phone verification and device timing are UNVERIFIED.** No device actions were performed for this release. Earlier evidence does not verify this artifact. Results will be recorded separately for a seven-app batch: one initial prompt (zero if already granted), completion within seconds after grant, and denied preflight leaving existing bindings unchanged. Check the profile copy's own approval separately.

Upgrade through the normal APK/module update flow, keeping app data and saved profiles. No migration or reset is required.

Assets: `app-release.apk`, `cyclone-cloak-0.11.0-alpha.11.zip`.
