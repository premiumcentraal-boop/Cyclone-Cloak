# State layout contract

The companion app publishes resolved device profiles for the platform module
reader. One layout, two implementations; change both in the same commit.

## Layout

- Root: `<companion data>/no_backup/cyclone-profile-state-v1/`
- Per-binding dir: `<root>/<sha256 key>/profile.json` - the full cloak profile JSON.
- Module index: `<root>/index.json` - maps `<androidUserId>/<packageName>` to
  `{"profileId", "cloakProfileId", "key"}`. Rebuilt from current enabled bindings.
- Key: lowercase hex SHA-256 of the tuple `<profileId>\n<androidUserId>\n<packageName>`.

## Test vectors

Input `Cyclone_0123456789abcdef`, user `7`, package `com.example.app` yields key
`d531e13ba21eb8f7c08a1afc89d49f148ac076b065ce3f9bd8aebbf42fb0b122`. Verified by
`CloakStateLayoutTest` (JVM) and `forge/tests/test_state_layout.py` (parity).

## Notes

- The module reads `/data/user/<user>/dev.cyclone.cloak/no_backup/cyclone-profile-state-v1/`
  during app specialization. The companion must be installed in the same Android
  user as the scoped app.
- State files are written world-readable for alpha; access is tightened in the
  isolation sprint.