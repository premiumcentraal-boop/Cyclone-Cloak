package dev.cyclone.cloak.probe;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.pm.PackageInfo;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.MediaDrm;
import android.os.StatFs;
import android.telephony.TelephonyManager;
import android.webkit.WebView;
import org.json.JSONArray;
import org.json.JSONObject;

/** Optional format 1 extension. Only digests, fixed reasons and timing leave each reader. */
@SuppressWarnings("deprecation")
final class RuntimeEnvelopeReport {
    private RuntimeEnvelopeReport() { }
    static Object normalize(Object value) throws Exception {
        if (value instanceof Number) return new java.math.BigDecimal(value.toString()).stripTrailingZeros();
        if (value instanceof JSONObject) {
            JSONObject result = new JSONObject();
            java.util.Iterator<String> keys = ((JSONObject) value).keys();
            while (keys.hasNext()) { String key = keys.next(); result.put(key, normalize(((JSONObject) value).get(key))); }
            return result;
        }
        if (value instanceof JSONArray) {
            JSONArray source = (JSONArray) value, result = new JSONArray();
            java.util.List<Object> items = new java.util.ArrayList<>();
            boolean inventory = source.length() > 0;
            for (int i = 0; i < source.length(); i++) {
                Object item = normalize(source.get(i)); items.add(item);
                inventory &= item instanceof JSONObject;
            }
            if (inventory) items.sort(java.util.Comparator.comparing(item -> {
                try { return ProbeReportPolicy.canonical(item); }
                catch (Exception invalid) { throw new IllegalArgumentException("ENVELOPE_VALUE_INVALID"); }
            }));
            for (Object item : items) result.put(item);
            return result;
        }
        return value;
    }
    static JSONObject row(String id, Object expected, RealityReport.Reader reader) throws Exception {
        return RealityReport.row(id, normalize(expected), () -> normalize(reader.read()));
    }
    static Object expected(JSONObject profile, String section, String field) {
        JSONObject reality = profile.optJSONObject("reality");
        JSONObject item = reality == null ? null : reality.optJSONObject(section);
        JSONObject values = item != null && "known".equals(item.optString("availability")) ? item.optJSONObject("values") : null;
        return values == null ? null : values.opt(field);
    }
    static JSONObject report(JSONArray rows) throws Exception {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            String id = row.getString("id");
            if (!id.matches("envelope\\.[A-Za-z0-9.]+") || !ids.add(id)) throw new IllegalArgumentException("ENVELOPE_ROW_INVALID");
            java.util.Set<String> keys = new java.util.HashSet<>(); row.keys().forEachRemaining(keys::add);
            if (!keys.equals(new java.util.HashSet<>(java.util.Arrays.asList("id", "source", "expected_sha256", "actual_sha256", "status", "reason", "duration_ms"))))
                throw new IllegalArgumentException("ENVELOPE_ROW_INVALID");
            if (!java.util.Arrays.asList("PASS", "FAIL", "OBSERVED", "BLOCKED", "MISSING", "UNMAPPED").contains(row.getString("status"))
                    || row.getLong("duration_ms") < 0) throw new IllegalArgumentException("ENVELOPE_ROW_INVALID");
            for (String hash : new String[]{"actual_sha256", "expected_sha256"})
                if (!row.isNull(hash) && !row.getString(hash).matches("[0-9a-f]{64}")) throw new IllegalArgumentException("ENVELOPE_ROW_INVALID");
            if ("PASS".equals(row.getString("status")) || "FAIL".equals(row.getString("status"))) {
                if (row.isNull("actual_sha256") || row.isNull("expected_sha256")
                        || row.getString("actual_sha256").equals(row.getString("expected_sha256")) != "PASS".equals(row.getString("status")))
                    throw new IllegalArgumentException("ENVELOPE_COMPARISON_UNPROVEN");
            }
            // No raw values and no verified state. Even a hash match is only a local comparison.
            for (String key : new String[]{"actual", "expected", "value", "raw"})
                if (row.has(key)) throw new IllegalArgumentException("ENVELOPE_RAW_VALUE_REJECTED");
        }
        return new JSONObject().put("format", 1).put("verification", "NOT_VERIFIED")
            .put("rows", rows).put("limitations", new JSONArray()
                .put("CALLER_VISIBILITY_AND_PERMISSIONS_PRESERVED")
                .put("NOMINAL_RAM_STORAGE_AND_PHYSICAL_CORES_ARE_NOT_PROCESS_CAPACITY")
                .put("DRM_SECURITY_LEVEL_MATCH_ONLY")
                .put("PHYSICAL_COLD_START_AND_UNBOUND_RESTORATION_PENDING"));
    }

    static JSONObject collect(Activity activity, JSONObject profile) throws Exception {
        JSONArray rows = new JSONArray();
        rows.put(row("envelope.sensors.inventory", expected(profile, "sensors", "inventory"), () -> {
            JSONArray values = new JSONArray();
            for (Sensor s : activity.getSystemService(SensorManager.class).getSensorList(Sensor.TYPE_ALL))
                values.put(new JSONObject().put("type", s.getType()).put("name", s.getName())
                    .put("min_delay_us", s.getMinDelay()).put("max_delay_us", s.getMaxDelay())
                    .put("range", s.getMaximumRange()).put("resolution", s.getResolution()));
            return values;
        }));
        rows.put(row("envelope.camera.characteristics", null, () -> {
            JSONArray values = new JSONArray();
            CameraManager manager = activity.getSystemService(CameraManager.class);
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                android.util.Size size = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                values.put(new JSONObject().put("id", id).put("facing", c.get(CameraCharacteristics.LENS_FACING))
                    .put("level", c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL))
                    .put("width", size == null ? JSONObject.NULL : size.getWidth())
                    .put("height", size == null ? JSONObject.NULL : size.getHeight()));
            }
            return values;
        }));
        rows.put(row("envelope.memory.totalMem", null, () -> {
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            activity.getSystemService(ActivityManager.class).getMemoryInfo(memory); return memory.totalMem;
        }));
        rows.put(row("envelope.storage.totalBytes", null, () -> new StatFs(activity.getFilesDir().getPath()).getTotalBytes()));
        Object free = expected(profile, "memory", "free_storage_gib");
        rows.put(row("envelope.storage.availableBytes", free instanceof Number ? (long) (((Number) free).doubleValue() * 1073741824.0) : null,
            () -> new StatFs(activity.getFilesDir().getPath()).getAvailableBytes()));
        rows.put(row("envelope.compute.schedulerProcessors", null, () -> Runtime.getRuntime().availableProcessors()));
        rows.put(row("envelope.compute.gpuRenderer", expected(profile, "compute", "gpu"), RuntimeEnvelopeReport::gpu));
        rows.put(row("envelope.packages.visibleVersions", expected(profile, "packages", "installed"), () -> {
            JSONArray result = new JSONArray();
            for (String pkg : new String[]{"android", "com.android.vending", "com.google.android.gms", "com.google.android.gsf", "com.google.android.webview", "com.android.chrome"}) {
                try {
                    PackageInfo p = activity.getPackageManager().getPackageInfo(pkg, 0);
                    result.put(new JSONObject().put("package", p.packageName).put("version_name", p.versionName).put("version_code", p.getLongVersionCode()));
                } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { /* Do not broaden visibility. */ }
            }
            return result;
        }));
        rows.put(row("envelope.webview.provider", expected(profile, "webview", "provider"), () -> {
            PackageInfo p = WebView.getCurrentWebViewPackage(); return p == null ? null : p.packageName;
        }));
        rows.put(row("envelope.webview.version", expected(profile, "webview", "version"), () -> {
            PackageInfo p = WebView.getCurrentWebViewPackage(); return p == null ? null : p.versionName;
        }));
        rows.put(row("envelope.webview.defaultUa", expected(profile, "webview", "user_agent"), () -> android.webkit.WebSettings.getDefaultUserAgent(activity)));
        rows.put(row("envelope.webview.instanceUa", expected(profile, "webview", "user_agent"), () -> {
            android.webkit.WebView v = new android.webkit.WebView(activity);
            try {
                return v.getSettings().getUserAgentString();
            } finally {
                v.destroy();
            }
        }));
        rows.put(row("envelope.display.realSize", baseSize(profile), () -> {
            android.graphics.Point size = new android.graphics.Point();
            activity.getWindowManager().getDefaultDisplay().getRealSize(size);
            return new JSONArray().put(size.x).put(size.y);
        }));
        TelephonyManager telephony = activity.getSystemService(TelephonyManager.class);
        Object state = expected(profile, "carrier", "sim_state");
        Integer expectedState = simStateExpectation(state);
        rows.put(row("envelope.telephony.simState", expectedState, telephony::getSimState));
        rows.put(row("envelope.telephony.simStateSlot0", expectedState, () -> telephony.getSimState(0)));
        rows.put(row("envelope.drm.securityLevel", expected(profile, "drm", "security_level"), () -> {
            try (MediaDrm drm = new MediaDrm(java.util.UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"))) {
                return drm.getPropertyString("securityLevel");
            }
        }));
        return report(rows);
    }

    static Integer simStateExpectation(Object state) {
        if ("absent".equals(state)) return Integer.valueOf(1);
        if ("pin_required".equals(state)) return Integer.valueOf(2);
        if ("puk_required".equals(state)) return Integer.valueOf(3);
        if ("ready".equals(state)) return Integer.valueOf(5);
        return null;
    }

    private static Object baseSize(JSONObject profile) {
        JSONObject display = profile.optJSONObject("display");
        return display == null || !display.has("width") || !display.has("height") ? null
            : new JSONArray().put(display.opt("width")).put(display.opt("height"));
    }

    private static String gpu() {
        // A private 1x1 pbuffer supplies a real GL context; restore the caller's context on every path.
        android.opengl.EGLDisplay previousDisplay = android.opengl.EGL14.eglGetCurrentDisplay();
        android.opengl.EGLContext previousContext = android.opengl.EGL14.eglGetCurrentContext();
        android.opengl.EGLSurface previousDraw = android.opengl.EGL14.eglGetCurrentSurface(android.opengl.EGL14.EGL_DRAW);
        android.opengl.EGLSurface previousRead = android.opengl.EGL14.eglGetCurrentSurface(android.opengl.EGL14.EGL_READ);
        android.opengl.EGLDisplay display = android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY);
        android.opengl.EGLContext context = android.opengl.EGL14.EGL_NO_CONTEXT;
        android.opengl.EGLSurface surface = android.opengl.EGL14.EGL_NO_SURFACE;
        boolean initialized = false;
        try {
            if (display == android.opengl.EGL14.EGL_NO_DISPLAY || !android.opengl.EGL14.eglInitialize(display, new int[2], 0, new int[2], 0))
                throw new IllegalStateException("EGL_UNAVAILABLE");
            initialized = true;
            android.opengl.EGLConfig[] configs = new android.opengl.EGLConfig[1]; int[] count = new int[1];
            int[] attrs = {android.opengl.EGL14.EGL_RENDERABLE_TYPE, 4, android.opengl.EGL14.EGL_SURFACE_TYPE, 1, android.opengl.EGL14.EGL_NONE};
            if (!android.opengl.EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, count, 0) || count[0] == 0)
                throw new IllegalStateException("EGL_CONFIG_UNAVAILABLE");
            context = android.opengl.EGL14.eglCreateContext(display, configs[0], android.opengl.EGL14.EGL_NO_CONTEXT,
                new int[]{android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, android.opengl.EGL14.EGL_NONE}, 0);
            surface = android.opengl.EGL14.eglCreatePbufferSurface(display, configs[0],
                new int[]{android.opengl.EGL14.EGL_WIDTH, 1, android.opengl.EGL14.EGL_HEIGHT, 1, android.opengl.EGL14.EGL_NONE}, 0);
            if (context == android.opengl.EGL14.EGL_NO_CONTEXT || surface == android.opengl.EGL14.EGL_NO_SURFACE
                    || !android.opengl.EGL14.eglMakeCurrent(display, surface, surface, context)) throw new IllegalStateException("EGL_CONTEXT_UNAVAILABLE");
            return android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER);
        } finally {
            if (initialized) {
                android.opengl.EGL14.eglMakeCurrent(display, android.opengl.EGL14.EGL_NO_SURFACE, android.opengl.EGL14.EGL_NO_SURFACE, android.opengl.EGL14.EGL_NO_CONTEXT);
                if (surface != android.opengl.EGL14.EGL_NO_SURFACE) android.opengl.EGL14.eglDestroySurface(display, surface);
                if (context != android.opengl.EGL14.EGL_NO_CONTEXT) android.opengl.EGL14.eglDestroyContext(display, context);
                // Do not terminate a display shared with the app's existing renderer.
                if (previousDisplay != android.opengl.EGL14.EGL_NO_DISPLAY)
                    android.opengl.EGL14.eglMakeCurrent(previousDisplay, previousDraw, previousRead, previousContext);
                // The default display may also be owned by another thread. Never terminate it here.
            }
        }
    }
}
