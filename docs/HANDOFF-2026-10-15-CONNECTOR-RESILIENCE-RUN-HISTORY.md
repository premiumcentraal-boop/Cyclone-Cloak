# Connector resilience and run-history handoff

Date: 2026-10-15

## Starting point

The latest connector and binding updates are in place. I also confirmed that
the helper package's notification channel and existing authorization state are
healthy for user 17. That means the remaining pain is not a missing permission;
it is reconnect timing, status clarity, and how much useful evidence survives a
failed or confusing run.

## Objective

Make the Cyclone-to-Cloak connection feel immediate and self-recovering, and
make every binding run leave behind a clear, exportable record. The user should
not have to guess whether a run is still active, whether it needs another tap,
or what happened if it stops midway.

## Delegated scope

1. **Connection lifecycle**
   - Reuse and harden the existing `ConnectionLifecycle`, `ReconnectCoordinator`,
     and `CycloneBridge` paths rather than adding a second connection manager.
   - Maintain one explicit connection state: connected, retrying, waiting for
     user action, or failed with a readable reason.
   - On retry, avoid repeating any confirmation step that already succeeded.
   - Make the UI show one clear "what to do next" message, not a wall of status
     text.

2. **Binding run history**
   - Extend the existing `BindingRunJournal` and `BindingRunBundle` instead of
     creating a new storage system.
   - Persist one record per binding attempt, including start and end time,
     selected Cyclone profile, Android user, selected apps, device profile,
     final state, and first failure reason.
   - Keep the history local and easy to inspect from the UI.
   - Make each record easy to export as a small, privacy-conscious bundle that
     includes the existing connector handshake, reconciliation summary, and
     diagnostics metadata.

3. **Failure feedback**
   - If a run stops because an approval or confirmation window expired, say that
     directly and offer one obvious retry action.
   - Preserve the partial state so the retry path does not need to recreate
     everything from scratch.
   - Do not claim success unless the final publication step also succeeded.

4. **Docs and tests**
   - Update the relevant docs to describe the new run-history behavior and the
     exported bundle layout.
   - Add focused unit tests for:
     - a successful run,
     - a retry after a partially completed run,
     - a run stopped by an expired confirmation,
     - a run stopped by a connector failure,
     - the export bundle contents.

## Non-goals

- No changes to engine internals.
- No new permissions.
- No platform verification claims.
- No new UI surface beyond a history view and an export action.
- No new storage format outside the existing local app-private stores.

## Suggested next step

Start from the existing connector lifecycle and binding-run plumbing, then
improve resilience and observability in place. Keep the wording neutral and
operator-focused: "connection", "reconnect", "run history", and "diagnostics
bundle" are enough to describe the work.
