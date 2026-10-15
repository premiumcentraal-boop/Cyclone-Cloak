# State layout contract

The companion app publishes resolved device profiles for the platform module
reader. One layout, two implementations; change both in the same commit.

## Layout (v2)

- Module state root: `/data/adb/cyclone_cloak/state-v1/` - root-owned, mode 700,
  written by the companion app through Magisk `su --mount-master` using a
  validated temporary tree and fail-fast publish script.
- Staging root: `<companion cache>/state-staging-<unique>/` - what the companion builds
  before publishing; never read by the module.
- Per-binding dir: `<root>/<sha256 key>/profile.json` - the full cloak profile JSON.
- Per-binding dir: `<root>/<sha256 key>/pif.json` - PIF-style key/value Play Integrity
  posture derived from the same device block. The module does not read it; external consumption and attestation are unverified.
- Module index: `<root>/index.json` - `{"schemaVersion": 2, "entries": {...}}` maps
  `<androidUserId>/<packageName>` to `{"profileId", "cloakProfileId", "key"}`.
  Rebuilt from all publisher shares on every publish (see Shares below).
  Each entry may also carry `publisher` (the Android user of the Cloak that published it) and
  `summary` (the display summary Cloak sends Cyclone). The module reads only `key`.
- Key: lowercase hex SHA-256 of the tuple `<profileId>\n<androidUserId>\n<packageName>`.

## Shares (since 0.9.0-alpha.1)

Cyclone installs Cloak in every Cyclone profile, and every install publishes into the same
device-wide tree. So that one install never drops another's bindings, each publishes only its own
share and the tree the module reads is assembled from all of them:

- `/data/adb/cyclone_cloak/publishers/<androidUser>/` - one install's share: its per-binding dirs and
  `index.part` (the members of `entries`, without braces). Replaced atomically on that install's
  publish.
- `/data/adb/cyclone_cloak/publishers.main` - the Android user of Cloak in Main.
- Cloak in Main publishes its bindings for every profile; Cloak in a profile publishes only its own
  apps for its own user.
- On every publish `state-v1/` is rebuilt from all shares, Main's last: for an app both bound, Main's
  per-binding dir and index member win (nlohmann/json and Android's org.json both keep the last of
  a repeated key).
- Cloak in Main prunes shares only when the current registry and reconciliation
  establish removal. Ambiguous records retain their old user shares with warnings;
  reused Android user numbers require review.
- The first publish after updating from an older release keeps the existing tree as Main's share.
- Cloak in a profile reads the assembled `index.json` (read-only, through the same su checks) to
  mirror what Main bound for it.

## Publication locking and recovery (local sprint)

The writer holds a nonblocking exclusive `flock` on
`/data/adb/cyclone_cloak/publication.lock` through migration, share replacement,
Main's pruning and merge/swap. The root-owned lock file is **never deleted**:
removing it would let another process lock a different inode. Android's
[Toybox flock implementation](https://android.googlesource.com/platform/external/toybox/+/refs/heads/main/toys/other/flock.c)
provides the kernel lock; missing locking support fails publication closed.
`publication.owner` is a private interruption marker, not a reader input.
Only after the kernel grants the lock can a leftover marker be treated as stale.
PID reuse and elapsed time cannot evict a live publisher. Kernel ownership ends
when the last owning descriptor closes, including after process death.

Each app invocation uses a separate private staging directory. Under the shared
lock, missing shares are restored from `<user>.old` before merging. Legacy
migration is assembled under `publishers.migration` before being renamed into
place. `state-v1.previous` is restored first when `state-v1` is missing or its
index is empty and the backup has a nonempty index; it is
never deleted merely because another publication started. A failed final rename
attempts rollback. The backup remains until post-swap checks pass, and a failed
post-swap check rolls the uncommitted candidate back as well. If rollback also fails, the complete previous tree remains at
`state-v1.previous` and the next locked attempt retries recovery. No bindings or
reader keys change. A failed merge can leave a completed publisher share pending
for the next merge; the currently published complete tree remains authoritative.

Since alpha.14, each publisher's key directories are copied into the candidate in
one `cp` invocation. Existing candidate keys are removed before an overriding
share is copied, so whole-key replacement and Main precedence are unchanged.
Staging remains a separate copy; source profiles and shares are not hard-linked.
Privilege preflight retains its 30-second budget. Granted publication work uses
a fixed 120-second shared deadline anchored to the grant, including when invoked
through the batch session. A timeout after confirmed root access is publication
failure rather than an approval failure. Locking and recovery remain unchanged.
The runtime excludes `dev.cyclone.cloak` before profile lookup, independently of
Android user, without deleting self-binding records from any store or index.

The swap retains the existing two-rename reader contract. There can be a brief
interval with no `state-v1`, and a failed rollback leaves it unavailable until
recovery succeeds. This does not provide power-loss durability or coordination
with older app versions/external writers that ignore the lock. Upgrade every
publisher before device concurrency testing. The runtime probe's direct index
edits must still run without concurrent app publication.

Shell outcomes are `CLOAK_PUBLICATION=LOCKED|FAILED|RECOVERED|COMPLETED`.
`LOCKED` requires retry without changing shares/state; `RECOVERED` and `COMPLETED`
mean the merge succeeded. A stale owner marker produces
`CLOAK_PUBLICATION_DETAIL=STALE_LOCK` only after exclusive acquisition; it is
reported separately from the final outcome, including in redacted diagnostics.
`FAILED` includes a recovery detail:
`LAST_TREE_RETAINED`, `RESTORE_PENDING`, or `NO_PREVIOUS_TREE`. Root Doctor retains its existing
`READY`/`PUBLISH_FAILED` codes and exposes the publication reason separately;
Health Doctor displays fixed reasons and copies only allowlisted messages.

Local checks on 2026-10-11: 14 `PublicationRecoveryTest` cases exercise real Linux
kernel locks through WSL Ubuntu, stale markers, SIGKILL, concurrent publishers,
share/swap/rollback failures and recovery. Existing merge and Root Doctor tests
also pass. Run `gradle -p android :app:testDebugUnitTest` on POSIX; on Windows with
Ubuntu WSL, set `$env:CLOAK_TEST_WSL='1'` to run these shell cases rather than skip
them. Full Gradle test execution is currently blocked by uncached Robolectric
runtime dependencies; the production Kotlin sources and these tests were compiled
and executed directly with Kotlin/JUnit. Android Toybox/device verification is
pending; no phone installation or reboot was performed.

## Test vectors

Input `Cyclone_0123456789abcdef`, user `7`, package `com.example.app` yields key
`d531e13ba21eb8f7c08a1afc89d49f148ac076b065ce3f9bd8aebbf42fb0b122`. Verified by `CloakStateLayoutTest` (JVM) and `forge/tests/test_state_layout.py`
(parity).

## Notes

- The module process does not read `/data/adb` directly. During
  `preAppSpecialize`, it requests the current user/package profile over the
  Zygisk companion socket; that handler runs in Magisk's root companion process.
  The request is constrained to the package and Android user being specialized.
- `preAppSpecialize` runs with Zygote's privilege, not full superuser access.
  The companion process is the privileged reader for the root-only state tree.
- The staging tree lives in the companion's private cache and is never read by
  the Zygisk module; only the `su` publish copy lands in the root-owned root.
- On every publish the companion purges legacy world-readable state left by
  pre-0.5 releases under `no_backup`.
- The v2 root-location change retained key derivation and the profile JSON contract.
  Since 0.9.0, publisher shares and optional `publisher`/`summary` metadata extend
  publication while retaining the module's `key` lookup. Profile schema remains 0.2.
