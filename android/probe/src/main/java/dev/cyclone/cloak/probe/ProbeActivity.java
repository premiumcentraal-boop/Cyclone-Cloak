package dev.cyclone.cloak.probe;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.MediaDrm;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/** Independent consumer: actual values only come from Android APIs, never the fixture. */
@SuppressWarnings("deprecation")
public class ProbeActivity extends Activity {
    private interface Read { Object get() throws Exception; }
    private static final CaptureCoordinator CAPTURES = new CaptureCoordinator();
    private Capture activeCapture;

    private static byte[] readBytes(java.io.InputStream stream) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int count;
        while ((count = stream.read(chunk)) != -1) out.write(chunk, 0, count);
        return out.toByteArray();
    }

    private static String hex(byte[] value) {
        StringBuilder out = new StringBuilder();
        for (byte b : value) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }

    private static String sourceFor(String id) {
        if (id.startsWith("properties.nativeCallback.")) return "libc.__system_property_read_callback";
        if (id.startsWith("properties.native.")) return "libc.__system_property_get";
        if (id.startsWith("properties.java.")) return "android.os.SystemProperties";
        if (id.startsWith("build.")) return "android.os.Build";
        if (id.startsWith("version.")) return "android.os.Build.VERSION";
        if (id.startsWith("telephony.") || id.startsWith("operator.")) return "android.telephony.TelephonyManager";
        if (id.startsWith("settings.")) return "android.provider.Settings.Secure";
        if (id.startsWith("wifi.")) return "android.net.wifi.WifiInfo";
        if (id.startsWith("bluetooth.")) return "android.bluetooth.BluetoothAdapter";
        if (id.startsWith("drm.")) return "android.media.MediaDrm";
        if (id.startsWith("camera.")) return "android.hardware.camera2.CameraCharacteristics";
        if (id.startsWith("display.")) return "android.util.DisplayMetrics/Display";
        if (id.startsWith("locale.")) return "java.util.Locale/Configuration";
        if (id.startsWith("timezone.")) return "java.util.TimeZone";
        if (id.startsWith("packages.")) return "android.content.pm.PackageManager (caller-visible only)";
        if (id.startsWith("process.")) return "android.os.Process";
        if (id.startsWith("runtime.")) return "/proc/self/maps/ClassLoader (observation only)";
        return "Android API";
    }
    private TextView matrix;
    static { System.loadLibrary("probe"); }
    private static native String nativeProperty(String key);
    private static native String nativePropertyCallback(String key);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        matrix = new TextView(this);
        matrix.setPadding(24, 24, 24, 24);
        matrix.setTextIsSelectable(true);
        matrix.setText("Reading Android inspection surfaces…");
        ScrollView scroll = new ScrollView(this);
        scroll.addView(matrix);
        setContentView(scroll);
        beginCapture(new android.content.Intent(getIntent()));
    }

    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        beginCapture(new android.content.Intent(intent));
    }

    @Override protected void onDestroy() {
        if (activeCapture != null) CAPTURES.cancel(activeCapture.generation);
        super.onDestroy();
    }

    protected void prepareReads(Runnable ready) { ready.run(); }
    protected String permissionFailure(String id) { return null; }

    private void beginCapture(android.content.Intent intent) {
        Capture capture = new Capture(CAPTURES.begin(), intent);
        activeCapture = capture;
        prepareReads(() -> {
            if (CAPTURES.current(capture.generation) && capture.startOnce())
                new Thread(capture::run, "cloak-probe-" + capture.generation).start();
        });
    }

    /** Activity services are shared; all mutable capture data belongs to this instance. */
    private final class Capture extends ProbeCaptureSession {
        private String fixture;
        Capture(long generation, android.content.Intent intent) {
            super(generation, intent.getStringExtra("request_id"), intent.getStringExtra("fixture"),
                getPackageName().endsWith(".diag") && intent.getBooleanExtra("external_profile", false),
                intent.getIntExtra("expected_user", -1));
            fixture = "b".equals(requestedFixture) ? "b" : "a";
        }

        private JSONObject envelope() throws Exception {
            return new JSONObject().put("format", 1).put("fixture", fixture)
                .put("package", getPackageName()).put("uid", android.os.Process.myUid())
                .put("pid", android.os.Process.myPid()).put("request_id", requestId)
                .put("session_generation", generation).put("profile_sha256", profileDigest);
        }

        private void publish(JSONObject report, String visible) throws Exception {
            CAPTURES.commit(generation, () -> {
                File partial = new File(getFilesDir(), "report.json." + generation + ".tmp");
                try {
                    Files.write(partial.toPath(), report.toString(2).getBytes(StandardCharsets.UTF_8));
                    Files.move(partial.toPath(), new File(getFilesDir(), "report.json").toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } finally { Files.deleteIfExists(partial.toPath()); }
            });
            runOnUiThread(() -> {
                try { CAPTURES.commit(generation, () -> matrix.setText(visible)); }
                catch (Exception ignored) { /* UI must not affect evidence. */ }
            });
        }

        void run() {
            try {
                try (java.io.InputStream stream = external
                        ? Files.newInputStream(new File(getFilesDir(), "expected-profile.json").toPath())
                        : getAssets().open("profile-" + fixture + ".json")) {
                    byte[] bytes = readBytes(stream);
                    profile = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                    profileDigest = hex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
                }
                fixture = ProbeReportPolicy.fixture(requestedFixture, external,
                    external && "a".equals(requestedFixture) && sameFixture("a"),
                    external && "b".equals(requestedFixture) && sameFixture("b"));
                if ("a".equals(fixture) || "b".equals(fixture)) {
                    try (java.io.InputStream stream = getAssets().open("expectations-" + fixture + ".json")) {
                        diagnosticExpectations = DiagnosticExpectation.fixture(new JSONObject(
                            new String(readBytes(stream), StandardCharsets.UTF_8)), fixture);
                    }
                }
                collect();
                validateComplete();
                JSONObject runtimeTable;
                try (java.io.InputStream stream = getAssets().open("reality-runtime-fields.json")) {
                    runtimeTable = new JSONObject(new String(readBytes(stream), StandardCharsets.UTF_8));
                }
                JSONObject reality = RealityProbe.collect(ProbeActivity.this, profile, runtimeTable);
                JSONObject report = envelope().put("rows", new JSONArray(rows.toString()))
                    .put("device_reality", reality)
                    .put("reality_runtime", RealityRuntimeReport.build(runtimeTable, profile, reality))
                    .put("linked_parity", reality.getJSONObject("linked_parity"))
                    .put("runtime_envelope", RuntimeEnvelopeReport.collect(ProbeActivity.this, profile));
                StringBuilder visible = new StringBuilder("Cloak Runtime Probe\n\n");
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    visible.append(row.getString("status")).append("  ")
                        .append(row.getString("id")).append("\n")
                        .append(row.getString("reason")).append("\n\n");
                }
                publish(report, visible.toString());
            } catch (Exception failure) {
                try {
                    String reason = failure instanceof InvalidCapture ? ((InvalidCapture) failure).reason : "CAPTURE_FAILED";
                    String badId = failure instanceof InvalidCapture ? ((InvalidCapture) failure).rowId : "";
                    // Only fixed metadata/row IDs; never exception text or partial raw readings.
                    JSONArray failedRows = new JSONArray();
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject row = rows.optJSONObject(i);
                        if (row != null && EXPECTED_IDS.contains(row.optString("id")))
                            failedRows.put(new JSONObject().put("id", row.optString("id")));
                    }
                    publish(envelope().put("error", failure.getClass().getSimpleName())
                        .put("status", "FAIL").put("reason", reason).put("failed_row_id", badId)
                        .put("rows", failedRows), "Probe failed: " + reason);
                } catch (Exception ignored) { /* Host also preserves missing/unparseable output evidence. */ }
            }
        }

        private boolean sameFixture(String label) throws Exception {
            try (java.io.InputStream stream = getAssets().open("profile-" + label + ".json")) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int count;
                while ((count = stream.read(chunk)) != -1) bytes.write(chunk, 0, count);
                JSONObject fixture = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
                return ProbeReportPolicy.canonical(profile).equals(ProbeReportPolicy.canonical(fixture));
            }
        }

        /**
         * Expected camera rows: the profile spec matched by real facing + occurrence
         * order, with real values kept for cameras the profile does not describe.
         */
        private JSONArray buildCameraExpectation() throws Exception {
            CameraManager cameras = getSystemService(CameraManager.class);
            JSONArray actual = new JSONArray();
            for (String id : cameras.getCameraIdList()) {
                CameraCharacteristics c = cameras.getCameraCharacteristics(id);
                actual.put(new JSONObject().put("id", id).put("facing", c.get(CameraCharacteristics.LENS_FACING))
                    .put("level", c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL))
                    .put("pixelArray", String.valueOf(c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE))));
            }
            JSONArray specs = profile.optJSONObject("camera") == null
                    ? null : profile.optJSONObject("camera").optJSONArray("cameras");
            Map<Integer, Integer> seen = new HashMap<>();
            JSONArray expected = new JSONArray();
            for (int i = 0; i < actual.length(); i++) {
                JSONObject row = actual.getJSONObject(i);
                int facing = row.getInt("facing");
                int occurrence = seen.getOrDefault(facing, 0);
                seen.put(facing, occurrence + 1);
                JSONObject spec = null;
                if (specs != null) {
                    int index = 0;
                    for (int j = 0; j < specs.length(); j++) {
                        JSONObject candidate = specs.optJSONObject(j);
                        if (candidate == null || candidate.optInt("facing", -1) != facing) continue;
                        if (index++ == occurrence) { spec = candidate; break; }
                    }
                }
                JSONObject out = new JSONObject().put("id", row.get("id")).put("facing", facing);
                if (spec != null) {
                    out.put("level", spec.optInt("hardware_level"));
                    out.put("pixelArray", spec.optInt("pixel_width") + "x" + spec.optInt("pixel_height"));
                } else {
                    out.put("level", row.get("level"));
                    out.put("pixelArray", row.get("pixelArray"));
                }
                expected.put(out);
            }
            return expected;
        }

        private Object expected(String path) throws Exception {
            if (path == null) return null;
            String[] parts = path.split("\\.");
            Object value = profile;
            for (String part : parts) value = ((JSONObject) value).get(part);
            return value;
        }

        private void read(String id, String path, Read operation) throws Exception {
            compare(id, expected(path), operation);
        }

        private void compare(String id, Object expected, Read operation) throws Exception {
            long started = android.os.SystemClock.elapsedRealtimeNanos();
            JSONObject rule = diagnosticExpectations.optJSONObject(id);
            JSONObject row = new JSONObject().put("id", id).put("source", sourceFor(id))
                .put("has_expectation", rule != null || (expected != null && !expected.toString().isEmpty()))
                .put("actual", JSONObject.NULL).put("expected", expected == null ? JSONObject.NULL : expected);
            if (rule != null) {
                JSONObject ruleIdentity = new JSONObject(rule.toString());
                ruleIdentity.remove("expected_change"); // Edge expectation, not a different value constraint.
                row.put("expectation_policy", rule.getString("policy")).put("policy_version", 1)
                    .put("expectation_rule_sha256", hex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(ProbeReportPolicy.canonical(ruleIdentity).getBytes(StandardCharsets.UTF_8))));
                if (rule.getString("policy").equals("STABLE_ACROSS_SWITCH")) row.put("expected_change", rule.optBoolean("expected_change", false));
                if (rule.getString("policy").equals("EXACT")) row.put("expected", rule.get("value"));
            }
            try {
                String denied = permissionFailure(id);
                if (denied != null) {
                    row.put("status", "BLOCKED").put("reason", denied);
                    finishRow(row, started);
                    rows.put(row);
                    logDiagnosticRow(id, row);
                    return;
                }
                Object value = operation.get();
                row.put("actual", value == null ? JSONObject.NULL : value);
                row.put("actual_available", !DiagnosticExpectation.missing(value));
                if (rule != null) {
                    JSONObject checked = DiagnosticExpectation.evaluate(rule, value);
                    row.put("status", checked.getString("status")).put("reason", checked.getString("reason"));
                    finishRow(row, started);
                    rows.put(row);
                    logDiagnosticRow(id, row);
                    return;
                }
                boolean unspecified = expected == null || expected.toString().isEmpty();
                boolean matches = !unspecified && expected.toString().equals(String.valueOf(value));
                String status = ProbeReportPolicy.status(!unspecified, value == null && expected != JSONObject.NULL, matches);
                row.put("status", status)
                    .put("reason", unspecified ? "NO_PROFILE_EXPECTATION" : status.equals("MISSING") ? "VALUE_MISSING" : matches ? "MATCH" : "VALUE_MISMATCH");
            } catch (Exception | LinkageError error) {
                row.put("status", "BLOCKED").put("reason", ProbeReportPolicy.failureReason(error));
            }
            finishRow(row, started);
            rows.put(row);
            logDiagnosticRow(id, row);
        }

        private void finishRow(JSONObject row, long started) throws Exception {
            row.put("detail", row.optString("reason"))
                .put("duration_ms", (android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000);
        }

        private void logDiagnosticRow(String id, JSONObject row) {
            try {
                android.util.Log.i("CloakProbeDiagnostics", new JSONObject()
                    .put("timestamp", System.currentTimeMillis())
                    .put("process", android.os.Process.myPid()).put("thread", android.os.Process.myTid())
                    .put("field", id).put("status", row.optString("status")).toString());
            } catch (Exception ignored) { /* Diagnostics must not change a probe result. */ }
        }

        // These reads intentionally exercise denied APIs too. compare() checks
        // declared grants and converts SecurityException into a precise BLOCKED row.
        @android.annotation.SuppressLint("MissingPermission")
        private void collect() throws Exception {
            String[][] fields = {
                {"MANUFACTURER", "manufacturer"}, {"BRAND", "brand"}, {"MODEL", "model"},
                {"DEVICE", "device"}, {"PRODUCT", "product"}, {"HARDWARE", "hardware"},
                {"FINGERPRINT", "fingerprint"}, {"ID", "build_id"}, {"BOOTLOADER", "bootloader"},
            };
            for (String[] field : fields) read("build." + field[0], "device." + field[1],
                () -> Build.class.getField(field[0]).get(null));
            read("build.SERIAL", "identifiers.serial", () -> Build.SERIAL);
            read("build.getSerial", "identifiers.serial", Build::getSerial);
            compare("build.TIME", ((Number) expected("device.build_date_utc")).longValue() * 1000, () -> Build.TIME);
            compare("build.TYPE", "user", () -> Build.TYPE);
            compare("build.TAGS", "release-keys", () -> Build.TAGS);
            read("version.SDK_INT", "device.sdk_int", () -> Build.VERSION.SDK_INT);
            read("version.RELEASE", "device.version_release", () -> Build.VERSION.RELEASE);
            read("version.INCREMENTAL", "device.version_incremental", () -> Build.VERSION.INCREMENTAL);
            read("version.SECURITY_PATCH", "device.security_patch", () -> Build.VERSION.SECURITY_PATCH);
            read("build.getRadioVersion", "device.baseband", Build::getRadioVersion);

            String[][] properties = {
                {"ro.product.model", "device.model"}, {"ro.product.manufacturer", "device.manufacturer"},
                {"ro.product.brand", "device.brand"}, {"ro.product.device", "device.device"},
                {"ro.product.name", "device.product"}, {"ro.build.version.sdk", "device.sdk_int"},
                {"ro.build.fingerprint", "device.fingerprint"}, {"ro.serialno", "identifiers.serial"},
                {"ro.sf.lcd_density", "display.density"}, {"persist.sys.timezone", "locale.timezone"},
            };
            for (String[] prop : properties) {
                read("properties.java." + prop[0], prop[1], () -> Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class).invoke(null, prop[0]));
                read("properties.native." + prop[0], prop[1], () -> nativeProperty(prop[0]));
            }
            read("properties.nativeCallback.ro.product.model", "device.model",
                () -> nativePropertyCallback("ro.product.model"));

            TelephonyManager tm = getSystemService(TelephonyManager.class);
            compare("telephony.simState", null, () -> tm == null ? null : tm.getSimState());
            compare("telephony.phoneType", null, tm::getPhoneType);
            compare("telephony.roaming", null, tm::isNetworkRoaming);
            read("telephony.imei0", "identifiers.imei_primary", () -> tm.getImei(0));
            if (((Number) expected("telephony.sim_slot_count")).intValue() == 1)
                compare("telephony.imei1", JSONObject.NULL, () -> tm.getImei(1));
            else read("telephony.imei1", "identifiers.imei_secondary", () -> tm.getImei(1));
            read("telephony.deviceId", "identifiers.imei_primary", tm::getDeviceId);
            read("telephony.simSerial", "identifiers.sim_serial", tm::getSimSerialNumber);
            read("telephony.phoneCount", "telephony.sim_slot_count", tm::getPhoneCount);
            read("telephony.activeModemCount", "telephony.sim_slot_count", () -> {
                if (Build.VERSION.SDK_INT < 30) throw new NoSuchMethodException("API_30_REQUIRED");
                return tm.getActiveModemCount();
            });
            int networkType;
            switch (expected("telephony.network_type").toString()) {
                case "5G": networkType = TelephonyManager.NETWORK_TYPE_NR; break;
                case "LTE": networkType = TelephonyManager.NETWORK_TYPE_LTE; break;
                case "HSPA": networkType = TelephonyManager.NETWORK_TYPE_HSPA; break;
                case "UMTS": networkType = TelephonyManager.NETWORK_TYPE_UMTS; break;
                default: networkType = TelephonyManager.NETWORK_TYPE_GSM;
            }
            compare("telephony.networkType", networkType, tm::getNetworkType);
            compare("telephony.dataNetworkType", networkType, tm::getDataNetworkType);
            String operator = expected("telephony.mcc").toString() + expected("telephony.mnc");
            compare("operator.networkNumeric", operator, tm::getNetworkOperator);
            compare("operator.simNumeric", operator, tm::getSimOperator);
            read("operator.networkName", "telephony.carrier_name", tm::getNetworkOperatorName);
            read("operator.simName", "telephony.carrier_name", tm::getSimOperatorName);
            String country = expected("locale.country").toString().toLowerCase(Locale.ROOT);
            compare("operator.networkCountry", country, tm::getNetworkCountryIso);
            compare("operator.simCountry", country, tm::getSimCountryIso);
            String serial = expected("identifiers.sim_serial").toString();
            compare("telephony.subscriberId", operator + serial.substring(serial.length() - (15 - operator.length())), tm::getSubscriberId);
            read("settings.secure.androidId", "identifiers.android_id",
                () -> Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
            read("settings.provider.androidId", "identifiers.android_id", () -> {
                try (Cursor cursor = getContentResolver().query(Settings.Secure.CONTENT_URI,
                        new String[]{"value"}, "name=?", new String[]{"android_id"}, null)) {
                    return cursor != null && cursor.moveToFirst() ? cursor.getString(0) : null;
                }
            });
            compare("settings.unrelatedControl", null,
                () -> Settings.Secure.getString(getContentResolver(), "accessibility_enabled"));
            // Builder isolates the getter hook from service permission requirements.
            read("wifi.objectMac", "identifiers.mac", () -> {
                if (Build.VERSION.SDK_INT < 30) throw new NoSuchMethodException("API_30_REQUIRED");
                return new WifiInfo.Builder().build().getMacAddress();
            });
            read("wifi.serviceMac", "identifiers.mac", () -> getSystemService(WifiManager.class).getConnectionInfo().getMacAddress());
            compare("wifi.bssid", null, () -> {
                WifiInfo info = getSystemService(WifiManager.class).getConnectionInfo();
                return info == null ? null : info.getBSSID();
            });
            compare("wifi.linkSpeed", null, () -> {
                WifiInfo info = getSystemService(WifiManager.class).getConnectionInfo();
                return info == null ? null : info.getLinkSpeed();
            });
            compare("wifi.ssid", null, () -> {
                WifiInfo info = getSystemService(WifiManager.class).getConnectionInfo();
                return info == null ? null : info.getSSID();
            });
            read("bluetooth.address", "identifiers.bt_mac", () -> BluetoothAdapter.getDefaultAdapter().getAddress());
            compare("bluetooth.name", null, () -> {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                return adapter == null ? null : adapter.getName();
            });
            read("advertising.id", "identifiers.advertising_id",
                () -> AdvertisingIdRead.read(getClassLoader(), Context.class, ProbeActivity.this));
            read("drm.deviceUniqueId", "identifiers.widevine_id", () -> {
                try (MediaDrm drm = new MediaDrm(UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"))) {
                    byte[] value = drm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID);
                    if (value == null) return null;
                    StringBuilder hex = new StringBuilder();
                    for (byte b : value) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
                    return hex.toString();
                }
            });
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            read("display.resources.width", "display.width", () -> metrics.widthPixels);
            read("display.resources.height", "display.height", () -> metrics.heightPixels);
            read("display.resources.density", "display.density", () -> metrics.densityDpi);
            Display display = getWindowManager().getDefaultDisplay();
            DisplayMetrics real = new DisplayMetrics();
            display.getRealMetrics(real);
            read("display.real.width", "display.width", () -> real.widthPixels);
            read("display.real.height", "display.height", () -> real.heightPixels);
            read("display.real.density", "display.density", () -> real.densityDpi);
            compare("display.refresh", ((Number) expected("display.refresh_rate_hz")).floatValue(), display::getRefreshRate);
            Object cameraExpected;
            try {
                cameraExpected = buildCameraExpectation();
            } catch (Throwable t) {
                cameraExpected = null;
            }
            compare("camera.characteristics", cameraExpected, () -> {
                CameraManager cameras = getSystemService(CameraManager.class);
                JSONArray result = new JSONArray();
                for (String id : cameras.getCameraIdList()) {
                    CameraCharacteristics c = cameras.getCameraCharacteristics(id);
                    result.put(new JSONObject().put("id", id).put("facing", c.get(CameraCharacteristics.LENS_FACING))
                        .put("level", c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL))
                        .put("pixelArray", String.valueOf(c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE))));
                }
                return result;
            });
            compare("sensors.list", null, () -> {
                JSONArray result = new JSONArray();
                for (Sensor sensor : getSystemService(SensorManager.class).getSensorList(Sensor.TYPE_ALL))
                    result.put(new JSONObject().put("type", sensor.getType()).put("name", sensor.getName())
                        .put("vendor", sensor.getVendor()));
                return result;
            });
            read("locale.default.language", "locale.language", () -> Locale.getDefault().getLanguage());
            read("locale.default.country", "locale.country", () -> Locale.getDefault().getCountry());
            read("locale.resources.language", "locale.language", () -> getResources().getConfiguration().getLocales().get(0).getLanguage());
            read("locale.resources.country", "locale.country", () -> getResources().getConfiguration().getLocales().get(0).getCountry());
            read("timezone.default", "locale.timezone", () -> TimeZone.getDefault().getID());
            compare("process.uid", getApplicationInfo().uid, android.os.Process::myUid);
            // expectedUser was snapshotted before the worker started.
            compare("process.androidUser", expectedUser < 0 ? null : expectedUser,
                () -> android.os.Process.myUid() / 100000);
            compare("packages.visible", null, () -> {
                java.util.List<String> names = new java.util.ArrayList<>();
                for (android.content.pm.PackageInfo item : getPackageManager().getInstalledPackages(0)) names.add(item.packageName);
                java.util.Collections.sort(names);
                return new JSONArray(names);
            });
            compare("runtime.loadedLibraries", null, () -> {
                java.util.Set<String> names = new java.util.TreeSet<>();
                for (String line : Files.readAllLines(new File("/proc/self/maps").toPath(), StandardCharsets.UTF_8)) {
                    String name = line.substring(line.lastIndexOf('/') + 1).trim();
                    if (name.endsWith(".so") && (name.contains("cloak") || name.contains("pine")
                            || name.contains("shadowhook") || name.contains("zygisk"))) names.add(name);
                }
                return new JSONArray(names);
            });
            compare("runtime.javaBridgeVisible", null, () -> {
                try { Class.forName("dev.cyclone.cloak.hook.HookBridge", false, getClassLoader()); return true; }
                catch (ClassNotFoundException absent) { return false; }
            });
        }
    }
}
