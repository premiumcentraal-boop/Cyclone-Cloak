Cyclone Cloak v0.11.0-alpha.1 — camera characteristics coverage

First sprint of the camera coverage plan (B3A). A bound profile now also re-renders
the Android camera2 read path: supported hardware level and sensor pixel array are
served from the profile, matched per camera by facing. Camera facing itself stays
real so camera apps keep working normally.

What shipped:
- Catalog: camera specs for all 15 built-in phones (main/tele/ultrawide/front with
  realistic sensor sizes and hardware levels).
- Schema 0.3: optional `camera.cameras[]` block (facing, hardware level, pixel
  array). Schema 0.2 profiles remain valid.
- Forge: camera block copied into every forged profile (Python and Kotlin forges in
  parity); coherence validation with bounds and aspect-ratio warnings.
- Runtime: per-camera specs published as `cloak.camera.*` props; Java hook serves
  the two keys on `CameraManager.getCameraCharacteristics` results.
- Probe: camera rows now verify against the profile expectation (matched facings)
  instead of being observed-only.
- Tests: forge suite 186 green, module 11/11, app suite green; probe builds both ABIs.

Known limits (honest ledger):
- Camera count still follows the host device; per-id list trimming is a future sprint.
- Facing values are host-real; only level + pixel array are re-rendered.
- Device verification of the camera rows is scheduled with tonight's device pass.

Artifacts: signed companion APK and module zip are attached by CI from this tag.
