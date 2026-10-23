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
        readers.put("webview.version", () -> WebView.getCurrentWebViewPackage() == null ? null : WebView.getCurrentWebViewPackage().versionName);
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
}
