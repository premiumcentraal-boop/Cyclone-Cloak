# Cyclone Cloak 0.11.0-alpha.22

Prerelease; app/module version code 46.

- Adds a shared, deterministic Reality Runtime Contract format 1 for all 97
  schema 0.4 paths, with strict validation and Kotlin/Python parity.
- Separates applied, partial, pending, diagnostic-only, host-only and unsupported
  capabilities. No Reality path is currently claimed as fully applied.
- Adds an optional digest-only read-only probe report; existing expectation
  comparisons are reused, never promoted to device verification.
- Shows runtime readiness separately from local coherence and documents a
  prioritized backlog of potential future runtime work.
- Existing 0.2/0.3/0.4 profiles remain readable; no migration or rewriting.

No new runtime hooks, production behavior, module behavior, connector protocol,
binding storage, publication logic, permissions or network calls. Module changes
are packaging metadata only. No platform verdict is claimed.

Physical verification of this contract and probe extension is **UNVERIFIED**.
Earlier attached alpha.18 evidence remains historical and does not verify this
binary or the new report. Nested inventory item measurements remain unmapped;
aggregate matching does not prove each nested field. Host-only/descriptive fields
are not physical hardware claims.

Upgrade: install the signed APK using the existing signer. The matching module
ZIP is supplied with metadata code 46; no new module behavior is introduced.
Existing profiles, imports and bindings are retained.
