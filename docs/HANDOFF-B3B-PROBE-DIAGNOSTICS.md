# B3B — diagnostics probe variant + single-phase runner flag

Assigned: normal agent. Date: 2026-10-13. No release, no tag — commit to `main` and push is enough.

## Context

- Repo: `C:\Users\Agent\Cyclone\cloak-repo` (branch `main`). The repo-level `AGENTS.md` compass has environment facts; the short version:
  - `JAVA_HOME=C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot`
  - Gradle: `$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat` (fallback: `$env:LOCALAPPDATA/Gradle/gradle-8.11.1/bin/gradle.bat`), invoked with `-p android`
  - adb: `C:/Users/Agent/android-sdk/platform-tools/adb.exe`, serial `3B171FDJH0061G`
  - Python via `uv run -- python ...`
- The diagnostic probe (`android/probe`, package `dev.cyclone.cloak.probe`) runs a coverage matrix of device reads. Its default build deliberately declares no extra permissions, so three rows can only record `BLOCKED` (SecurityException), never a value: `wifi.serviceMac`, `wifi.ssid`, `bluetooth.name`. That is honest, but it masks whether the engine would serve these rows correctly when the caller holds the permissions.
- `tools/run_runtime_probe.py` always runs the full five-phase sequence (unbound, bound-a, bound-a-repeat, bound-b, unbound-after), so every verification costs a full cycle.

## Goal

Separate "no permission held" from "engine gap", and make cheap partial verification possible.

## Deliverables

1. **New probe flavor `diagnostics`** in `android/probe/build.gradle`:
   - Keep the default variant exactly as it is today (same manifest, same output path `android/probe/build/outputs/apk/debug/probe-debug.apk`).
   - Add flavor `diagnostics` with `applicationIdSuffix ".diag"` and `versionNameSuffix "-diag"` so it installs side by side.
   - Flavor source set `src/diagnostics/` provides a launcher activity extending the existing probe activity that requests the runtime permissions **before** reads start: `ACCESS_FINE_LOCATION`, `NEARBY_WIFI_DEVICES` (API 33+), `BLUETOOTH_CONNECT` (API 31+). The flavor manifest adds only the matching `<uses-permission>` entries.
   - Denied permissions must degrade gracefully: the affected rows stay `BLOCKED` with the reason recorded in the row.
2. **Runner flag** `--phases` on `tools/run_runtime_probe.py`: `all` (default) or a comma-separated subset (e.g. `bound-a`). Subset runs write the JSON with a `phases` metadata list, and `tools/render_runtime_matrix.py` must tolerate partial phase sets (missing phases render as "not run", no crash). Default behavior unchanged.
3. **Local verification (no device actions):**
   - Build both probe variants; confirm the default APK path is unchanged and note the diagnostics APK path.
   - `:module:testDebugUnitTest :app:testDebugUnitTest` and the forge pytest suite must stay green.
4. **Device verification (adb only — no reboot, no module install; this sprint does not touch the module):**
   - Install the diagnostics APK, then run the runner with `--apk <diagnostics apk>` and `--phases bound-a`.
   - Expected outcome: the three rows move from `BLOCKED` to `PASS` or `FAIL`. A real `FAIL` is a valid, valuable result — record it, do not paper over it.
   - Update `docs/RUNTIME-COVERAGE.md` (counts plus a note that the diagnostics variant unlocks these rows; the default probe intentionally keeps them BLOCKED), add `docs/RUNTIME-MATRIX-B3B-2026-10-13.md`, and a dated evidence JSON under `docs/runtime-evidence/`.
5. **Constraints:** changes limited to `android/probe/**`, `tools/run_runtime_probe.py`, `tools/render_runtime_matrix.py`, `docs/**`. No schema/forge/module/app changes, no new dependencies, no release/tag.

## Reporting

Report back: which rows flipped and to what, exact matrix counts, both APK paths, the `--phases` flag usage, and any fact in this handoff that turned out to be wrong (trust the code, note the correction).