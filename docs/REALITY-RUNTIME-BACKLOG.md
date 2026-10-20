# Reality runtime backlog

Unreleased phase 2 implements selected callbacks in groups 2, 3, 4, 5, 6 and a
match-only DRM gate. See [the precise matrix](RUNTIME-ENVELOPE-PHASE-2.md).
Physical verification remains pending; no new field is APPLIED. The planning
rows below remain constraints for unsupported surfaces, not verification claims.

This is a prioritized planning contract, not authorization to add hooks. Every
native/runtime change below needs a separate authorized device run, a test app,
an artifact-bound before/after result, and preservation/isolation audits. Current
Reality descriptions do not alter hardware. No platform verdict is implied.

| Priority / group | Exact Android surfaces / proposed strategy | Impact / risk / coherence |
|---|---|---|
| 1: linked display and locale parity | `Display.getMode().getPhysicalWidth/Height`, `Display.getRealMetrics().densityDpi`, `Display.getMode().getRefreshRate`, `Locale.getDefault().getLanguage/getCountry`, `TimeZone.getDefault().getID`, `TelephonyManager.getPhoneCount/getSimOperator`. First prove existing base aliases; Phase 1 implements base-backed Display.getMode/getSupportedModes and exact linked parity. Reality never supplies overrides. Local tests pass; separate matching-module device validation remains pending. | High / medium / high |
| 2: nested read-only inventory | `Display.Mode.getPhysicalWidth/getPhysicalHeight/getRefreshRate`, `CameraCharacteristics.LENS_FACING`, `INFO_SUPPORTED_HARDWARE_LEVEL`, `SENSOR_INFO_PIXEL_ARRAY_SIZE`; `Sensor.getType/getName/getMinDelay/getMaxDelay/getMaximumRange/getResolution`; `PackageInfo.packageName/versionName/getLongVersionCode`. Project and hash each item with explicit ordering/identity semantics before considering runtime support. Megapixel capability needs a documented definition, not an invented measurement. Reader-only follow-up first. | High / low reader, high hooks / high |
| 3: compute | `Build.SOC_MODEL`, `Build.SUPPORTED_ABIS`; proposed GPU reader `GLES20.glGetString(GL_RENDERER)` requires a valid GL context. CPU physical-core inventory has no equivalent reliable public API; `Runtime.availableProcessors()` is not a substitute. Never change ABI/kernel hardware; descriptive facts until scoped behavior can be proven coherent. Any runtime strategy requires separate authorization. | Medium / high / high |
| 4: capacity and panel facts | `ActivityManager.MemoryInfo.totalMem`, `StatFs.getAvailableBytes`; advertised RAM/storage, physical CPU count, design battery capacity and panel technology lack an equivalent stable public measurement. Keep advertised capacity separate from visible/reserved bytes. No proposed hook may fabricate unavailable hardware. `StorageStatsManager.getTotalBytes(UUID_DEFAULT)` would need a separately reviewed permission-free feasibility check, not new permissions in this sprint. | Medium / high / high |
| 5: host power/radio/carrier state | `ACTION_BATTERY_CHANGED` level/scale, status, plugged, temperature; `PowerManager.getCurrentThermalStatus/isPowerSaveMode`; `WifiManager.isWifiEnabled`, `BluetoothAdapter.getName/isEnabled`; `TelephonyManager.getSimCountryIso/getSimState`. Retain read-only host observations; changing dynamic safety/power state is out of scope. Bluetooth label semantics need explicit base-profile linkage before a future scoped callback proposal. Local Wi-Fi device label is not SSID. | Medium / high / medium |
| 6: software/environment descriptions | `WebView.getCurrentWebViewPackage`, prospective `WebSettings.getUserAgentString` on a deliberately created diagnostics WebView; `PackageManager.getPackageInfo`; `Settings.Secure.DEFAULT_INPUT_METHOD/ACCESSIBILITY_ENABLED`; prospective keyboard inventory `InputMethodManager.getEnabledInputMethodList` cannot prove a physical keyboard layout. Preserve caller visibility; do not fabricate installations/settings. A future scoped UA callback requires separate runtime authorization. | Medium / medium / high |
| 7: DRM metadata | `MediaDrm.getPropertyString("securityLevel")` is read-only. No key material, keybox, attestation or security-level elevation strategy is proposed. Actual hardware capability remains external evidence; metadata alone never proves it. | High / unacceptable for fabrication / high |

The authoritative source table gives exact mappings and reasons for all 97 paths.
Counts when all ten linked fields match: 10 applied, 17 partial, 3 pending, 27 diagnostic-only, 37 host-only,
3 unsupported. Pending paths: CPU cores, advertised storage and keyboard layout.
GPU and WebView UA now have partial, locally tested callbacks awaiting device
evidence. Host-only provenance and unsupported fields are not
targets for automatic enforcement. Counts include containers and wildcard leaves;
they are contract coverage, not independent physical measurements.
