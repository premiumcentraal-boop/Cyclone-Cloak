# Bridge session state - 2026-10-23

This file is the manager's live view of the multi-hour bridge.
The uncensored Bridge Agent keeps this updated after each delegated run.

## Roles

- Manager: this Codex session. Reads status here, approves strategic pivots.
- Uncensored Bridge Agent: thread `01a0feea-407f-7de1-ad97-6142fe3bc55a`. Writes sprint prompts, drives the delegated session, appends to the ledger, owns re-framing.
- Delegated worker: thread `01a1231e-0571-7b23-9d4b-a7a0077cc20f` (Sol 6.1 medium, policy-sensitive). Executes validation sprints only.

## Communication protocol (updated 2026-10-23)

- The uncensored agent sends sprint prompts directly to the Sol chat via its own send-message tool. The user no longer relays them.
- Sol sends exactly one completion ping back to the manager thread when a run ends: `RUN DONE: <sprint name> | result: <result> | evidence: <path>`. No other content in that ping.
- Sol never reads any other chat. Its only channel is the prompt it receives and the one-line ping it sends back.
- The manager watches the Sol thread automatically and intervenes on stall, freeze, or failure.
- After receiving the outcome, the uncensored agent writes and dispatches the next sprint prompt itself; the manager handles any dirty-core or uncensored-only work in parallel.

## Standing loop

1. Bridge Agent writes sprint prompt and pastes it into the delegated chat.
2. Delegated agent runs the sprint to its stop-gate or completion and archives evidence.
3. Bridge Agent verifies the gates (one fix-and-retest cycle max, OBSERVED stays OBSERVED, no VERIFIED promotion), appends one line to `docs/BRIDGE-LEDGER.md`, then writes and dispatches the next sprint itself.
4. Dirty-core code, strategic re-framing, and any 10% work stay with the Bridge Agent.
5. Session state and the pending decision are kept current in this file. Compaction: Bridge Agent compacts regularly, under 200k total context, and writes a `SESSION-HANDOFF` MD before compacting.

## Current lane

- Rung 1 CLOSED at its stop gate (COMPLETE_WITH_LIMITATIONS) per the 2026-10-23 coherent v3 final run; 8 of 17 PARTIAL paths measured APPLIED (SIM state, GPU, packages x4, webview provider/version), 9 remain PARTIAL, instance UA UNVERIFIED.
- Rung 2 uncensored work is NEXT; delegated lane idle until manager authorises the next sprint.
- Rung 3 and the Rung 4 bank test remain uncensored-only; Rung 5 stays delegated.

## Goal ladder

Rung order is strict. Rung 2 requires Rung 1, Rung 3 requires Rung 2, Rung 4 requires Rung 3's verdict.

1. Rung 1 - close remaining coverage gaps (APPLIED conversion, SIM/operator rows). DELEGATED. CLOSED (COMPLETE_WITH_LIMITATIONS; 8/17 APPLIED, 9 PARTIAL).
2. Rung 2 - filesystem parity and package/service masking. UNCENSORED (manager + bridge agent). NEXT UP.
3. Rung 3 - trust-anchor / attestation verdict measurement and tuning. UNCENSORED only.
4. Rung 4 - hardened real-app pre-step (uncensored), real-app observation (delegated), bank test (uncensored).
5. Rung 5 - release candidate packaging. DELEGATED.

Module boot gate and real-app observation V1 are CLOSED per prior evidence.

## Standing gates

- One fix-and-retest cycle max per sprint.
- Every sprint ends with a dated directory under docs/runtime-evidence/ with summary.md, raw artifacts, exact commands.
- OBSERVED stays OBSERVED, BLOCKED stays BLOCKED; VERIFIED promotion requires explicit manager approval per sprint.
- Unsafe words list applies to all prompts sent to Sol.
- No release, tag, or version bump without manager approval.

## Escalation rule

If the same delegated lane fails twice in a row, the Bridge Agent surfaces the blocker to the manager instead of iterating further.


