# Cyclone Cloak Architecture

Current published prerelease: `v0.10.0-rc.1`, based on `v0.10.0-alpha.1`. See [CURRENT-STATE.md](CURRENT-STATE.md)
for versions, local verification and unresolved items. Dated release reports and
checkpoints describe their historical releases.

## System view

```text
Cyclone app in the same Android user
        | approved cyclone.connector/1.3 provider/Binder calls and events
        v
Cloak companion: UI -> Cyclone sync -> root publishing
        | su --mount-master
        v
/data/adb/cyclone_cloak/publishers/<androidUser>/
        | merge shares, Main takes precedence
        v
/data/adb/cyclone_cloak/state-v1/
        | root Zygisk companion reads the user/package profile
        v
Zygisk module in the scoped app -> Build/properties + Pine Java callbacks
```

The connector requires Cyclone 5.0.0-alpha.122 or newer. Cloak talks to Cyclone
in its own Android user. The previously proposed loopback HTTPS API is not implemented.

## App layout

`android/probe` is an independent diagnostic APK, not part of the companion or
module payload. It reads framework/native APIs against two synthetic fixtures
and displays per-read results. [RUNTIME-COVERAGE.md](RUNTIME-COVERAGE.md) records
device evidence separately from source implementation and local callback tests.

`MainActivity` hosts `CloakTheme` and `CloakApp` using `CloakViewModel`. The view
model exposes observable state and serializes changes with a mutex on the IO
dispatcher. `ConnectorService` handles connector registration and wakes.

Packages under `android/app/src/main/java/dev/cyclone/cloak/`:

| Package | Responsibility |
| --- | --- |
| `forge/` | Shared phone catalog, phone drafts, dump/JSON imports, coherence validation, seeded identities and fleets, PIF data |
| `data/` | Identities, custom phones, bindings, install placement and atomic JSON writes |
| `root/` | Root Doctor, module repair, binding resolution, staging and root publication |
| `cyclone/` | Connector bridge, approval/scope gates, events, reconciliation, Main mirror, health, bulk binding, open requests and startup callback |
| `ui/` | Four tabs, phone editor, cards, theme and view model |

`com/cyclone/connector/client/` contains the connector client and startup provider
base. `ArchitectureTest` checks that `forge` and `data` import no other Cloak
layers, `root` may import those two, `cyclone` may also import `root`, and `ui`
may import all lower layers.

| Tab | Current behavior |
| --- | --- |
| Phones | Built-in/custom templates; build, import, clone, edit and share |
| Identities | Seeded generation, fleet creation, values, rename, guarded deletion, binding and import/export |
| Profiles | Cyclone profiles, bindings, health pills, bulk binding and open requests |
| Health | Health Doctor v2 readiness/checks, redacted clipboard diagnostics, existing repair/reconnect actions and bundled compatibility coverage |

Editing a phone does not change the snapshot already stored in an identity.

## Data and synchronization

The profile contract is `schema/cloak-profile.schema.json`, `schema_version: "0.2"`.
Python `forge/cloak_forge/` and Kotlin `forge/ProfileValidator.kt` implement
coherence checks. They consume `catalog/phones.json` and cases in `forge/tests/vectors/`.

Identities use app-private `files/profiles/`; custom phones use `files/phones/`.
`JsonDirectory` uses temporary files and atomic renames and skips unreadable
entries. Bindings use `no_backup/cloak-bindings-v1.json` (binding-store schema
version 2, separate from the profile schema and module index versions).

A binding's stable identity is Cyclone profile id plus package. Reconciliation
updates its Android user number from Cyclone's registry. Main binds apps across
ready profiles; a profile's Cloak binds only its own apps. Main is not a binding
target. Sync checks approval/scopes, writes changed connector tuples, clears stale
tuples and reports health. Snapshot absence, trashed/unready profiles, missing
app lists/packages and duplicate ties retain local choices and warn. Deferred
bindings are excluded from remote cleanup; deletion requires a removal event
confirmed by the current snapshot. See [RECONCILIATION-TESTS.md](RECONCILIATION-TESTS.md).
Event sequence numbers deduplicate wakes. Open
requests are gated and are not retried blindly. `CloakStartupProvider` resolves
enabled bindings for the pre-launch callback.

Each install publishes its own root-owned share. The assembled tree merges all
shares with Main taking precedence. Publication now holds a process-safe kernel
lock across migration, share replacement, pruning and merge; interrupted swaps
restore the previous complete tree before another merge. Each invocation owns
its private staging snapshot. Secondary installs mirror Main's bindings
through root reads. [STATE_LAYOUT.md](STATE_LAYOUT.md) defines permissions, keys,
index metadata, migration and publication.

## Module and diagnostics

`android/module` builds a Magisk/Zygisk module for `arm64-v8a` and `armeabi-v7a`.
`cloak.cpp` requests the profile from the root companion before specialization,
then applies Build fields, property interception and locale/timezone after
specialization. The embedded Pine library and
`src/hook/java/dev/cyclone/cloak/hook/HookBridge.java` implement Java callbacks for
telephony, Android ID, Wi-Fi/Bluetooth addresses, serial, WebView user agent,
MediaDRM device id, advertising ID and guarded GSF queries. Stored identifiers
also appear under `cloak.*` properties; storage does not prove every consumer
API is intercepted. Callbacks remain available during the app process lifetime;
the code does not implement the old claimed unload posture. Unbound apps do not
receive a profile.

Root Doctor checks root access, Magisk/Zygisk, module version/architecture and
publication. The app embeds the matching module ZIP for explicit offline repair.
An installed module update requires a reboot and another check.

Publishing `pif.json` does not establish a Play Integrity verdict. Automatic
attestation checks, keybox management, burned-fingerprint rotation, egress/account
tracking and automatic reseeding on app-data clear are not implemented in this
release. Device verification and remaining compatibility surfaces are recorded
in [CURRENT-STATE.md](CURRENT-STATE.md) and [COMPAT_SURFACE.md](COMPAT_SURFACE.md).
