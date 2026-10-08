# Cyclone Cloak 0.8.0-alpha.2 (identity summaries for Glass)

A small, targeted patch release that makes the companion's config payloads
useful to dashboard consumers (Glass) without exposing anything sensitive.

- **Identity summaries in config.** Every bind now writes a compact,
  display-oriented identity object alongside the cloak profile id:
  name, manufacturer, model, Android release and SDK level, plus a version
  tag and timestamp. No hardware identifiers are included.
- **Fleet bind updated to match.** Bulk binding uses the same payload, so
  every bound app carries a dashboard-ready summary.
- **Read rules.** Summaries are additive config data; readers must tolerate
  unknown and missing fields, and older payloads may contain only the
  cloak profile id.

See docs/HANDOFF-GLASS-CLOAK-IDENTITIES.md for the integration contract.
No module behavior change; module zip rebuilt for version alignment.
