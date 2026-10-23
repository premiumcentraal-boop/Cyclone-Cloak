# Rung 1b — real authoring facts, fixture-schema stop

Result: **STOPPED** at fixture-authoring schema conformance. The real host authoring export was captured; its full sensor inventory cannot be represented by the existing schema without changing or discarding facts. No enriched fixture was installed and no normal comparison captures or APPLIED promotions occurred. Prior evidence is preserved.

## Preparation and authoring

Users 0 and 17 were RUNNING_UNLOCKED. The diagnostics APK was installed for both users and its installed SHA-256 verified for each; see `authoring-apk.json` and `authoring-installed-hashes.json`. Authoring used the existing standard switch to user 17 and a cold diagnostics launch with `authoring_export=true`, without a temporary runtime binding.

One narrow export fix retains the seven explicitly approved authoring reader results and timestamps for `authoring.json` only. Normal report/marker redaction remains unchanged. A focused test proves authoring preservation and normal report redaction. The first test build used Files.readString unsupported by this Android compilation harness; the single build retest uses the existing Files.readAllBytes pattern and passed.

The local module ZIP is now `0a69abcfb7943cd2d43b5e6e4ececc8b8c356e99768dda431b83d9bcf1a43d18`, differing from the handoff ZIP. It was never installed or used. All installed immutable files were checked against the previous validated candidate manifest, and ARM64 remains `d7dd105446a229f981e5cdd1aed862a13e3c7d04220f04fb08eadde175bb15c1`. Installed immutable module files are identical before/after. The mutable load-records.jsonl hash changed; both hashes and the exact path are recorded in module-unchanged.json. No log rollback or rewrite was performed.

The export was not readable through the requested cross-user external run-as path: it returned Permission denied and reached the 45-second transport timeout. A read-only root check recovered the existing file at `/data/media/17/Android/data/dev.cyclone.cloak.probe.diag/files/standalone-evidence/authoring.json`; no second launch/retest was performed. `late-authoring-root-visible.json` retains its exact text, with captured_at_ms=1792771610372 and captured_sdk_int=36. The transport limitation is explicit; no successful run-as pull or observed COMPLETE log line is claimed.

## Genuine authoring results

- GPU reader: Mali-G715.
- Available storage: 66.90276718139648 GiB; proposed requested rounding: 66.9 GiB.
- Caller-visible allowlist: five package/version entries. Chrome was not returned; no visibility expansion or invented entry.
- Sensors: full array of 38 real descriptors preserved.
- WebView provider: com.google.android.webview; version: 155.0.8059.43.
- Concrete-instance UA: error `NullPointerException`; no UA was invented.

`profile-c-proposed-not-applied.json` binds each supplied value to reader ID, authoring time, SDK, Android user, unbound host state and authoring-file hash using the existing open meta field. It is evidence only. The actual source fixture C remains byte-identical to its preflight copy.

## Exact schema blocker

The full real sensor inventory produces **33 schema errors**, retained with paths and actual values in `fixture-authoring-validation.json`. Examples:

- sensors.values.inventory.14.min_delay_us = -1; schema minimum 0.
- sensors.values.inventory.16.range = 3.4028234663852886e+38; schema maximum 1000000.
- sensors.values.inventory.22.type = 65538; schema maximum 65535.
- Several real descriptors have resolution 0, below schema minimum 0.000001.

No clamping, filtering, replacement, schema edit or fabricated provenance was performed. The unrepresentable full inventory is the authoring stop gate, not a demonstrated runtime mismatch. The user-0 smoke, two user-17 comparison captures and settled third capture were not run. Legacy determinism is **NOT MEASURED** in this run.

## Cleanup and preservation

Diagnostics globally uninstalled, package paths and CE/DE/external diagnostics directories absent for both users, standard switch returned to user 0. The existing production snapshot comparison is exactly equal, including companion APK, audited grants/stable AppOps, published entries/bodies/shares and accessible binding/index stores. No temporary runtime overlay was created. Immutable module files unchanged; the durable runtime load log changed during the run, as recorded separately. No production APK launch/install, module install/removal, reboot, shell permission/AppOps writes, user creation/removal, commit, push, tag or release.

## Checks and changed files

Probe diagnostics unit suite: **145 tests**, 0 failures, 0 errors, 0 skipped; diagnostics APK build passed on the single build retest. `git diff --check` returned 2 for a pre-existing new blank line at EOF in ProbeReportPolicy.java:90, which was preserved; cached check passed. LF/CRLF notices are retained. No full app/forge/module suites were run because the authoring gate stopped the sprint.

This run changes only ProbeEvidenceExport.java and its test, plus this evidence directory; pre-existing manager changes are preserved. Fixture C was not changed. Complete source copies, logs, hashes, proposed fixture, validation errors, command log, audits and cleanup proof are archived here. Exact commands: `exact-commands.txt` and `commands.jsonl`.

## Open questions

The sensor schema needs an explicit policy for actual Android descriptors: one-shot delay -1, vendor/private sensor type IDs, zero resolution and Float.MAX_VALUE-style ranges. This sprint does not authorize inventing replacements or changing the schema, so this remains unresolved.

The run-as transport cannot read this user's external authoring path despite a real export being present. This run recovered evidence through read-only root access and does not relabel it a successful requested transport.

The authoring reader returned the exact recorded error **NullPointerException** for concrete-instance UA. No matching retained RUNTIME_EXCEPTION stack/cause was recovered from this run; it would be false to quote a diagnosed cause. The full available target-PID log is preserved in authoring-timeout-logcat.json. No WebView UA fix was attempted.

No platform verdict, VERIFIED promotion, parity proof or coverage claim is made.
