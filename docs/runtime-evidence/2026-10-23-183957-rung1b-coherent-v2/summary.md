# Rung 1b coherent v2 — real export complete, UA contract stop

Result: **STOPPED** at full fixture coherence validation. The v2 authoring export now genuinely contains the missing host facts and its COMPLETE log was observed. The exact real WebView UA cannot currently satisfy the linked base-UA validator. No proposed fixture was applied, no comparison captures were run, and no status promotion was made.

## What was executed

The advertised v2 readers were not present in the inspected source: only the prior seven reader IDs were exported, while two extra top-level Build fields were stripped by the authoring allowlist. One narrowly scoped authoring fix added the required public Build/version, caller-visible identifiers, SIM/operator, display, locale and default-UA reads, plus allowlisted export retention. An existing native-property reader is reused for first API level and the radio-class property. No runtime/module hook or permission change. The export unit test now checks default-UA and SDK preservation while unrelated fields remain filtered.

Diagnostics build/test gate passed. Diagnostics APK SHA-256 **4dca724b770407a8b7ae081f94128f18696c076affabbbd326d64b7f37d4c980** installed only for users 0/17; both installed package-path hashes matched. One standard switch to user 17, one unbound cold authoring launch, PID **21053**. captured_at_ms **1792773858598**, captured_sdk_int **36**. The exact retained log contains:

```text
10-23 18:44:18.620 21053 21093 I CloakProbeAuthoring: authoring_export COMPLETE
```

The run-as external read again returned Permission denied. The specifically approved read-only root recovery retrieved the atomic file from /data/media/17/Android/data/dev.cyclone.cloak.probe.diag/files/standalone-evidence/authoring.json. Export SHA-256 **57c6fa87e5c2ada239117f6457f0545eb0050726c4a3967199662da9ccd4c3f0**. Authoring source, hash, request, user, PID, installed hashes, error/visibility and full bounded target-PID log retained.

The export has 38 reader entries, including Build release 16, SDK 36, Pixel 8/shiba, matching real fingerprint/build fields, two phone slots, Lebara operator 204/08, real 1080x2400/420dpi/60Hz display, en/NL and Europe/Amsterdam. Unavailable serial is an explicit error, never replaced with a generated identifier. The five package entries and 38 sensor descriptors remain unchanged from accepted prior evidence. No host identifier values are printed in this summary.

## Genuine default UA and validation evidence

The actual WebSettings.getDefaultUserAgent result is:

```text
Mozilla/5.0 (Linux; Android 16; Pixel 8 Build/CP1A.260505.005; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/155.0.8059.43 Mobile Safari/537.36
```

It is a WebView UA, not the Chrome-browser-only grammar expected by the existing base-UA validator.

First proposed host fixture: JSON schema passes, but forge reports DISPLAY_CLASS (the authoring export lacks screen_size_class) and REALITY_LINK for reality.webview.values.user_agent because no browser UA was observed and the synthetic base UA was removed. Warnings include UA_MISSING and PATCH_AFTER_BUILD from the real host's patch/build dates.

The **one evidence-only authoring retest** puts the **exact same unmodified real UA** into both linked UA fields and retains the previous size-class metadata solely to isolate this defect. It removes both REALITY_LINK and REALITY_WEBVIEW findings, but still produces:

- **UA_FORMAT**, field ua.value: **ua.value must be a Chrome-on-Android user agent string**.
- PATCH_AFTER_BUILD warning, using unmodified observed timestamps.

See fixture-validation.json, profile-c-proposed.json, exact-ua-alternative-not-applied.json and exact-ua-alternative-validation.json. Neither draft was applied. The original profile-c.json is byte-identical to profile-c-before.json. No stripping Version/4.0, rewriting model/UA tokens, inventing a browser UA, weakening a validator, or changing shared schema/contract was performed.

This is a local representational/validation blocker, **not a runtime mismatch**. The new fixture cannot honestly meet the requested full validation gate with the current mandatory UA linkage and browser-only syntax. Measurement stopped before rebuilding/installing a proposed fixture. All three requested comparison captures, determinism and APPLIED decisions are **NOT RUN**, not passes.

## Cleanup and preservation

Diagnostics globally uninstalled. Both users' package paths, CE/DE/external directories are absent. Standard switch returned to user **0**. No temporary overlay/binding/body was created in this continuation. Production before/after audited snapshots match exactly: companion APK, audited grants/stable AppOps, published state/shares and accessible binding/index stores. Module immutable files match before/after and the prior candidate manifest. No module install/removal, reboot, production APK install/launch, shell permission/AppOps writes, user creation/removal, release/tag/version bump, commit or push. Prior evidence preserved.

The validated loaded ARM64 remains d7dd105446a229f981e5cdd1aed862a13e3c7d04220f04fb08eadde175bb15c1, associated with original candidate ZIP 8fd58b4ad0ea60ec06460b0ecaa0b9cea8e56467437975103d04392958a68795. The differing local ZIP was not installed; prior immutable manifest verification retained.

## Changed files and checks

This continuation adds the missing authoring-only readers/export allowlist and tests in ProbeActivity.java, RealityProbe.java, ProbeEvidenceExport.java and ProbeEvidenceExportTest.java; it adds this evidence directory and a coverage-document evidence pointer. Existing manager changes preserved. No fixture or module source edit applied.

Probe diagnostics unit tests: **145 passed**, 0 failures/errors/skips; diagnostics APK build passed. No compiler warning/error lines in the successful build log. Full shared profile JSON-schema check passes; full coherence check fails as above. git diff --check has the pre-existing ProbeReportPolicy.java:90 blank-line-at-EOF failure (exit 2); cached diff check passes. No unrelated whitespace fix. No full app/module/forge suites were rerun after the fixture stop.

Exact build/device commands and outputs: authoring-v2-build.log, authoring-v2-final-build.log, run_ops.py and commands.jsonl. Source copies preserve the actual inspected/tested implementation and validator behavior.

## Open questions

The profile contract must explicitly distinguish a real WebView UA from the Chrome-browser UA grammar, or authorize a reviewed validation/linkage change. A measured WebView UA cannot be turned into a browser UA by deleting tokens and still be called an exact observation. This sprint did not authorize that shared validation behavior change.

A host screen_size_class source or explicit derived-metadata policy is still required; it is required by forge validation despite being optional in JSON schema. The retained previous class was used only in an unapplied diagnostic alternative, not labelled a measured host fact.

The current concrete-instance UA authoring result is **RuntimeException**, and the retained log has no underlying exception/cause stack to quote. Earlier raw evidence recorded NullPointerException. Both are retained as error classes, never claimed as a diagnosed root cause. No UA-reader workaround was applied.

No platform verdict, VERIFIED promotion, coverage/parity claim or broader compatibility claim.
