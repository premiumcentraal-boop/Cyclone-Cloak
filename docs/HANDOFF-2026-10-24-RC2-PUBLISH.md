# RC2: gate green, publish it

S8 passed 3/3 cycles at `2026-10-24-183452-rc2-envelope-retry`. The install gate did its job and
is now satisfied: masked rows 72/72 `NOT_PRESENT`, no FOUND, package comparison passing on all
six bound captures, both users restored cold every cycle, production/permission/AppOps/cleanup
unchanged.

This is the mechanical close-out. No engineering decisions needed.

## What to publish

`v1.0.0-rc.2`, code 61, signed predecessor `8b07e2c2…` / module ZIP `afebb16a…` from workflow
40105383085. Measured diagnostics APK `2320880189…`. Release notes already exist at
`docs/RELEASE_1.0.0-rc.2.md`.

Dispatch `release.yml` with `tag=v1.0.0-rc.2`. The workflow creates the tag on the checked-out
commit and re-verifies version, code and artifacts, so the tag must land on a commit whose app
version is still rc.2. Confirm that before dispatching; `main` has moved three commits since
`992b176` with probe, tool and docs work only, and the app version is unchanged.

Do not rebuild the app merely to move the tag. If the version in `android/app/build.gradle` is
still `1.0.0-rc.2` / `61`, the existing signed artifacts remain the measured pair.

## Notes for the release body

Keep the honest framing already in the release notes:

- Masked results are caller-visible measurements on this artifact, not broader invisibility or
  platform trust.
- `HASH_MATCH_ONLY` on the package row means the expected side merges profile assertions over the
  caller-visible inventory; it is not independent proof the host returned an overridden value.
- Remaining limits stay stated: 6 MISSING envelope rows per capture, 5 BLOCKED and 9 OBSERVED
  legacy rows, warm post-removal observations, mutable module load records.
- No platform verdict, certification or hardware-equivalence claim.

## After publishing

Record the release URL and asset hashes, and confirm the published APK still verifies with the
existing signer and embeds the exact module ZIP.

Two open items carry forward and should be named in the post-release record rather than silently
dropped:

1. `envelope.webview.defaultUa` and `instanceUa` return `RUNTIME_EXCEPTION:AndroidRuntimeException`
   on the worker thread while the `userAgent()` hook is loaded. Root cause is unattributed.
2. The remaining six MISSING envelope rows come from template `unknown` sections (sensors,
   camera, storage, DRM), not from the mask.
