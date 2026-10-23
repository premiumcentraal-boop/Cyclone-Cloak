# Cyclone connector handoff: complete profile app inventory

## Goal

Make the profile app list complete and stable for companion tools.

Today a companion connector can bind only to apps that Cyclone exposes as
ordinary profile apps. Some built-in Essentials-style apps are installed with
the profile but do not appear in that list. That creates an inventory gap: a
companion tool cannot address those apps with the same profile-scoped identity
used by every other app.

## What to fix

1. Audit how the profile app inventory is built.
   - Find where Essentials packages and other bundled apps are filtered out.
   - Determine whether they are hidden by package type, installer source, user
     visibility, profile role, or an internal allowlist.
   - Preserve the reason in code comments or diagnostics; do not silently hide
     apps that are actually present in the profile.

2. Expose one canonical app record for every app visible to that profile.
   - Keep the existing stable fields: package name, label, user id, install
     state and any existing Cyclone profile association.
   - Mark bundled/system-managed apps clearly enough that companion tools can
     distinguish them from ordinary user-installed apps.
   - Do not invent apps that are not actually installed in the profile.

3. Keep connector compatibility.
   - Existing consumers must continue working.
   - If the contract changes, use a minor version bump.
   - Do not expose private Cyclone internals, user data, or package contents.

4. Make Android user handling explicit.
   - A bundled app in a non-zero Android user must remain associated with that
     user.
   - A package present in several users is several inventory records.

## Deliverables

- A complete connector-side inventory including Essentials-style apps.
- Contract/schema updates if required.
- Unit tests for ordinary apps, bundled apps, missing apps and multi-user cases.
- A short report naming the filter that caused the omission.

Do not change companion behavior or add new UI.
