# Cyclone Cloak v0.11.0-alpha.4 — Local compatibility posture

This prerelease adds the neutral local posture layer and the report decision
engine from main through `4edfec8`. It is a **local evidence layer, not a platform
verdict**, attestation result or promise of physical-device compatibility.

- Separate app-private posture records support UNKNOWN, PASS, DEGRADED, FAILED
  and NEEDS_ROTATION. Existing profile files and engine sidecars remain unchanged.
- The default provider returns UNKNOWN without inferring a result from a profile.
- The local decision engine evaluates explicit check rows, profile body SHA-256,
  the bundled module payload digest and report age. Changed identities/artifacts
  and stale reports cannot silently reuse a prior local PASS.
- Identity cards show recorded state, source, timestamp and reason. Local posture
  reports can be loaded using the existing local file picker.
- Rotation helpers do not reset, reseed or delete profiles automatically. Clearing
  rotation requires a completed explicit local reset and deliberate persistence.
- Existing engine import, uniqueness generation, diagnostics coverage and saved
  profile compatibility remain intact. No schema migration or profile rewrite is
  required. No connector, binding, publication or module/runtime behavior changes.

## Upgrade and limitations

Update with the signed APK. Both APK and module ZIP use alpha.4, version code 28;
module packaging metadata changes, runtime behavior does not. Existing local
backups retain their behavior and do not include posture records or engine
sidecars. The artifact comparison is against the bundled payload, not proof of
the installed module. No device tests were performed for this release.

No platform verdict is inferred from PASS or raw report verdict text. Reports
are user-supplied local evidence, not authenticated measurements. The diagnostics-
to-posture preview/save adapter is the next development sprint and is **not part
of alpha.4**.

## Release validation

Release CI runs the full forge and supplemental schema suites and Android app/
module unit suites on Linux, then builds signed `app-release.apk` and
`cyclone-cloak-0.11.0-alpha.4.zip`. It checks version agreement and that the APK
embeds the exact attached module ZIP before publication. See the release Actions
run for final counts and build status. Physical-device verification remains
unverified.
