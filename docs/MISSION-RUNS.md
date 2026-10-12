# Cyclone Cloak Mission Runs

Use this file with `AGENT-BRIEF.md`. Give the assigned agent one run at a time. Each prompt is intentionally scoped so it can be completed without broader product context.

## Run order

1. Run 01 — Profile Confidence Engine v3
2. Run 02 — One-Click Valid Profile Generator
3. Run 03 — Fleet Uniqueness Manager
4. Run 04 — Device Catalog Expansion v2
5. Run 05 — Readiness Doctor v3
6. Run 06 — Sync and Publication Hardening
7. Run 07 — Guided Binding Flow
8. Run 08 — Backup, Export, and Restore
9. Run 09 — Release Candidate Integration

The listed order is the recommended sequence. Runs 04 and 05 can run in parallel only if they touch separate files and both start from the same clean commit.

---

## Run 01 — Profile Confidence Engine v3

### Prompt

```text
Read AGENT-BRIEF.md first, then work in C:\Users\Agent\Cyclone\cloak-repo.

Upgrade the existing device-profile validation system into a clearer coherence engine. Do not change the profile storage schema, connector contract, or runtime module.

Implement:

1. A stable validation result model with severity levels: info, warning, error.
2. A stable machine-readable reason code for each finding.
3. A user-readable explanation for each finding.
4. Rule coverage for:
   - manufacturer/model relationships;
   - Android release and SDK/API-level consistency;
   - build fingerprint structure and consistency;
   - display size, density, and aspect-ratio sanity;
   - locale and timezone format validity;
   - identifier format validity;
   - carrier/network field sanity;
   - hardware feature consistency.
5. A deterministic summary that sorts findings by severity and field.

Keep existing valid profiles valid unless they contain a genuine coherence error. Do not remove fields or add migration requirements. Add unit tests for valid, warning-level, and invalid examples, including at least one test per new rule group. Ensure Kotlin and Python validation behavior remain aligned where the same data is shared.

Deliver the standard completion report and include the exact test commands run.
```

### Definition of done

- Validation findings are structured, explainable, and deterministic.
- Existing valid profiles do not regress.
- No schema migration is required.
- Android and Python tests pass.

---

## Run 02 — One-Click Valid Profile Generator

### Prompt

```text
Read AGENT-BRIEF.md first, then work in C:\Users\Agent\Cyclone\cloak-repo.

Add a one-click profile-generation flow using the existing phone catalog and validation engine.

Implement:

1. A “Generate profile” action on the Phones tab.
2. Template selection using the existing catalog.
3. Deterministic generation of all profile fields supported by the current schema.
4. A stable random seed for each generated profile.
5. Validation before saving.
6. If validation fails, show the findings and do not save the profile.
7. If validation passes, save the profile atomically using the existing storage layer.
8. Clear success and failure states.

Do not alter the connector contract, runtime module, or existing identity records. Do not add networking. Reuse the existing catalog, forge, validation, and storage components.

Add unit tests for successful generation, validation failure preventing save, repeated generation from the same seed producing the same values, and generation from different seeds producing distinct values.

Deliver the standard completion report and include the exact test commands run.
```

### Definition of done

- A user can generate a valid profile in one flow.
- Invalid generated profiles are never saved.
- Existing identity records remain untouched.
- Android and Python tests pass.

---

## Run 03 — Fleet Uniqueness Manager

### Prompt

```text
Read AGENT-BRIEF.md first, then work in C:\Users\Agent\Cyclone\cloak-repo.

Add local uniqueness checking for saved device-configuration profiles.

Implement:

1. A local uniqueness index for distinguishing fields such as Android ID, advertising ID, serial, DRM ID, Bluetooth identity, and Wi-Fi identity.
2. A check that runs when creating, importing, duplicating, or editing a profile.
3. A clear warning when a proposed value already exists in another saved profile.
4. An option to regenerate the conflicting value when the source field supports generation.
5. A diagnostics view showing duplicate values across saved profiles.

Do not delete or rewrite existing profiles during migration. Existing profiles may be flagged, but must remain readable. Do not send data off-device or alter the connector contract.

Add tests for no duplicates, one duplicate, multiple duplicates, regeneration resolving a conflict, and corrupted or missing index entries not breaking the library.

Deliver the standard completion report and include the exact test commands run.
```

### Definition of done

- Duplicate distinguishing values are visible before saving.
- Existing profiles remain readable.
- The uniqueness index is recoverable if missing or damaged.
- Android and Python tests pass.

---

## Run 04 — Device Catalog Expansion v2

### Prompt

```text
Read AGENT-BRIEF.md first, then work in C:\Users\Agent\Cyclone\cloak-repo.

Expand the shared phone catalog using the existing schema and validation rules.

Add at least eight additional coherent phone templates covering several device classes:

- compact;
- mainstream;
- large-display;
- midrange;
- older supported device;
- newer supported device.

For each template, include manufacturer, model, Android release, SDK/API level, display class, hardware feature group, camera class, carrier/telephony defaults, and locale/timezone defaults.

If the catalog format supports extension without breaking existing readers, add a `confidence` metadata field. Document source confidence using categories such as “public reference”, “approximated”, or “needs verification”.

Do not alter the runtime module or connector contract. Ensure every new template passes validation. Add tests that iterate the full catalog and validate each entry. Keep Kotlin and Python readers aligned.

Deliver the standard completion report and include the exact test commands run.
```

### Definition of done

- The catalog has at least eight additional valid templates.
- Every catalog entry passes validation.
- Confidence metadata is documented.
- Android and Python tests pass.

---

## Run 05 — Readiness Doctor v3

### Prompt

```text
Read AGENT-BRIEF.md first, then work in C:\Users\Agent\Cyclone\cloak-repo.

Improve the existing health/diagnostics screen into Readiness Doctor v3.

Implement:

1. One overall readiness verdict: ready, degraded, or failed.
2. Individual checks with pass, warning, or fail state.
3. User-readable reasons for every non-pass state.
4. Checks for companion/module version agreement, binding presence, binding enabled state, profile reference validity, local identity readability, publication state, synchronization state, and stale or conflicting local records.
5. A redacted “Copy diagnostics” action.

The copied report must not include seeds, full identifier values, private keys, tokens, or user content. Do not modify synchronization behavior, the runtime module, or the connector contract in this run.

Add unit tests for verdict calculation, redaction, and mixed-state summaries.

Deliver the standard completion report and include the exact test commands run.
```

### Definition of done

- The doctor explains why a binding is not ready.
- The copied report is safe to share and redacted.
- Existing health behavior is preserved.
- Android tests pass.

---

## Run 06 — Sync and Publication Hardening

### Prompt

```text
Read AGENT-BRIEF.md first, then work in C:\Users\Agent\Cyclone\cloak-repo.

Harden synchronization and publication without changing the public connector contract.

Implement:

1. A process-safe lock around publication.
2. Staging, commit, and rollback around the final publication swap.
3. Recovery from an interrupted publication.
4. Clear status reasons for locked, completed, failed, recovered, and stale-lock states.
5. Safer reconciliation when a profile’s Android user number changes, a profile is removed or trashed, an app package disappears, duplicate local records exist, or Cyclone returns a rate-limit or transient error.

Prefer preserving local records and reporting a warning over deleting data on ambiguous failures. Do not change binding identity from profile ID plus package name unless absolutely necessary. Do not introduce networking.

Add unit tests for concurrent publication, stale lock recovery, rollback, and reconciliation ambiguity.

Deliver the standard completion report and include the exact test commands run.
```

### Definition of done

- Publication is protected against concurrent writers.
- Failed swaps can recover the previous complete tree.
- Ambiguous reconciliation does not silently delete data.
- Android tests pass.

---

## Run 07 — Guided Binding Flow

### Prompt

```text
Read AGENT-BRIEF.md first, then work in C:\Users\Agent\Cyclone\cloak-repo.

Improve the binding workflow without changing the connector contract or synchronization engine.

Implement a guided flow with clear steps:

1. Choose Cyclone profile.
2. Choose app.
3. Choose an existing profile or generate one.
4. Review the summary.
5. Confirm binding.

Add inline validation before confirmation and warnings for:

- profile not ready;
- missing Android user assignment;
- app missing from the current app list;
- identity already bound elsewhere;
- duplicate distinguishing values.

Add a final review screen showing profile name, app package, Android user, and selected compatibility profile. Add clear success and retry paths if reconciliation reports an error.

Reuse the existing binding store, reconciler, and health models. Do not introduce a new storage format or new permissions. Add unit tests and UI-state tests for success, warning, blocked, and retry paths.

Deliver the standard completion report and include the exact test commands run.
```

### Definition of done

- The binding flow is clear and difficult to misuse.
- Warnings are actionable.
- Existing bindings and reconciliation behavior remain intact.
- Android tests pass.

---

## Run 08 — Backup, Export, and Restore

### Prompt

```text
Read AGENT-BRIEF.md first, then work in C:\Users\Agent\Cyclone\cloak-repo.

Add local export/import/backup support for saved device-configuration profiles.

Implement:

1. Export one profile.
2. Export the full profile library.
3. Import one profile.
4. Import a full library with user confirmation.
5. Duplicate-value detection during import.
6. Schema-version mismatch detection.
7. Atomic writes for all import and export operations.
8. A backup manifest containing schema version, app version, creation time, profile count, and an optional user-supplied label.

Do not include binding state, Cyclone identifiers, or module publication data unless the user explicitly selects an advanced full backup. Do not send data off-device or add networking/cloud storage. Imported profiles must pass validation before being saved.

Add tests for valid import, invalid import, partial import failure, duplicate detection, and schema mismatch.

Deliver the standard completion report and include the exact test commands run.
```

### Definition of done

- Export and import are local, atomic, and validated.
- Sensitive values are not included unless explicitly selected.
- Corrupt or mismatched files fail safely.
- Android and Python tests pass.

---

## Run 09 — Release Candidate Integration

### Prompt

```text
Read AGENT-BRIEF.md first, then work in C:\Users\Agent\Cyclone\cloak-repo.

Prepare a release candidate from the current development state.

Implement:

1. Update app and module versions consistently.
2. Confirm the release workflow produces both APK and module zip.
3. Update release notes with major improvements, diagnostics changes, reliability changes, migration notes, and known limitations.
4. Update user-facing documentation where behavior changed.
5. Confirm all tests pass.
6. Confirm no stale documentation claims unimplemented behavior.

Do not invent device-verification claims. Mark unverified physical-device checks as unverified. Do not change feature behavior in this run unless required to make the release candidate coherent. Do not publish until explicitly asked.

Deliver the standard completion report and include the exact test and build commands run.
```

### Definition of done

- Versions, release notes, docs, and builds agree.
- All local tests pass.
- Unverified physical-device behavior is clearly marked.
- No release is published without an explicit instruction.
