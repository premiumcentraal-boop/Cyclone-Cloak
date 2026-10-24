# Handoff: give Cyclone Cloak its own profile manager
## Update on top of v1.0.0-rc.2

Read the current tree before starting. This is an architecture change, not a feature patch.

## The goal in plain terms

Right now Cloak hangs off a Cyclone profile: Cyclone owns the profile, Cloak attaches behaviour to
it. We want Cloak to be able to stand on its own, so a person can create a profile inside Cloak and
get a separate working space with its own device data, without first going to Cyclone and making a
profile there.

Cyclone already does this for its own profiles. Reproduce the same mechanism inside Cloak rather
than inventing a new one.

## What decides the whole design

One hard constraint drives everything, so check it first:

`data/CloakBindingStore.kt` validates a profile id as `^Cyclone_[a-f0-9]{16}$`, and bindings are
keyed by `profileId + androidUserId + packageName`. Cloak cannot currently own an id.

Pick one of these and say why in the change description:

- **Keep the prefix and mint ids in that same shape inside Cloak.** Smallest change, everything
  downstream keeps working, but the `Cyclone_` prefix then means "this shape" rather than "made by
  Cyclone".
- Widen to a second shape, e.g. a `Cloak_` prefix alongside the existing one. Cleaner meaning, more
  places to touch, and every existing binding must stay readable.

Either way: existing bindings and existing Cyclone profiles must keep working. No migration that
breaks a saved profile.

## What Cloak already has

- An Android-user switch path in `root/PrivilegedSession.kt`, which is how a profile gets its own
  user today.
- `CycloneApi` / `CycloneBridge` / `ConnectorHandshake` for talking to Cyclone.
- `CloakStore` and `PhoneStore` for local records, `BatchIdentityWorkshop` for bulk creation, and a
  posture and coverage surface.

So the pieces are largely present. What is missing is that they assume the profile came from
elsewhere.

## Scope

- A profile manager inside Cloak: create, list, rename, delete, with its own storage record.
- Creating a profile must make its Android user, seed its identity from the existing forge, and
  leave it ready to bind apps.
- Bindings resolve against local records first. When a profile also exists in Cyclone, keep the
  current behaviour and do not regress it.
- A switch action per profile that reuses the existing privileged path.
- Delete must go through the same user-removal care the current flow uses, and must not strand a
  binding.

Out of scope: new identity fields, catalog work, integrity verdicts, any change to the runtime hook
layer, any new permission.

## Gates

- Existing tests green, plus focused tests for id minting, local binding resolution, delete with an
  attached binding, and a mixed case where one profile is local and one comes from Cyclone.
- Existing rc.2 evidence stays valid; the module is untouched, so no new module install is implied.
- Device proof is separate. Write the plan so a single profile create/switch/delete round trip can be
  measured later without rework.

## House style

Match the existing UI and storage patterns, keep cards at the existing radius, and reuse
`CloakDesign`/`CloakTheme` rather than introducing a second visual language. Keep the code paths
small and obvious; someone has to audit them.

Record the id-shape decision, the touched files, and what you verified. Do not claim device
behaviour you did not measure.
