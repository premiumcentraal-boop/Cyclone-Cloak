# Health compatibility coverage

Generated from `android/app/src/main/assets/compatibility-coverage.json`; the Health tab reads this same local asset. Do not edit this table by hand.

Pixel 8 / Android API 36 / measured installed module

Bundled evidence, not a live device check. Partial proof applies only to listed reads on the measured baseline. Local runtime fixes await device verification; reboot checks are deferred.

Evidence: [device results](runtime-evidence/release-api36-advertising.json), [per-read matrix](RUNTIME-MEASUREMENTS.md), [callback tests](../android/module/src/test/java/dev/cyclone/cloak/hook/HookBridgeTest.java).

Last verification dates are UTC dates of successful **selected baseline reads**, not the date of a failed attempt or verification of the local runtime fixes. Camera/sensor enumeration and coincidental matches do not count as proof.

| Area | Implementation status | Test status | Last verified (UTC) | Note |
| --- | --- | --- | --- | --- |
| Build fields | partial | partial_device_proof | 2026-10-10 | Nine Build fields and SDK/release/patch passed on the measured baseline. Incremental, bootloader and radio remain incomplete; local fixes await a device run. |
| System properties | partial | device_failed | not verified | Java/native identity reads failed. Local JNI registration fixes await a device run; native property paths remain uncovered. |
| Telephony | partial | unit_passed_device_failed | not verified | Count/type/slot callbacks have JVM tests. Baseline identifiers/operators failed and counts/types were blocked; ART installation remains unverified. |
| Android ID | partial | unit_passed_device_failed | not verified | Getter and unrelated-key guard have callback tests. Baseline getter/provider reads failed; local provider fix awaits a device run. |
| Serial | partial | partial_device_proof | 2026-10-10 | Build.SERIAL passed on the measured baseline; Build.getSerial failed. The whole serial area is not verified. |
| Advertising ID | partial | device_failed | not verified | The real Google SDK read failed. Deferred app-class-loader hook awaits device verification. |
| Display | partial | device_failed | not verified | Geometry and refresh hooks are incomplete. Height/refresh failed; matching density/width do not prove a hook. |
| Locale/timezone | partial | device_failed | not verified | Default/resource locale and timezone did not match both profiles. Local startup/resource fixes await a device run. |
| Wi-Fi identity | partial | device_failed_and_blocked | not verified | MAC callback exists but object getter failed; service MAC and SSID were permission blocked. SSID/BSSID are not profile fields. |
| Bluetooth identity | partial | device_failed_and_blocked | not verified | Address callback exists but failed on the baseline; name read was permission blocked and has no profile field. |
| Camera metadata | not_implemented | unmapped | not verified | Probe enumerates host characteristics only. No profile expectation or verified metadata hook exists. |
| Sensor metadata | not_implemented | unmapped | not verified | Probe enumerates host sensors only. No profile expectation or verified metadata hook exists. |
| DRM identifier | partial | device_failed | not verified | Widevine deviceUniqueId callback exists but failed on the baseline. Other DRM properties and attestation are not covered. |

Regenerate with `uv run -- python tools/render_health_coverage.py`. The evidence tests in `forge/tests/test_health_coverage.py` reject unsupported verification claims and a stale generated table.
