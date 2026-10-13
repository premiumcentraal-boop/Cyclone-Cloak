# Cyclone Cloak v0.11.0-alpha.5 — Local compatibility report review

This prerelease improves local compatibility evidence review. It does not infer
a platform verdict or claim physical-device compatibility.

- Selected identities show a short report identifier when available, source,
  checked time, explicit saved-profile and bundled-artifact matches, and freshness.
  Missing metadata, changed profiles/artifacts, stale reports and future timestamps
  have distinct states.
- Compatibility reports and runtime diagnostics open a preview before explicit
  saving. Diagnostics use existing proof classification: VERIFIED becomes a PASS
  check, FAIL becomes FAIL, and partial/blocked/incomplete/unmapped/observed proof
  produces WARNING. A raw probe PASS is not automatically VERIFIED.
- Local provenance binds reports to profile and artifact digests. Replay/newer-report
  acceptance rules remain in force. Artifact digests are retained only when the
  diagnostics artifact and metadata agree. Source profile mismatches prevent saving.
- Review report details shows the saved summary, reason and provider. Individual
  check rows are available in the import preview and are not stored in the summary.
- Mark needs rotation requires confirmation and preserves evidence provenance.
  Clear after explicit local reset requires acknowledgement of a completed reset
  or reseed, records Unknown, and retains replay history. Neither action resets,
  reseeds or deletes the identity. Changed records require another review.
- Safe inline load/save errors retain retry and cancel paths.

## Upgrade and limitations

Install the signed APK as an update. App and module packaging versions agree at
`0.11.0-alpha.5`, code 29. The module ZIP updates packaging metadata only:
there are no runtime/module, connector, binding or publication behavior changes.
No profile schema migration or rewrite is required. No networking, analytics or
new permissions were added.

Reports remain user-supplied local evidence, not authenticated measurements.
Matching an artifact means the bundled module payload, not the installed module.
Fixture diagnostics without a saved-profile binding remain warnings. Local PASS
is not a platform verdict. Physical-device checks remain unverified; no device
tests were run for this release.

## Validation and artifacts

The preceding local handoff reports 378 app tests (355 passed, 23 Windows shell-test
skips), 12 module tests and 33 schema tests passed, plus a successful debug build.
Those suites were not rerun locally for packaging. Release CI is the confirmation
gate: full app/module, forge and schema suites, signed release build, app/module
version agreement, and exact embedded-module ZIP verification before publication.

Artifacts: `app-release.apk` and `cyclone-cloak-0.11.0-alpha.5.zip`.
See the release Actions run for the final CI result.
