# Handoff: Glass integration for Cloak device identities

**Audience:** the Glass (PC dashboard) agent.
**Scope:** read-only integration. How identity data reaches Cyclone, the exact
payload shape, and rendering rules. Nothing here requires new Cloak-side
surfaces; everything described is shipped and on the phone.

## 1. What this integration is

The Cyclone Cloak companion keeps one **device identity profile** per bound
app. Each Cyclone profile (and each Android user) gets its own identity, and
every identity is unique across the fleet. Glass should surface these
identities on its overview so an operator sees, at a glance, which device
identity serves which Cyclone profile and app.

## 2. How the data reaches Cyclone

```
Cloak companion (phone)              Cyclone (phone)                Glass (PC)
-----------------------              ---------------                ----------
forge identity profile               stores per-app config          reads `profiles`
  profile.json  (private,            via the connector              listing; per app
  identifiers stay on device)        setConfig call                 config object it
        |                                  |                          renders one
        +--- bind app -------------------> |                          identity card
                                           |                          per profile
                                           v
                                    config payload (section 3)
                                    joins on:
                                      profileId + androidUserId
                                      + packageName
```

- Cloak writes the config through the existing Cyclone connector
  (`setConfig(profileId, androidUserId, packageName, payload)`).
- Cyclone stores it per `(profileId, androidUserId, packageName)`.
- The Cyclone `profiles` listing already exposes each profile's config
  (`ext`) to approved connectors. Glass reads the same listing; it never
  touches Cloak stores or `/data/adb` directly.

## 3. Payload spec (identityVersion 1)

The config payload written for each bound app:

```json
{
  "identityVersion": 1,
  "cloakProfileId": "a15ece73-55a2-5b1d-9b66-c695a95776b7",
  "boundAt": 1792000000000,
  "name": "Vault 01",
  "manufacturer": "Google",
  "model": "Pixel 7",
  "androidRelease": "13",
  "sdkInt": 33
}
```

Rules:

- `identityVersion` tags the schema. Treat unknown fields as reserved; treat
  missing display fields as "unknown".
- Older payloads (pre-0.8.0-alpha.2) contain only `cloakProfileId`. Glass must
  render those as "identity details unavailable" rather than failing.
- `name` is the human label of the identity (fleet names look like
  `Vault 01`); display it as the card title.
- The payload intentionally carries **no** hardware identifiers. IMEI, serial,
  MACs, Widevine id, Android ID and the UA live only in the on-device
  `profile.json`. Do not expect them here, and do not scrape for them.

## 4. Vocabularies Glass may see

Binding state (per app, from the companion's publish cycle): `pending`,
`ready`, `disabled`, `missing`, or a short reason string such as
`root approval needed` or `reboot required`. The same values appear in the
companion's binding list; `ready` is the healthy state.

Identity health (from the profile itself): `new`, `ready`, `degraded`,
`failed`. Display only; not part of the config payload.

## 5. Rendering rules for the overview

1. One identity card per Cyclone profile. A profile's apps can share one
   identity (binding is per app, but fleet flows assign one identity per
   profile); when the payloads disagree, show the most common one and note
   the count.
2. Show: manufacturer + model as the device line, `Android <androidRelease>`
   as the OS line, `name` as the label.
3. Badge states: `ready` is neutral/positive, `pending`/`missing` are
   attention states, anything else is an error state with the reason text.
4. Unbound profiles (no config payload) render as "no identity bound" with a
   neutral badge. This is a normal state, not an error.
5. Sorting: group by Cyclone profile, then by Android user id.

## 6. Privacy and safety rules

- Glass must not persist identifiers off-device. The payload above contains
  none by design; keep it that way.
- Never write to Cloak stores. The integration is read-only over the Cyclone
  listing.
- If a needed field is missing, ask for a payload extension in a new
  `identityVersion`; do not invent fallback lookups against device storage.

## 7. Test vectors

Forge parity vectors live in `android/app/src/test/java/dev/cyclone/cloak/CloakForgeTest.kt`.
A minimal display fixture Glass can use in tests:

```json
{
  "identityVersion": 1,
  "cloakProfileId": "a15ece73-55a2-5b1d-9b66-c695a95776b7",
  "boundAt": 1792000000000,
  "name": "Vault 01",
  "manufacturer": "Google",
  "model": "Pixel 7",
  "androidRelease": "13",
  "sdkInt": 33
}
```

## 8. Out of scope for this handoff

- Live binding-state streaming to Glass (candidate for a later, separately
  specified channel).
- Any write path from Glass into Cloak.
- The module's on-device behavior; it is unchanged and requires no Glass
  awareness.
