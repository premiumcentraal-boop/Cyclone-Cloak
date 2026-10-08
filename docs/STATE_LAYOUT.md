# State layout contract

The companion app publishes resolved device profiles for the platform module
reader. One layout, two implementations; change both in the same commit.

## Layout (v2)

- Module state root: `/data/adb/cyclone_cloak/state-v1/` - root-owned, mode 700,
  written by the companion app through Magisk `su --mount-master` using a
  validated temporary tree and fail-fast publish script.
- Staging root: `<companion cache>/state-staging/` - what the companion builds
  before publishing; never read by the module.
- Per-binding dir: `<root>/<sha256 key>/profile.json` - the full cloak profile JSON.
- Module index: `<root>/index.json` - `{"schemaVersion": 2, "entries": {...}}` maps
  `<androidUserId>/<packageName>` to `{"profileId", "cloakProfileId", "key"}`.
  Rebuilt from current enabled bindings on every publish.
- Key: lowercase hex SHA-256 of the tuple `<profileId>\n<androidUserId>\n<packageName>`.

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
- The key derivation, index shape and profile JSON contract are unchanged from
  v1; only the location and permissions changed.
