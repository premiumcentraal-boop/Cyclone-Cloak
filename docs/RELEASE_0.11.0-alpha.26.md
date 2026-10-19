# Cyclone Cloak 0.11.0-alpha.26

Signed prerelease; matching app/module version code **50**.

## Play Surface Contract v1

- Additive, optional `play_surface` profile section for local account mode,
  stable opaque account slot, display hint, explicit sharing consent, provisioning,
  allowed package scope and review timestamp. No credentials or account contents.
- Local expectations for Play Services, Play Store, Play Protect, Web provider,
  Google settings access, install origin and backup/sync posture.
- Shared schema and Kotlin/Python parsers with bounded strict JSON, duplicate-key
  rejection, fixed machine-readable reason codes and non-sensitive explanations.
- Active account-slot reuse conflicts unless every assignment explicitly permits
  sharing. Package and service disagreements produce explicit conflicts; unknown
  or absent observations cannot silently become a successful check.
- Separate versioned local readiness records: READY, DEGRADED, BLOCKED or UNKNOWN,
  bound to profile ID/body hash, artifact hash, evidence digest, time and provider.
  Missing complete evidence cannot be encoded as READY. Binding checks preserve or
  downgrade supplied state; mismatches become UNKNOWN without relabelling reports.

## Verification

Local gate: **1,880 tests**, zero failures/errors/skips: app 608; module 40;
standard and diagnostics probe 109 each; forge 876; schema 131; host probe 7.
Debug/release assembly, probe builds, module packaging, artifact metadata,
lint/format, generated-rule and diff checks are required before release. The
normal signed-validation and release workflows rerun the gate and use the
existing release signing identity.

No device actions or new physical verification are part of alpha.26. Historical
device evidence remains specific to its measured artifact; it does not verify
this contract's future providers or service setup. **No platform verdict** or
broader device compatibility is claimed.

## Upgrade and limitations

Install the normal signed APK and matching module ZIP. App/module packaging
metadata agrees at alpha.26/code 50. Existing schema 0.2/0.3/0.4 profiles remain
readable unchanged; the new section is optional and no migration, automatic
generation, replacement, data clearing or storage rewrite occurs.

This release adds a contract and validation, not account provisioning, service
isolation, observation acquisition, readiness decisions, persistence or UI.
Provider authentication/signature verification is not implemented. Callers supply
exact-byte SHA-256 digests; matching hashes alone never promote readiness. Free
text hints must stay display labels and must not contain credentials. Schema
consumers must enable calendar/date-time format checks; both parsers enforce them.

Production runtime/module behavior, connector protocol, binding logic/storage,
publication scripts, permissions and network behavior are unchanged. No new
dependencies, credentials, key material or platform-attestation behavior.

Assets: `app-release.apk`, `cyclone-cloak-0.11.0-alpha.26.zip`,
`play-surface.schema.json`, `play-readiness.schema.json`,
`PLAY-SURFACE-CONTRACT-V1.md` and the implementation handoff. Source archives
include both implementations, generators and synthetic shared vectors. See
[contract guide](PLAY-SURFACE-CONTRACT-V1.md) and
[implementation handoff](HANDOFF-2026-10-19-PLAY-SURFACE-CONTRACT-V1.md).
