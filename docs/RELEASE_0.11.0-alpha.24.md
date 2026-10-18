# Cyclone Cloak 0.11.0-alpha.24

Prerelease candidate; app/module version code 48.

- Hardened display-mode construction: independent optional refresh/HDR metadata,
  exact reviewed constructor selection from the framework list, and a simpler
  constructor fallback for any rich-path failure. Host mode is retained only when
  no valid profile-backed tuple can be constructed.
- Active and supported display modes use the same authoritative base-profile tuple.
- Linked diagnostics retain hashes and add non-sensitive active/supported screen
  tuples and Display.getRefreshRate() for direct failure analysis.
- Configure the Java hook engine with the actual host API level and ART debug
  mode captured before profile rewriting. Profile SDK values and concise logging
  no longer select the engine's runtime configuration.
- Diagnostics lifecycle failures distinguish a fresh, matching probe crash from
  a report timeout, ignoring stale exits and unrelated processes.
- No profile schema, bindings, storage layout, connector, publication or permission
  changes. No networking or platform verdict.

Physical validation: PENDING. Alpha.23 failed at 90 Hz requested versus 60 Hz
observed and absent supported-mode membership. A temporary diagnostic identified
NoSuchMethodException on exact lookup despite signatures being enumerable; that
instrumentation was removed before this candidate. Publication is gated on three
successful lifecycle cycles for users 0 and 17, with unchanged production and
permission audits and complete cleanup. Local tests are not physical proof.

Upgrade uses the existing APK signer and matching Magisk module. A restart is
required to load the updated module. Existing identities and bindings are retained.
