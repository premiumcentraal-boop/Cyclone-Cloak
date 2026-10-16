# Profile-transition harness release preparation (historical draft)

Final release notes: [0.11.0-alpha.15](RELEASE_0.11.0-alpha.15.md), code 39.
Alpha.14 already existed; its tag and assets are preserved.

- Adds a phase-based PowerShell runner, structured probe rows, request/profile/user
  checks, transition and cold-restart diffs, and a compact redacted evidence ZIP.
- Retains the permission-free default probe and existing diagnostic row IDs.
- Preserves production bindings, profile schema, connector behavior, permissions,
  publication scripts, runtime code, and binding history.
- Device observations are local compatibility evidence, not a platform verdict.
- Full-profile mode audits published packages and probes their selected profile;
  it does not prove normal app publication throughput or Cyclone bootstrap.
- Device runs on 2026-10-16 completed with warnings: missing advertising SDK in
  both users and ungranted Wi-Fi/Bluetooth permissions in user 17. No permission
  grants were added to conceal these results.

This initial draft preceded the separately authorized alpha.15 release run.
See PROFILE-TEST-HARNESS.md and HANDOFF-2026-10-16-PROFILE-HARNESS-RESULTS.md
for commands, test/build results, evidence, and remaining limitations.
