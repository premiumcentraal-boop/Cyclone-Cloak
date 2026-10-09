# Cyclone Cloak 0.9.0-alpha.1

Works with Cyclone's profiles (Cyclone 5.0.0-alpha.122, connector contract `cyclone.connector/1.3`).

- **Approved once, everywhere.** Cloak declares the `cyclone-cloak` connector for contract 1.3 with
  the scopes for bindings, events, root status and open requests. It says hello on start and after
  every profile switch, so an approval Cyclone carries into a profile is picked up right away. When
  it isn't approved it shows one line telling you where to approve it.
- **Health reaches Cyclone's pill.** Every bound app's state is reported (`ready`, `degraded`,
  `failed`), so Cyclone shows Rooted, Rooted · check or Rooted · not working instead of always
  Rooted. Turning a binding off or removing it now clears it in Cyclone (Native).
- **Bindings follow their profile.** Bindings are keyed by Cyclone profile id. A profile restored
  under a new Android user number keeps its bindings, and they are republished for the module; a
  permanently deleted profile is forgotten. A profile without a user number is never bound as user 0.
- **Every profile's Cloak can publish safely.** The root state is now assembled from one share per
  Cloak install, with Main's winning. Opening Cloak or Root Doctor inside a profile no longer wipes
  the bindings Cloak in Main made for the other profiles. The first publish keeps the existing
  state.
- **Cloak inside a profile shows Main's bindings** for that profile and writes them to that
  profile's Cyclone, so its Profiles page shows the pill too.
- **Cloak's own pill** per profile (Rooted ✓ / Rooted ! with the reason / Native), using Cyclone's
  `root.status.v1` root facts.
- **Open in Cyclone**: ask Cyclone to open another profile (or Main, from a profile). You answer on
  Cyclone's own screen; Cloak never retries.
- **Events** are pulled on every wake and on start, de-duplicated by `seq`, and reconciled.

Update both the app and the module (Root Doctor → Check & repair, then restart). Not yet run on a
physical phone: see docs/CYCLONE_PROFILES_REPORT.md.
