package dev.cyclone.cloak.probe;

import android.app.Activity;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.MediaDrm;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.StatFs;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.webkit.WebView;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only descriptive surfaces, isolated from the legacy 89-row report and all runtime hooks. */
@SuppressWarnings("deprecation")
final class RealityProbe {
    private RealityProbe() { }
    // RealityReport catches denied reads per row; no privileged permission is requested.
    @android.annotation.SuppressLint("MissingPermission")
    static JSONObject collect(Activity activity, JSONObject profile, JSONObject contractTable) throws Exception {
        Map<String, RealityReport.Reader> readers = new HashMap<>();
        readers.put("compute.soc", () -> Build.VERSION.SDK_INT >= 31 ? Build.SOC_MODEL : null);
        readers.put("compute.abis", () -> new JSONArray(Build.SUPPORTED_ABIS));
        // Runtime.availableProcessors is a scheduler view, not a physical CPU inventory. Leave cores unmapped.
        readers.put("memory.ram_gib", () -> {
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            activity.getSystemService(ActivityManager.class).getMemoryInfo(info);
            return info.totalMem / 1073741824.0;
        });
        // Filesystem capacity excludes reserved space; do not pretend it is advertised storage capacity.
        readers.put("memory.free_storage_gib", () -> new StatFs(activity.getFilesDir().getPath()).getAvailableBytes() / 1073741824.0);
        Display display = activity.getWindowManager().getDefaultDisplay();
        readers.put("panel.width", () -> display.getMode().getPhysicalWidth());
        readers.put("panel.height", () -> display.getMode().getPhysicalHeight());
        readers.put("panel.refresh_hz", () -> display.getMode().getRefreshRate());
        readers.put("panel.density_dpi", () -> { DisplayMetrics m = new DisplayMetrics(); display.getRealMetrics(m); return m.densityDpi; });
        readers.put("panel.modes", () -> {
            JSONArray modes = new JSONArray();
            for (Display.Mode mode : display.getSupportedModes()) modes.put(new JSONObject().put("width", mode.getPhysicalWidth())
                .put("height", mode.getPhysicalHeight()).put("refresh_hz", mode.getRefreshRate()));
            return modes;
        });
        CameraManager cameras = activity.getSystemService(CameraManager.class);
        readers.put("cameras.count", () -> cameras.getCameraIdList().length);
        readers.put("cameras.inventory", () -> {
            JSONArray inventory = new JSONArray();
            for (String id : cameras.getCameraIdList()) {
                CameraCharacteristics c = cameras.getCameraCharacteristics(id);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                Integer level = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                if (facing == null || level == null) continue;
                String[] levels = {"limited", "full", "legacy", "level3", "external"};
                JSONObject camera = new JSONObject().put("id", id).put("facing", facing == 0 ? "front" : facing == 1 ? "back" : "external");
                if (level >= 0 && level < levels.length) camera.put("hardware_level", levels[level]);
                inventory.put(camera);
            }
            return inventory;
        });
        readers.put("sensors.inventory", () -> {
            JSONArray sensors = new JSONArray();
            for (Sensor s : activity.getSystemService(SensorManager.class).getSensorList(Sensor.TYPE_ALL))
                sensors.put(new JSONObject().put("type", s.getType()).put("name", s.getName()).put("min_delay_us", s.getMinDelay())
                    .put("max_delay_us", s.getMaxDelay()).put("range", s.getMaximumRange()).put("resolution", s.getResolution()));
            return sensors;
        });
        Intent battery = activity.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        readers.put("power.battery_percent", () -> battery == null || battery.getIntExtra(BatteryManager.EXTRA_SCALE, 0) <= 0 ? null
            : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
        readers.put("power.temperature_c", () -> battery == null || !battery.hasExtra(BatteryManager.EXTRA_TEMPERATURE) ? null
            : battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0);
        readers.put("power.battery_state", () -> {
            if (battery == null) return null;
            switch (battery.getIntExtra(BatteryManager.EXTRA_STATUS, 0)) {
                case 2: return "charging"; case 3: return "discharging"; case 4: return "not_charging"; case 5: return "full"; default: return null;
            }
        });
        readers.put("power.charging_source", () -> {
            if (battery == null) return null;
            switch (battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                case 0: return "none"; case 1: return "ac"; case 2: return "usb"; case 4: return "wireless"; default: return null;
            }
        });
        PowerManager power = activity.getSystemService(PowerManager.class);
        readers.put("power.thermal_status", power::getCurrentThermalStatus);
        readers.put("power.power_save", power::isPowerSaveMode);
        readers.put("drm.security_level", () -> {
            try (MediaDrm drm = new MediaDrm(UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"))) {
                return drm.getPropertyString("securityLevel");
            }
        });
        // Local Wi-Fi device labels have no public API mapping to network SSIDs; leave unmapped.
        // The unchanged legacy wifi.ssid row already observes the connected network safely.
        readers.put("radio.bluetooth_name", () -> {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            return adapter == null ? null : adapter.getName();
        });
        readers.put("radio.wifi_state", () -> activity.getSystemService(WifiManager.class).isWifiEnabled() ? "enabled" : "disabled");
        readers.put("radio.bluetooth_state", () -> {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            return adapter == null ? null : adapter.isEnabled() ? "enabled" : "disabled";
        });
        TelephonyManager telephony = activity.getSystemService(TelephonyManager.class);
        readers.put("carrier.slots", telephony::getPhoneCount);
        readers.put("carrier.country", () -> {
            String country = telephony.getSimCountryIso(); return country.isEmpty() ? null : country.toUpperCase(Locale.ROOT);
        });
        readers.put("carrier.mcc", () -> numeric(telephony.getSimOperator(), true));
        readers.put("carrier.mnc", () -> numeric(telephony.getSimOperator(), false));
        readers.put("carrier.sim_state", () -> {
            switch (telephony.getSimState()) { case 1: return "absent"; case 2: return "pin_required"; case 3: return "puk_required"; case 5: return "ready"; default: return null; }
        });
        readers.put("packages.installed", () -> {
            JSONArray installed = new JSONArray();
            for (String name : new String[]{"android", "com.android.vending", "com.google.android.gms", "com.google.android.gsf", "com.google.android.webview", "com.android.chrome"}) {
                try {
                    android.content.pm.PackageInfo info = activity.getPackageManager().getPackageInfo(name, 0);
                    installed.put(new JSONObject().put("package", name).put("version_name", info.versionName).put("version_code", info.getLongVersionCode()));
                } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { /* Caller visibility only. */ }
            }
            return installed.length() == 0 ? null : installed;
        });
        readers.put("webview.provider", () -> WebView.getCurrentWebViewPackage() == null ? null : WebView.getCurrentWebViewPackage().packageName);
        readers.put("masked.package_list", () -> {
            android.content.pm.PackageManager pm = activity.getPackageManager();
            try {
                for (android.content.pm.PackageInfo entry : pm.getInstalledPackages(0)) {
                    if (entry != null && entry.packageName != null
                            && entry.packageName.startsWith("dev.cyclone.cloak")) {
                        // The probe and the publisher companion are the diagnostics
                        // tooling itself, not concealed state; either flavor of the
                        // probe and the exact companion id are always visible here.
                        if (entry.packageName.equals("dev.cyclone.cloak")
                                || entry.packageName.startsWith("dev.cyclone.cloak.probe")) continue;
                        return "FOUND:" + entry.packageName;
                    }
                }
            } catch (RuntimeException denied) {
                return "DENIED:" + denied.getClass().getSimpleName();
            }
            return "NOT_PRESENT";
        });
        readers.put("masked.maps", () -> {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader("/proc/self/maps"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String lower = line.toLowerCase(java.util.Locale.ROOT);
                    if (lower.contains("cyclone_cloak") || lower.contains("libhook")) {
                        return "FOUND:" + line;
                    }
                }
            } catch (java.io.IOException denied) {
                return "DENIED:" + denied.getClass().getSimpleName();
            }
            return "NOT_PRESENT";
        });
        readers.put("masked.services", () -> {
            java.lang.Process process;
            try {
                process = Runtime.getRuntime().exec(new String[]{"sh", "-c", "dumpsys service --user 0 | grep -ci cloak || true"});
            } catch (java.io.IOException denied) {
                return "DENIED:" + denied.getClass().getSimpleName();
            }
            try (java.io.BufferedReader out = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                StringBuilder listing = new StringBuilder();
                String line;
                while ((line = out.readLine()) != null) listing.append(line);
                process.waitFor();
                String value = listing.toString().trim();
                if (!value.isEmpty() && !value.equals("0")) return "FOUND:" + value;
                return "NOT_PRESENT";
            } catch (java.lang.InterruptedException | java.io.IOException ioError) {
                return "DENIED:" + ioError.getClass().getSimpleName();
            }
        });
        readers.put("webview.version", () -> WebView.getCurrentWebViewPackage() == null ? null : WebView.getCurrentWebViewPackage().versionName);
        readers.put("webview.default_ua", () -> android.webkit.WebSettings.getDefaultUserAgent(activity));
        readers.put("device.fingerprint", () -> Build.FINGERPRINT);
        readers.put("device.version_release", () -> Build.VERSION.RELEASE);
        readers.put("device.version_incremental", () -> Build.VERSION.INCREMENTAL);
        readers.put("device.build_date_utc", () -> Build.TIME);
        readers.put("device.bootloader", () -> Build.VERSION.SDK_INT >= 14 ? Build.BOOTLOADER : null);
        readers.put("device.baseband", () -> Build.RADIO == null ? null : Build.RADIO);
        readers.put("environment.language", () -> Locale.getDefault().getLanguage());
        readers.put("environment.country", () -> Locale.getDefault().getCountry());
        readers.put("environment.timezone", () -> TimeZone.getDefault().getID());
        readers.put("environment.input_method", () -> Settings.Secure.getString(activity.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD));
        readers.put("environment.accessibility_enabled", () -> Settings.Secure.getInt(activity.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 0);
        java.util.Set<String> allowedReaders = new java.util.HashSet<>();
        JSONArray contractFields = contractTable.getJSONArray("fields");
        for (int i = 0; i < contractFields.length(); i++) {
            JSONObject field = contractFields.getJSONObject(i);
            if (!field.isNull("surface") && !field.isNull("reader_key")) allowedReaders.add(field.getString("reader_key"));
        }
        // Self-visibility readers survive the allowlist filter: a missing row
        // proves nothing, so their three reader entries are never removed.
        for (String masked : new String[] {"masked.package_list", "masked.maps", "masked.services"})
            allowedReaders.add(masked);
        readers.keySet().retainAll(allowedReaders);
        JSONObject fields;
        try (java.io.InputStream stream = activity.getAssets().open("reality-fields.json")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            fields = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        }
        JSONArray rows = new JSONArray();
        java.util.TreeSet<String> sections = new java.util.TreeSet<>(); fields.keys().forEachRemaining(sections::add);
        for (String section : sections) {
            JSONArray names = fields.getJSONArray(section);
            JSONObject entry = profile.optJSONObject("reality") == null ? null : profile.getJSONObject("reality").optJSONObject(section);
            JSONObject expected = entry != null && "known".equals(entry.optString("availability")) ? entry.optJSONObject("values") : null;
            for (int i = 0; i < names.length(); i++) {
                String name = names.getString(i); String key = section + "." + name;
                // Wi-Fi's network SSID has different semantics from a local device name.
                Object value = key.equals("radio.wifi_name") ? null : expected == null ? null : expected.opt(name);
                for (int j = 0; j < contractFields.length(); j++) {
                    JSONObject field = contractFields.getJSONObject(j);
                    if (field.optString("reader_key").equals(key) && !field.isNull("linked_base_path"))
                        value = LinkedParityReport.at(profile, field.getString("linked_base_path"));
                }
                rows.put(RealityReport.row("reality." + key, value, readers.get(key)));
            }
        }
        // Emitted after contract rows so the report shows masked results last;
        // each row is always OBSERVED (value) or BLOCKED (denial), never absent.
        for (String masked : new String[] {"masked.package_list", "masked.maps", "masked.services"})
            rows.put(RealityReport.row("reality." + masked, null, readers.get(masked)));
        Map<String, RealityReport.Reader> linked = new HashMap<>();
        for (int i = 0; i < contractFields.length(); i++) {
            JSONObject field = contractFields.getJSONObject(i);
            if (!field.isNull("linked_base_path")) linked.put(field.getString("field_path"), readers.get(field.getString("reader_key")));
        }
        linked.put("linked.simOperator", telephony::getSimOperator);
        linked.put("linked.activeModemCount", () -> {
            if (Build.VERSION.SDK_INT < 30) throw new NoSuchMethodException("ACTIVE_MODEM_COUNT_API_30");
            return telephony.getActiveModemCount();
        });
        linked.put("linked.supportedModes", readers.get("panel.modes"));
        linked.put("diagnostic.active_mode", () -> {
            Display.Mode mode = display.getMode();
            return new JSONObject().put("width", mode.getPhysicalWidth()).put("height", mode.getPhysicalHeight())
                .put("refresh_hz", mode.getRefreshRate());
        });
        linked.put("diagnostic.supported_modes", readers.get("panel.modes"));
        linked.put("diagnostic.refresh_rate_hz", display::getRefreshRate);
        return new JSONObject().put("format", 1).put("rows", rows).put("linked_parity", LinkedParityReport.build(profile, contractTable, linked));
    }
    private static String numeric(String operator, boolean mcc) {
        return operator != null && operator.matches("[0-9]{5,6}") ? mcc ? operator.substring(0, 3) : operator.substring(3) : null;
    }

    /**
     * Authoring-only snapshot: run every reader once and keep the raw value.
     * This is uncensored-side tooling for fixture authoring; it does not
     * replace the digest-only validation report and must never be used in
     * place of one. Result shape: {reader_id: raw_value, ...} plus
     * captured_at_ms and reader provenance.
     */
    @android.annotation.SuppressLint("MissingPermission")
    static JSONObject authoringSnapshot(Activity activity) throws Exception {
        Map<String, RealityReport.Reader> readers = new HashMap<>();
        readers.put("device.manufacturer", () -> Build.MANUFACTURER);
        readers.put("device.brand", () -> Build.BRAND);
        readers.put("device.model", () -> Build.MODEL);
        readers.put("device.product", () -> Build.PRODUCT);
        readers.put("device.device", () -> Build.DEVICE);
        readers.put("device.hardware", () -> Build.HARDWARE);
        readers.put("device.fingerprint", () -> Build.FINGERPRINT);
        readers.put("device.version_release", () -> Build.VERSION.RELEASE);
        readers.put("device.sdk_int", () -> Build.VERSION.SDK_INT);
        readers.put("device.security_patch", () -> Build.VERSION.SECURITY_PATCH);
        readers.put("device.build_id", () -> Build.ID);
        readers.put("device.version_incremental", () -> Build.VERSION.INCREMENTAL);
        readers.put("device.build_date_utc", () -> Build.TIME / 1000);
        readers.put("device.first_api_level", () -> Integer.parseInt(ProbeActivity.authoringProperty("ro.product.first_api_level")));
        readers.put("device.bootloader", () -> Build.BOOTLOADER);
        readers.put("device.baseband", Build::getRadioVersion);
        readers.put("identifiers.android_id", () -> Settings.Secure.getString(activity.getContentResolver(), Settings.Secure.ANDROID_ID));
        readers.put("identifiers.serial", Build::getSerial);
        TelephonyManager authoringTelephony = activity.getSystemService(TelephonyManager.class);
        readers.put("telephony.sim_slot_count", authoringTelephony::getPhoneCount);
        readers.put("telephony.carrier_name", authoringTelephony::getSimOperatorName);
        readers.put("telephony.mcc", () -> numeric(authoringTelephony.getSimOperator(), true));
        readers.put("telephony.mnc", () -> numeric(authoringTelephony.getSimOperator(), false));
        readers.put("telephony.network_type_property", () -> ProbeActivity.authoringProperty("gsm.network.type"));
        Display authoringDisplay = activity.getWindowManager().getDefaultDisplay();
        readers.put("display.width", () -> authoringDisplay.getMode().getPhysicalWidth());
        readers.put("display.height", () -> authoringDisplay.getMode().getPhysicalHeight());
        readers.put("display.refresh_rate_hz", () -> authoringDisplay.getMode().getRefreshRate());
        readers.put("display.density", () -> {
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            authoringDisplay.getRealMetrics(metrics); return metrics.densityDpi;
        });
        readers.put("locale.language", () -> Locale.getDefault().getLanguage());
        readers.put("locale.country", () -> Locale.getDefault().getCountry());
        readers.put("locale.timezone", () -> TimeZone.getDefault().getID());
        readers.put("webview.default_ua", () -> android.webkit.WebSettings.getDefaultUserAgent(activity));
        readers.put("compute.gpu", () -> {
            // Real GL context required for GL_RENDERER; borrow the pbuffer path used by RuntimeEnvelopeReport.
            return RuntimeEnvelopeReport.gpu();
        });
        readers.put("memory.free_storage_gib", () -> new StatFs(activity.getFilesDir().getPath()).getAvailableBytes() / 1073741824.0);
        readers.put("sensors.inventory", () -> {
            JSONArray sensors = new JSONArray();
            for (Sensor s : activity.getSystemService(SensorManager.class).getSensorList(Sensor.TYPE_ALL))
                sensors.put(new JSONObject().put("type", s.getType()).put("name", s.getName()).put("min_delay_us", s.getMinDelay())
                    .put("max_delay_us", s.getMaxDelay()).put("range", s.getMaximumRange()).put("resolution", s.getResolution()));
            return sensors;
        });
        readers.put("packages.installed", () -> {
            JSONArray installed = new JSONArray();
            for (String name : new String[]{"android", "com.android.vending", "com.google.android.gms", "com.google.android.gsf", "com.google.android.webview", "com.android.chrome"}) {
                try {
                    android.content.pm.PackageInfo info = activity.getPackageManager().getPackageInfo(name, 0);
                    installed.put(new JSONObject().put("package", name).put("version_name", info.versionName).put("version_code", info.getLongVersionCode()));
                } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { /* Caller visibility only. */ }
            }
            return installed.length() == 0 ? null : installed;
        });
        readers.put("webview.provider", () -> WebView.getCurrentWebViewPackage() == null ? null : WebView.getCurrentWebViewPackage().packageName);
        readers.put("webview.version", () -> WebView.getCurrentWebViewPackage() == null ? null : WebView.getCurrentWebViewPackage().versionName);
        readers.put("webview.user_agent", () -> {
            WebView v = new WebView(activity);
            try { return v.getSettings().getUserAgentString(); } finally { v.destroy(); }
        });
        JSONObject values = new JSONObject();
        for (Map.Entry<String, RealityReport.Reader> entry : readers.entrySet()) {
            Object raw;
            try { raw = entry.getValue().read(); }
            catch (Throwable error) {
                values.put(entry.getKey(), new JSONObject().put("error", ProbeReportPolicy.failureReason(error)));
                continue;
            }
            values.put(entry.getKey(), raw);
        }
        return new JSONObject()
            .put("format", "authoring-raw")
            .put("captured_at_ms", System.currentTimeMillis())
            .put("captured_sdk_int", Build.VERSION.SDK_INT)
            .put("captured_host_fingerprint", Build.FINGERPRINT)
            .put("captured_host_version_release", Build.VERSION.RELEASE)
            .put("values", values);
    }
}
