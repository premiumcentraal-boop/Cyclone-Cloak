# Probe-only validation

Result: INCOMPLETE
Failure: PROBE_REPORT_TIMEOUT_USER_0_CAPTURE_2
APK SHA-256: 81706b5ddc386881dfff7391cc344351806d8946f230d269f21d6105f815bda4
Production APK/version/permission snapshot unchanged: True
Diagnostics uninstall confirmed: True

| User | Capture | OBSERVED | BLOCKED | MISSING |
| --- | --- | --- | --- | --- |

Determinism:
- user 0 diagnostics.filesystem.build_prop: NOT_COMPARABLE
- user 0 diagnostics.filesystem.proc_version: NOT_COMPARABLE
- user 17 diagnostics.filesystem.build_prop: NOT_COMPARABLE
- user 17 diagnostics.filesystem.proc_version: NOT_COMPARABLE

Limits: probe-only caller-visible observations; no parity verdict or VERIFIED promotion.
No module install, root shell, reboot, binding overlay, user switch, permission grant/reset or production app launch was performed.
Private production stores/module files were not independently audited. APK/version/permission equality is not proof of private-store byte equality.
Installed module provenance was not established; the handoff alpha.28 description is not independently confirmed.
The existing diagnostics app was updated and then globally uninstalled as requested; its old private data is not restored.
Legacy probe comparisons used the packaged fixture without creating a runtime binding and are not interpreted as parity validation.
Reports are redacted exports retaining filesystem digests/counts and existing row statuses/digests only. No raw file content retained.
