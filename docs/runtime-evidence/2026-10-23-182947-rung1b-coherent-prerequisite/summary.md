# Rung 1b coherent fixture — missing real source facts

Result: **STOPPED** at the fixture-authoring prerequisite. No fixture edit, build, installation, capture, user switch, module action, reboot, permission change, commit, push, tag or release occurred. Existing evidence and the loaded module were left untouched.

## Exact evidence mismatch

The accepted authoring export from captured_at_ms **1792771610372**, SDK **36**, contains exactly seven reader IDs:

- compute.gpu
- memory.free_storage_gib
- packages.installed
- sensors.inventory
- webview.provider
- webview.version
- webview.user_agent — **{ "error": "NullPointerException" }**

It contains **no default-UA string** and no raw Build, identifiers, SIM/operator, display or locale fields. The authoringSnapshot source also has no default-UA reader. Its sole UA reader constructs a concrete WebView instance and failed. This corrects the continuation prompt's factual premise that a default UA was already captured at authoring time.

The prior bound user-0/A report has envelope.webview.defaultUa **OBSERVED** with actual_sha256 **eddc7054d711a56d34f362068209b0223ed8ed925ba9f893d56107fd873ada32**, but no raw UA. A one-way digest cannot supply the string. The prior bound user-17/C default-UA row is **MISSING / PROFILE_SURFACE_UNAVAILABLE**, with no actual digest. Neither record supplies an unbound host default-UA string.

The legacy reports are redacted; Build rows retain identity/status/reason rather than raw host values. Copying synthetic fixture A/C values or deriving Build strings from a catalogue would not constitute an actual retained host read. The successful host package entry android version_name=16 and authoring SDK=36 do not supply the missing fingerprint/build identifiers, telephony, display, locale or UA.

## Why no partial rewrite was made

Changing only release/SDK while retaining the old Android-13 fingerprint and synthetic base facts would recreate the incoherence this continuation is meant to remove. Fabricating a Chrome-155 UA would not be an actual WebSettings.getDefaultUserAgent read. Removing the WebView consistency check, guessing real host identifiers, or reverse-engineering a string from its digest would violate the evidence rules.

The prior enriched fixture and its two known coherence errors remain unchanged. No new readiness promotion, device result or final measured-count claim is made. No further fix/retest cycle was attempted because the required source facts are missing.

## Preserved artifacts

- authoring-source.json: exact accepted raw export, unchanged.
- prerequisite-audit.json: reader IDs, absence checks, commit and SHA-256 values.
- prior-report-availability.json: exact retained default-UA rows and example redacted legacy row.
- profile-c-unchanged.json: existing fixture, not rewritten.
- RealityProbe.java: inspected authoring reader source.
- result.json: stop phase and zero-action ledger.

No tests/builds were run; there is no implementation change to test. Prior measured counts are retained in the previous evidence directory, not relabelled as a new run.

## Open questions

A real, explicitly scoped **unbound host authoring capture** must retain WebSettings.getDefaultUserAgent plus the required Build/version/fingerprint, caller-visible identifiers where available, SIM/operator, display and locale fields with reader provenance. That export is absent from the current supplied evidence. A digest-only prior capture cannot replace it. The concrete-instance UA remains unavailable with the exact authoring error **NullPointerException**; no underlying exception cause has been established.

No platform verdict, certification, coverage claim or VERIFIED/APPLIED promotion.
