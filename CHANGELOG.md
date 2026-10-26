# Changelog

## v1.1.0-alpha.3 — home, identities and search

- Home has quick actions: New profile, New identity, Repair (runs the root check and repair) and Refresh. The two "New"
  actions open their screen with the matching dialog already up.
- Phones are merged into Identities. Phone profiles, built-in and your own, sit on the Identities page, and the
  separate Phones tab is gone. The tabs are Home, Identities, Profiles and Health.
- Profiles has one search field. As you type it filters Cyclone profiles, spaces and identities; tapping a matching
  identity opens the binding flow for it.
- New spaces include Cyclone Cloak, and Cyclone when it is installed, as essentials. They are shown locked in the
  create dialog and count toward the app limit.
- Creating a space or identity does not remove or rewrite Main's identities, phones or bindings. Main's phone list is
  the full list. A space's own Cloak does not see that list.

Not verified on a phone. The layout is checked only by the Robolectric screen tests.

## v1.1.0-alpha.2 — space binding

- Creating a space now leads straight into binding: the binding flow opens with the space's apps and its own device
  profile preselected. Resuming an unfinished space does the same.
- Spaces are binding targets in the binding flow, and a ready space has a "Bind apps" action on its card.
- Binding a space publishes Cloak's state only. It does not use the Cyclone connector, so it works without Cyclone.
- The spaces card shows how many apps are bound to each space.

## v1.1.0-alpha.1 — spaces

- Added spaces: Cloak can now create its own managed Android user, seed an identity for it through the existing
  forge, install selected apps into it, open an app inside it, and remove it. Creation and removal run only from
  Main, need root, and reuse the existing Magisk session. Spaces use the `Cloak_<16 hex>` id shape. Cyclone is not
  needed for them, and the connector contract, scopes and Cyclone profiles are unchanged.
- Removing a space unbinds its apps and republishes before the Android user is removed, so no published binding can
  point at a missing user. A failed run keeps the record and can be retried without creating a second user.
- Space bindings are kept out of Cyclone reconciliation and out of the "missing Cyclone profile" health count.
- Not yet verified on a phone. Binding an app to a space's identity is not wired into the binding flow yet.

## v1.0.0-rc.1 — unreleased

Changes since `v0.11.0-alpha.28`:

- Added a Shamiko-style denylist opt-in through `cloak.denylist_enabled`, using
  the existing root companion to request Magisk denylist configuration. This
  does not establish that Shamiko is installed or that isolation was measured.
- Added the optional per-profile `pif` integrity configuration section and its
  runtime-envelope projection. Declared configuration is not an authenticated
  integrity response or proof of the physical boot state.
- Added three diagnostics self-visibility readers: `masked.package_list`,
  `masked.maps` and `masked.services`. RC device results are pending; restricted
  visibility, unavailable evidence and collection errors remain limitations.
- Removed a stale debug log line from `ProbeReportPolicy`.
- Updated coverage-ledger wording and documented the distinction between
  implemented capability and artifact-specific physical evidence. Historical
  measurements are not automatically promoted by a code change.
- Set matching app/module metadata to `1.0.0-rc.1` / code `60`.

Local verification: 784 app, 80 module, 148 standard probe, 148 diagnostics probe
and 192 schema tests passed. Debug/release APK builds and module packaging passed.
The forge command stopped with four collection errors because `cryptography` and
`jsonschema` were missing from its environment. The release gate remains blocked;
no RC tag, publication or RC physical smoke test has occurred. No platform verdict
or broader device compatibility is claimed.
