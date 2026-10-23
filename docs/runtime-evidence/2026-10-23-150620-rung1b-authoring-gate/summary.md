# Rung 1b — fixture-authoring prerequisite stop

Result: **STOPPED** at fixture authoring. No build, installation, capture, user switch, permission grant, module action, reboot, commit, push, tag or release occurred. The read-only preflight found users 0 and 17 RUNNING_UNLOCKED, with user 0 active. Prior evidence is preserved.

## Exact blocker

The existing runtime-envelope readers execute the requested APIs but deliberately discard their raw results after hashing. `RealityReport.row` returns only expected/actual SHA-256 and classification. `RuntimeEnvelopeReport.report` strictly allowlists digest-only row fields and explicitly rejects `actual`, `expected`, `value`, and `raw`. `ProbeActivity` only calls this digest-only collector. Inspection found no existing authoring export command that returns these reader results.

Consequently the requested GPU string, exact available bytes, sensor descriptors, caller-visible package versions, and WebView provider/version/concrete-instance UA cannot be retrieved from the existing report. Hashes are not reversible facts. Reconstructing catalog values or substituting shell inventories would not satisfy the instruction that every authored expectation originate from the existing API reader at authoring time. No invented expectation, provenance entry, or runtime-readiness promotion was added. Fixture C is byte-identical to the copied preflight fixture.

The handoff's first fixture-authoring gate has therefore stopped the run before device mutation. This is an unavailable authoring interface, **not** a measured runtime or device failure. No new pass/determinism/UA-cause claim is made.

## Artifacts and checks

`preflight.json` records commit, current source/APK/ZIP hashes, installed ARM64 module hash and metadata, device reachability, user states and diagnostics package presence. Source copies preserve the inspected reader/report code; `profile-c-before.json` preserves the unchanged fixture. `commands.jsonl` records the read-only commands. No test/build suite ran because no implementation changed. The ZIP digest is recorded separately in `evidence-bundle.sha256`.

## Open questions

An explicit authoring-only path is needed to retain the allowlisted raw API results in app-private storage together with reader ID, time and host/profile state, without weakening the normal digest-only report contract. That capability is not present in the existing reader/export flow inspected here. It should be specified before using raw captures as fixture facts.

The concrete-instance WebView UA remains unavailable from the prior Rung 1a captures. This run did not launch a capture, so there is **no new verbatim exception cause** to quote. The current reader-failure change's stack logging checks the exact class name `android.app.AndroidRuntimeException`; Android's standard public class is `android.util.AndroidRuntimeException`. This is a source-level discrepancy requiring review, not an established cause of the earlier failure. No UA code or classification fix was attempted.

No platform verdict, coverage claim, VERIFIED state or broad compatibility claim is made.
