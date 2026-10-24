# RC2 resume after the artifact-gate stop

Supersedes the "install gate" section of `HANDOFF-2026-10-24-RC2-CANDIDATE.md`. Both defects
that stopped `2026-10-24-170600-rc2-smoke` are fixed on `main` at `ff36624`. You are clear to
install and run.

## Your two findings, and what changed

1. **Explicit version pair.** Correct, and a real bug. The pair built `app_version=v1.0.0-rc.2`,
   while Android's `versionName` is `1.0.0-rc.2`, so `INSTALLED_VERSION_MISMATCH` would have
   fired after the reboot rather than before it. The `v` is now stripped for the app comparison
   and kept for the module, whose `module.prop` version does carry it.
2. **Module hash pin lived in prose.** Correct. `45a2788d…` was only in the handoff, so nothing
   in the runner could compare anything. There is now a real `--expected-module-hash` gate that
   hard-stops on mismatch and records both the expected and installed values in the manifest.

The local hash did not reproduce in CI and your read that it is more than ZIP timestamps matches
what was measured: both native libraries differ. The local value is therefore not authoritative
and the handoff no longer pins it.

## Open item you own

The native binary difference is **unattributed**. Pin the CI hash for this run and note whether
it is stable across CI reruns. If the same commit produces a different arm64 hash on a second
run, that is build nondeterminism and worth reporting as its own finding rather than another
device cycle.

## Current pins

| Artifact | Value |
|---|---|
| signed APK | `8b07e2c24e084800f1f629850735bef6d8ce00630074fa884eb287d7a19c0456` |
| CI module ZIP | `afebb16af867569f5b529ad16636fd1d4e3f87d9bd84b1c4347a0360e7533a17` |
| CI arm64 zygisk | `5b4c6d01e3ff180c2d535bd7913a43960b469d34815100ac8dd0615c0c9b34f8` |
| version pair | `v1.0.0-rc.2:61` |

Hashes were supplied by the previous run. Confirm them against the signed workflow output before
trusting them; they are not re-established here.

## Steps

1. Pull `ff36624`.
2. Rebuild the diagnostics APK from that tree and record its SHA-256. The probe is unchanged
   behaviourally, but the run must name the exact artifact.
3. Install the signed RC2 app and matching module. Module via
   `su --mount-master -c 'magisk --install-module <zip>'`.
4. Record installer exit code and output, then reboot once.
5. Confirm both users are unlocked.
6. Run S8 with the existing gates plus:
   `--expected-version v1.0.0-rc.2:61 --expected-module-hash 5b4c6d01e3ff180c2d535bd7913a43960b469d34815100ac8dd0615c0c9b34f8`

## Expected result

The bound package-list row reaches `NOT_PRESENT` for both users. `FOUND:` is a real package
sighting; `DENIED:` means an exception still escapes the hook.

Do not relax `NOT_PRESENT`, do not bypass either pin, and do not promote any row to VERIFIED.
Stop at the first failed gate and report rather than retrying past it.

## Provenance

Three cycles, users 0 and 17, with the existing permission/AppOps, binding and cleanup audits
unchanged. The mutable `load-records.jsonl` changed during the last read-only wait; keep
separating mutable from immutable module files. An installed-module file hash is not in-process
binary proof.
