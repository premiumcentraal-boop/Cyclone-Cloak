# Cyclone Cloak 0.11.0-alpha.24

Signed prerelease; app/module version code 48. No platform verdict is claimed.

- Hardened display-mode construction: optional alternative-refresh/HDR lookups are
  independent, rich-constructor failure falls back to the simple constructor, and
  active/supported modes share the authoritative base-profile tuple.
- Fixed Java hook-loader lifetime: a strong JNI global reference prevents collection
  of DEX methods still referenced by native trampolines. Hook engine API/debug mode
  comes from the real host ART process, independently of profile Build values.
- Linked diagnostics include actual screen tuples and refresh rate. Lifecycle
  tooling distinguishes fresh matching probe crashes from report timeouts.
- Included Device Factory v1 (original `7f34ea2`): opt-in pure Python factory,
  twelve canonical references, deterministic identifiers, strict acceptance,
  identifier-free quality reports and all-or-nothing draft batches. Existing
  schemas/generators remain unchanged. Firmware/HAL approximations are documented.
- Included Profile Operations Console (original `5121b70`): preview/approve creation,
  reviewed assignment/replacement, per-profile readiness/history and document-picker
  diagnostics export. Existing stores and reconciliation/publication paths are reused.

## Physical result and limits

[Measured evidence](runtime-evidence/2026-10-18-222138-alpha24-integrated-stabilization/summary.md), code `49564ce`: 12 warm repetitions
survived compacting GC in one process; three full lifecycle cycles completed 24
captures across users 0 and 17. Every bound capture had 13 linked PASS / 0 FAIL.
User 0/A: 82 legacy PASS, 7 OBSERVED, no BLOCKED, active 1080x2400 @ 90 Hz.
User 17/B: 81 PASS, 4 OBSERVED, 4 BLOCKED, active 1080x2340 @ 120 Hz.
Supported modes included each active tuple. Fixture/user/report/published hashes
matched. Warm and cold removal captures completed; cold restoration had zero
comparison failures. Production entries stayed 327; permission/AppOps and
production audits were unchanged and all temporary cleanup proofs passed.

Four user-17 permission blocks remain. Unbound reports retain expected fixture
mismatches; external-state/unavailable comparisons remain observations/blocks.
This does not verify arbitrary profiles, broader Reality coverage, factory firmware
or HAL approximations, new console interactions on a phone, or a platform verdict.
The previous failed warm capture is preserved as redacted prior-failure evidence.

## Upgrade and checks

Use the existing signed APK and matching module ZIP; one restart loads the updated
native module. No data clearing or profile migration. No profile/binding schema,
connector protocol, publication script, permission, credential or networking change.
The run journal remains format 1 with optional redacted previous-device references.

Local gate: **1,679 passed**, zero skips: app 598; module 40; probe 109 per flavor;
forge 735; schema 81; probe host tools 7. Debug/release builds and packaging pass
without compiler warnings. [Signed validation](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39163201635)
passed. CI reports existing action-version/cache warnings. Evidence assets include
`evidence-bundle.zip`, `summary.md` and `build-provenance.json`.
