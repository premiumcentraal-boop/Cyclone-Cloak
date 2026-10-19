# Cyclone Cloak 0.11.0-alpha.27

Prerelease; matching companion/module version code **51**.

## Local Play/Google readiness

- Consolidates metadata-only Google surface assignment, conservative readiness
  evaluation/storage and the separate Play/Google evidence harness.
- Profiles gains explicit local JSON import, preview and save, readiness states,
  source/time, package/account observations and stale/mismatch reasons.
- Strict report validation binds exact saved profile bytes, Android user, current
  companion APK/bundled-module artifact set and freshness. Foreign, malformed,
  incomplete or stale reports cannot replace accepted evidence. Missing required
  observations stay UNKNOWN; optional observations never become readiness passes.
- Account-slot conflicts remain visible and require a second explicit sharing
  confirmation bound to the current assignment inventory. No account writes,
  credentials, token storage or global account discovery.
- Separate diagnostics APK `dev.cyclone.cloak.playevidence` declares zero
  permissions. Collection requires a user tap. Optional public Integrity requests
  require separate confirmation; only actual token-receipt metadata is retained,
  never tokens or fabricated decoded verdicts.

## Verification and accepted limitations

Local checks: **2,015 tests passed**: app 692, module 40, Play evidence 20,
standard/diagnostics probes 109 each, forge 907, schema 131, host probe 7.
Debug/release builds and exact APK/module metadata/embedding checks passed.
Production app lint has 0 errors / 19 inherited advisory warnings; Play evidence
and module lint have no issues. No new compiler warnings were observed.

**The full lint gate is not green.** Both unchanged legacy runtime probe flavors
have 9 errors / 12 warnings: six permission-analysis errors around privileged
IMEI/Bluetooth readers and three API-30/min-29 errors for active modem count and
Wi-Fi builder calls. No permissions, suppressions or lint baseline were added.
See the [integration handoff](HANDOFF-2026-10-19-PLAY-READINESS-INTEGRATION-V1.md)
for exact locations and commands. These findings are outside the new Play APK.

**Physical capture/import is UNVERIFIED.** ADB had no attached device; no new
device evidence is attached. Play Protect and actual Google settings access are
not measured by this harness. Account visibility is not ownership or isolation
proof. Package availability and encrypted Integrity token receipt are not a
platform verdict. No authenticated decoded verdict/provider is implemented.

The owner explicitly approved this prerelease with these device/lint limitations,
conditional on successful signed validation and release CI. They remain open;
historical device evidence does not verify these new surfaces. No certification,
broader compatibility or permanent trust is claimed.

## Upgrade and artifacts

Use the normal signed `app-release.apk` and matching
`cyclone-cloak-0.11.0-alpha.27.zip`. Existing 0.2/0.3/0.4 profiles remain readable
without destructive migration. No runtime/module behavior, connector protocol,
publication scripts, binding storage layout or production permissions changed.
Readiness/assignment metadata uses the existing private stores. Their writes are
individually atomic; an assignment can survive a subsequent readiness-save
failure, which is explicitly reported.

Optional `playEvidence-release.apk` is a separate developer diagnostics app,
version `1.0-diagnostics` / code 1, signed by the existing release identity.
A locally debug-signed copy may have a different signer and cannot be updated
in place; preserve its reports before any separately approved replacement.
Installing this APK does not install/update the companion or module.

Source, schema and usage guides accompany the release. Real device capture,
production import UI verification and supported readers for the unknown surfaces
remain follow-up work. No device actions are performed by this release run.
