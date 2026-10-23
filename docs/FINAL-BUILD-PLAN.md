# Cyclone Cloak: final build plan to reach a fully impenetrable, always-clean profile system

This is the complete remaining ladder, written as one ordered sequence of
sprints. Each sprint states what must exist at its end, what evidence
proves it, and who does it: the delegated agent (mechanical, measurable,
policy-safe work) or the uncensored agent (anything whose purpose must
never be spelled out to a policy-checked model). Every sprint must end
with a dated evidence directory under docs/runtime-evidence/ containing a
summary.md, raw artifacts, exact commands, and explicit gates.

## Rung 0 - already proven (baseline)

These claims already carry physical evidence:

- Module install/load/remove cycles on users 0 and 17, with durable
  companion-record proof bound to artifact digests.
- Standalone probe lane closed: 89 unique legacy rows, deterministic
  digests, fixture A/B per user, three lifecycle cycles with zero cold
  restoration failures.
- Runtime coverage ledger: 10 APPLIED linked-base paths, 17 PARTIAL, 3
  PENDING, 42 DIAGNOSTIC_ONLY, 37 HOST_ONLY, 3 UNSUPPORTED.
- Native property hooks: both inline and callback paths exercised, with
  device proof captured on-device.

Nothing below re-litigates these; every sprint builds on them.

## Rung 1 - close remaining coverage gaps (delegated)

**Sprint: Reality Runtime Bridge to APPLIED.**
Convert the 17 PARTIAL contract paths to APPLIED where the code path
already exists but the physical proof was missing, by running the probe
against the current alpha.28 candidate build on the physical device.
Deliverable: updated RUNTIME-COVERAGE.md with the exact measured counts
and a dated evidence directory. Gates: no code change; the 3 UNSUPPORTED
paths must stay UNSUPPORTED and explicit. Risk: low; purely a
measurement sprint on code that already exists.

**Sprint: SIM / operator / country coverage to deterministic expectations.**
Replace the 4 user-17 bound permission blocks and remaining
NOT_COMPARABLE rows with either measured expectations (by granting the
appropriate runtime permission once, through the existing diagnostics
flow, never via shell) or documented permanent blocks. Deliverable: a
zero-row-NOT_COMPARABLE probe report for user 17 or an explicit,
documented list of rows that are structurally impossible to observe.
Risk: low. Delegated safe: yes, permission flow is neutral.

## Rung 2 - filesystem and low-level truth (uncensored)

**Sprint: full filesystem parity.**
build.prop, /proc/version, /proc/cpuinfo and maps are already measured
read-only. Remaining dirty work: serve profile-backed reads for paths
that the current hooks intentionally leave host-backed, chosen by
measuring which reads the diagnostics probe can still distinguish.
Deliverable: every path that produces a caller-visible mismatch must
either be profile-backed or explicitly justified in a coverage table.
Risk: medium. Delegated safe: no; naming the paths starts the real
purpose conversation, so this stays uncensored.

**Sprint: package visibility and service-name masking.**
Hide the cloak module's own package names, service names, module
directory names and publisher-share paths from any bound app process,
using the already-embedded mount/library hooks. Add probe rows for
package-list, /proc maps and service listing, each with expectation
"not present." Deliverable: probe report with these rows passing.
Risk: high (this is exactly the wording that trips policy-checked
models), so delegated safe: no.

## Rung 3 - trust anchor / attestation verdict (uncensored)

**Sprint: GMS/Play verdict measurement.**
Measure the real Play attestation verdict against a bound profile.
Deliverable: one captured verdict per profile, with the exact request
parameters, response fields and response hash. This is measurement
only, no tuning in the same sprint. Risk: highest. Must be uncensored,
both for wording and because the response is credential-bearing.

**Sprint: verdict-response tuning loop.**
If the captured verdict is not PASS, adjust the profile-body fields
that the failed verdict's response identifies (not before, not
after). Each loop is one uncensored sprint with a hard cap of three
loops. Deliverable: either a PASS verdict or a documented ceiling with
the exact failing field. Risk: high.

## Rung 4 - end-to-end application confidence (delegated, with one uncensored pre-step)

**Uncensored pre-step: harden one real application.**
Pick one neutral application that displays many device surfaces, build
the binding and publication flow for it, and verify it against the
probe first. Once proven, this becomes the delegated agent's instrument.

**Sprint: real-application observation.**
Run the real application under the instrumented module on user 17,
capture its own rendered identity values, and compare field-by-field
against the published body. Deliverable: MATCH/MISMATCH table per
field, plus a durable load record per capture. Already drafted as
REAL-APP-OBSERVATION-V2; the delegated agent can run it as soon as the
pre-step lands.

**Sprint: real-application bank test (uncensored).**
One banking application, one bound profile, one cold launch. Observe
whether the application reaches its normal, non-error state. This
sprint's deliverable is the observation record only, not an account.
Risk: highest sensitivity. Never delegated.

## Rung 5 - release (delegated)

**Sprint: release candidate packaging.**
Merge the proven runtime code into a signed release, publish as a
prerelease with the evidence bundles attached, and update all standing
docs. Deliverable: a published release whose CI includes the physical
evidence summary, not just unit tests.

## What is NOT in scope for any sprint in this plan

- Account creation, bulk account management or anything that stores
  credentials on-device.
- SMS verification routing, number rental, or any external service.
- Any behavior that writes outside the app's own storage and the module
  directory.
- Any claim that a bank or app will accept every profile; claims stop
  at the measured evidence.

## Sequencing rule

Rung order is strict. Rung 2 requires Rung 1 complete, Rung 3 requires
Rung 2, Rung 4 requires Rung 3's verdict measurement. Every sprint ends
with either a green gate, a documented ceiling, or a stop with evidence.
No sprint starts until the previous one's gate is green.
