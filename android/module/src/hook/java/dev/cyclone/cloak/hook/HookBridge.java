package dev.cyclone.cloak.hook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import android.hardware.camera2.CameraCharacteristics;
import android.util.Size;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import top.canyie.pine.Pine;
import top.canyie.pine.PineConfig;
import top.canyie.pine.callback.MethodHook;

/**
 * In-process bridge that installs the Java framework callbacks for a scoped app.
 * Loaded by the native module from the DEX embedded in libcloak.so. Every value
 * comes from the prop table the module already renders, read through the hooked
 * SystemProperties, so the bridge adds no data path of its own.
 *
 * Vault fields wired here (coverage ledger): identifiers.android_id,
 * identifiers.imei_primary, identifiers.imei_secondary, identifiers.sim_serial,
 * identifiers.mac, identifiers.bt_mac, identifiers.serial, identifiers.widevine_id,
 * identifiers.advertising_id, telephony.sim_slot_count and the composed
 * user agent. The GSF read path is served through a guarded ContentResolver
 * query rewrite; the advertising id needs the Play services SDK present.
 */
public final class HookBridge {
    private static final String PROP_IMEI0 = "cloak.imei0";
    private static final String PROP_IMEI1 = "cloak.imei1";
    private static final String PROP_SIM_SERIAL = "cloak.sim_serial0";
    private static final String PROP_ANDROID_ID = "cloak.android_id";
    private static final String PROP_WIFI_MAC = "cloak.wifi_mac";
    private static final String PROP_BT_MAC = "cloak.bt_mac";
    private static final String PROP_CARRIER_ALPHA = "gsm.operator.alpha";
    private static final String PROP_CARRIER_NUMERIC = "gsm.operator.numeric";
    private static final String PROP_SIM_ALPHA = "gsm.sim.operator.alpha";
    private static final String PROP_SIM_NUMERIC = "gsm.sim.operator.numeric";
    private static final String PROP_SIM_SLOT_COUNT = "cloak.sim_slot_count";
    private static final String PROP_SERIAL = "ro.serialno";
    private static final String PROP_COUNTRY = "persist.sys.country";
    private static final String ANDROID_ID_KEY = "android_id"; // Settings.Secure.ANDROID_ID
    private static final String PROP_UA = "cloak.ua";
    private static final String PROP_WIDEVINE = "cloak.widevine_id";
    private static final String PROP_ADV_ID = "cloak.advertising_id";
    private static final String PROP_GSF = "cloak.gsf_id";

    // Hook kinds. Every identity getter always sets a result, so a call can
    // never fall through to the host device's real value.
    private static final int KIND_IMEI = 0;        // slot-aware: getImei/getMeid/getDeviceId
    private static final int KIND_PRIMARY = 1;     // single vault value (SIM serial)
    private static final int KIND_IMSI = 2;        // carrier numeric + serial tail
    private static final int KIND_ALPHA = 3;       // carrier name
    private static final int KIND_NUMERIC = 4;     // carrier mcc+mnc
    private static final int KIND_COUNTRY = 5;     // network/sim country ISO
    private static final int KIND_SLOT_COUNT = 6;  // sim_slot_count
    private static final int KIND_ANDROID_ID = 7;
    private static final int KIND_WIFI_MAC = 8;
    private static final int KIND_BT_MAC = 9;
    private static final int KIND_SERIAL = 10;
    private static final int KIND_UA = 11;
    private static final int KIND_NETWORK_TYPE = 12;
    private static final int KIND_REFRESH_RATE = 13;

    private static volatile Method sPropGet;
    private static final java.util.Set<Class<?>> sUaHooked =
            java.util.Collections.synchronizedSet(new java.util.HashSet<Class<?>>());

    private HookBridge() { }

    /** Called from the native module right after the scoped process specializes. */
    public static void init(String appDataDir, byte[] pineLib) {
        try {
            File dir = new File(appDataDir, "code_cache/cloak_hooks");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                log("cannot create hook lib dir " + dir);
                return;
            }
            File so = new File(dir, "libpine.so");
            writeIfChanged(so, pineLib);
            PineConfig.debug = false;
            PineConfig.debuggable = false;
            PineConfig.libLoader = () -> System.load(so.getAbsolutePath());
            installHooks();
            log("java callbacks installed");
        } catch (Throwable t) {
            log("bridge init failed", t);
        }
    }

    private static void installHooks() {
        try {
            installTelephony();
        } catch (Throwable t) {
            log("telephony hooks failed", t);
        }
        try {
            installSettings();
        } catch (Throwable t) {
            log("settings hooks failed", t);
        }
        try {
            installLocale();
        } catch (Throwable t) {
            log("locale hooks failed", t);
        }
        try {
            hookNoArg(Class.forName("android.net.wifi.WifiInfo", false, HookBridge.class.getClassLoader()),
                    "getMacAddress", KIND_WIFI_MAC);
        } catch (Throwable t) {
            log("wifi hooks failed", t);
        }
        try {
            hookNoArg(Class.forName("android.bluetooth.BluetoothAdapter", false, HookBridge.class.getClassLoader()),
                    "getAddress", KIND_BT_MAC);
        } catch (Throwable t) {
            log("bluetooth hooks failed", t);
        }
        try {
            hookNoArg(Class.forName("android.os.Build", false, HookBridge.class.getClassLoader()),
                    "getSerial", KIND_SERIAL);
        } catch (Throwable t) {
            log("serial hooks failed", t);
        }
        try {
            installWebView();
        } catch (Throwable t) {
            log("webview hooks failed", t);
        }
        try {
            installMediaDrm();
        } catch (Throwable t) {
            log("mediadrm hooks failed", t);
        }
        try {
            installCamera();
        } catch (Throwable t) {
            log("camera hooks failed", t);
        }
        try {
            installDisplay();
        } catch (Throwable t) {
            log("display hooks failed", t);
        }
        try {
            installApplicationHooks();
        } catch (Throwable t) {
            log("advertising id hooks failed", t);
        }
        try {
            installGsf();
        } catch (Throwable t) {
            log("gsf hooks failed", t);
        }
    }

    private static void installWebView() throws ClassNotFoundException {
        // getUserAgentString is abstract on android.webkit.WebSettings; Pine
        // cannot hook abstract methods. The concrete declaration lives on the
        // provider's runtime class, so hook getSettings() and resolve per class.
        Class<?> webView = Class.forName("android.webkit.WebView", false,
                HookBridge.class.getClassLoader());
        hookWith(webView, "getSettings", new MethodHook() {
            @Override public void afterCall(Pine.CallFrame frame) {
                Object settings = frame.getResult();
                if (settings == null) {
                    return;
                }
                Class<?> runtimeClass = settings.getClass();
                if (!sUaHooked.add(runtimeClass)) {
                    return;
                }
                Method ua = findConcreteMethod(runtimeClass, "getUserAgentString");
                if (ua == null) {
                    log("webview ua method not found on " + runtimeClass.getName());
                    return;
                }
                try {
                    Pine.hook(ua, new IdentityHook(KIND_UA));
                    log("webview ua hooked on " + runtimeClass.getName());
                } catch (Throwable t) {
                    log("webview ua hook failed", t);
                }
            }
        });
    }

    /** Walks up the class chain to the first declared, hookable method. */
    static Method findConcreteMethod(Class<?> start, String name, Class<?>... params) {
        for (Class<?> c = start; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                int mods = m.getModifiers();
                if (!java.lang.reflect.Modifier.isAbstract(mods)
                        && !java.lang.reflect.Modifier.isNative(mods)) {
                    return m;
                }
            } catch (NoSuchMethodException ignored) {
                // Keep walking toward the concrete declaration.
            }
        }
        return null;
    }

    private static void installMediaDrm() throws ClassNotFoundException {
        Class<?> mediaDrm = Class.forName("android.media.MediaDrm", false,
                HookBridge.class.getClassLoader());
        // getPropertyByteArray(String propertyName): only the device identity
        // read is redirected; every other property stays real.
        hookWith(mediaDrm, "getPropertyByteArray", new DeviceIdHook(), String.class);
        log("mediadrm hook installed");
    }

    /**
     * Display geometry and refresh: profile values flow through the
     * cloak.display.* property surface. Resources.getDisplayMetrics and the
     * Display out-parameter reads are rewritten after the real call so
     * unchanged fields keep framework behavior.
     */
    private static void installCamera() throws ClassNotFoundException {
        Class<?> cameraManager = Class.forName("android.hardware.camera2.CameraManager", false,
                HookBridge.class.getClassLoader());
        // Facing stays real so camera apps keep working; the camera2 read path is
        // re-rendered at the two keys the profile owns (level + pixel array).
        hookWith(cameraManager, "getCameraCharacteristics", new CameraCharacteristicsHook(), String.class);
        Class<?> characteristics = Class.forName("android.hardware.camera2.CameraCharacteristics", false,
                HookBridge.class.getClassLoader());
        hookWith(characteristics, "get", new CameraMetadataGetHook(), CameraCharacteristics.Key.class);
        log("camera hooks installed");
    }

    private static void installDisplay() throws ClassNotFoundException {
        Class<?> resources = Class.forName("android.content.res.Resources", false,
                HookBridge.class.getClassLoader());
        hookWith(resources, "getDisplayMetrics", new DisplayMetricsHook(), new Class<?>[0]);
        Class<?> display = Class.forName("android.view.Display", false,
                HookBridge.class.getClassLoader());
        hookWith(display, "getMetrics", new DisplayMetricsHook(), android.util.DisplayMetrics.class);
        hookWith(display, "getRealMetrics", new DisplayMetricsHook(), android.util.DisplayMetrics.class);
        hookNoArg(display, "getRefreshRate", KIND_REFRESH_RATE);
        hookWith(display, "getMode", new DisplayModeHook(false));
        hookWith(display, "getSupportedModes", new DisplayModeHook(true));
        log("display hooks installed");
    }

    /** New return objects only: never changes DisplayInfo, hardware or the framework's mode array. */
    static android.view.Display.Mode profileMode(android.view.Display.Mode host) {
        if (host == null) return null;
        int width = parseInt(prop("cloak.display.width"), -1);
        int height = parseInt(prop("cloak.display.height"), -1);
        float refresh = -1;
        try { refresh = Float.parseFloat(prop("cloak.display.refresh")); } catch (NumberFormatException ignored) { }
        if (!Float.isFinite(refresh) || refresh <= 0) refresh = -1;
        if (width <= 0 && height <= 0 && refresh <= 0) return host;
        width = width > 0 ? width : host.getPhysicalWidth();
        height = height > 0 ? height : host.getPhysicalHeight();
        refresh = refresh > 0 ? refresh : host.getRefreshRate();
        if (width == host.getPhysicalWidth() && height == host.getPhysicalHeight()
                && Float.compare(refresh, host.getRefreshRate()) == 0) return host;
        try {
            Class<?> mode = android.view.Display.Mode.class;
            try {
                Constructor<?> constructor = mode.getDeclaredConstructor(int.class, int.class, int.class,
                        float.class, float[].class, int[].class);
                constructor.setAccessible(true);
                float[] alternatives = (float[]) mode.getMethod("getAlternativeRefreshRates").invoke(host);
                int[] hdr = (int[]) mode.getMethod("getSupportedHdrTypes").invoke(host);
                return (android.view.Display.Mode) constructor.newInstance(host.getModeId(), width, height,
                        refresh, alternatives.clone(), hdr.clone());
            } catch (NoSuchMethodException olderFramework) {
                Constructor<?> constructor = mode.getDeclaredConstructor(int.class, int.class, int.class, float.class);
                constructor.setAccessible(true);
                return (android.view.Display.Mode) constructor.newInstance(host.getModeId(), width, height, refresh);
            }
        } catch (Throwable error) {
            log("display mode construction unavailable", error);
            return host;
        }
    }

    static android.view.Display.Mode[] profileModes(android.view.Display.Mode[] host, android.view.Display.Mode current) {
        if (host == null || current == null) return host;
        float requestedRefresh = -1;
        try { requestedRefresh = Float.parseFloat(prop("cloak.display.refresh")); } catch (NumberFormatException ignored) { }
        if (parseInt(prop("cloak.display.width"), -1) <= 0 && parseInt(prop("cloak.display.height"), -1) <= 0
                && (!Float.isFinite(requestedRefresh) || requestedRefresh <= 0)) return host;
        android.view.Display.Mode selected = profileMode(current);
        android.view.Display.Mode[] result = host.clone();
        for (int i = 0; i < result.length; i++) {
            if (result[i].getModeId() == selected.getModeId()) {
                if (result[i].getPhysicalWidth() == selected.getPhysicalWidth()
                        && result[i].getPhysicalHeight() == selected.getPhysicalHeight()
                        && Float.compare(result[i].getRefreshRate(), selected.getRefreshRate()) == 0) return host;
                result[i] = selected; return result;
            }
        }
        result = java.util.Arrays.copyOf(result, result.length + 1);
        result[result.length - 1] = selected;
        return result;
    }

    private static final class DisplayModeHook extends MethodHook {
        private final boolean supported;
        DisplayModeHook(boolean supported) { this.supported = supported; }
        @Override public void afterCall(Pine.CallFrame frame) {
            if (frame.hasThrowable()) return;
            Object result = frame.getResult();
            if (!supported && result instanceof android.view.Display.Mode) {
                frame.setResult(profileMode((android.view.Display.Mode) result));
            } else if (supported && result instanceof android.view.Display.Mode[] && frame.thisObject instanceof android.view.Display) {
                android.view.Display display = (android.view.Display) frame.thisObject;
                // getMode may already be rewritten. Compare against each original mode, not object identity.
                android.view.Display.Mode current = display.getMode();
                android.view.Display.Mode[] modes = (android.view.Display.Mode[]) result;
                if (current != null) {
                    for (android.view.Display.Mode mode : modes) {
                        if (mode.getModeId() == current.getModeId()) { current = mode; break; }
                    }
                }
                frame.setResult(profileModes(modes, current));
            }
        }
    }

    /**
     * Rewrites width/height/density fields of a DisplayMetrics. Handles both
     * returning methods (Resources.getDisplayMetrics) and void out-parameter
     * methods (Display.getMetrics/getRealMetrics write args[0]).
     */
    private static final class DisplayMetricsHook extends MethodHook {
        @Override public void afterCall(Pine.CallFrame frame) {
            Object result = frame.getResult();
            if (!(result instanceof android.util.DisplayMetrics)
                    && frame.args != null && frame.args.length > 0
                    && frame.args[0] instanceof android.util.DisplayMetrics) {
                result = frame.args[0];
            }
            if (!(result instanceof android.util.DisplayMetrics)) {
                return;
            }
            android.util.DisplayMetrics metrics = (android.util.DisplayMetrics) result;
            int width = parseInt(prop("cloak.display.width"), -1);
            int height = parseInt(prop("cloak.display.height"), -1);
            int density = parseInt(prop("ro.sf.lcd_density"), -1);
            if (width > 0) metrics.widthPixels = width;
            if (height > 0) metrics.heightPixels = height;
            if (density > 0) metrics.densityDpi = density;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static void installAdvertisingId(ClassLoader appLoader) throws ClassNotFoundException {
        Class<?> client = Class.forName(
                "com.google.android.gms.ads.identifier.AdvertisingIdClient", false,
                appLoader);
        hookWith(client, "getAdvertisingIdInfo", new AdvertisingIdHook(appLoader), android.content.Context.class);
    }

    private static void installApplicationHooks() throws ClassNotFoundException {
        // App SDK classes are unavailable in the bridge's boot-loader parent
        // at postAppSpecialize time. Install their callbacks before app startup.
        Class<?> instrumentation = Class.forName("android.app.Instrumentation");
        hookWith(instrumentation, "callApplicationOnCreate", new MethodHook() {
            @Override public void beforeCall(Pine.CallFrame frame) {
                android.app.Application app = (android.app.Application) frame.args[0];
                try {
                    installAdvertisingId(app.getClassLoader());
                } catch (ClassNotFoundException ignored) {
                    // The SDK is optional; this app does not use it.
                }
                applyLocaleDefaults();
            }
        }, android.app.Application.class);
    }

    static void applyLocaleDefaults() {
        String language = prop("persist.sys.language");
        String country = prop(PROP_COUNTRY);
        if (!language.isEmpty()) Locale.setDefault(Locale.forLanguageTag(
            language + (country.isEmpty() ? "" : "-" + country)));
        String zone = prop("persist.sys.timezone");
        if (!zone.isEmpty()) TimeZone.setDefault(TimeZone.getTimeZone(zone));
    }

    private static void installGsf() throws ClassNotFoundException {
        Class<?> resolver = Class.forName("android.content.ContentResolver", false,
                HookBridge.class.getClassLoader());
        // The classic GSF read is a query against gservices for the android_id
        // row. Both overloads that accept a Uri are guarded; anything unusual
        // falls through to the real provider response.
        hookWith(resolver, "query", new GsfQueryHook(false),
                android.net.Uri.class, String[].class, String.class, String[].class, String.class);
        // The bundle overload is absent on some framework levels; hookWith
        // treats a missing method as a no-op.
        hookWith(resolver, "query", new GsfQueryHook(true),
                android.net.Uri.class, String[].class, android.os.Bundle.class,
                android.os.CancellationSignal.class);
    }

    private static void installTelephony() throws ClassNotFoundException {
        Class<?> tm = Class.forName("android.telephony.TelephonyManager", false, HookBridge.class.getClassLoader());
        hookTm(tm, "getImei", KIND_IMEI);
        hookTm(tm, "getMeid", KIND_IMEI);
        hookTm(tm, "getDeviceId", KIND_IMEI);
        hookTm(tm, "getSimSerialNumber", KIND_PRIMARY);
        hookTm(tm, "getSubscriberId", KIND_IMSI);
        hookTm(tm, "getNetworkOperatorName", KIND_ALPHA);
        hookTm(tm, "getNetworkOperator", KIND_NUMERIC);
        hookTm(tm, "getSimOperatorName", KIND_ALPHA);
        hookTm(tm, "getSimOperator", KIND_NUMERIC);
        hookTm(tm, "getNetworkCountryIso", KIND_COUNTRY);
        hookTm(tm, "getSimCountryIso", KIND_COUNTRY);
        hookNoArg(tm, "getPhoneCount", KIND_SLOT_COUNT);
        hookNoArg(tm, "getActiveModemCount", KIND_SLOT_COUNT);
        hookTm(tm, "getNetworkType", KIND_NETWORK_TYPE);
        hookTm(tm, "getDataNetworkType", KIND_NETWORK_TYPE);
    }

    private static void installSettings() throws ClassNotFoundException {
        Class<?> settings = Class.forName("android.provider.Settings$Secure", false, HookBridge.class.getClassLoader());
        Class<?> resolver = Class.forName("android.content.ContentResolver", false, HookBridge.class.getClassLoader());
        hookWith(settings, "getString", new IdentityHook(KIND_ANDROID_ID), resolver, String.class);
        hookWith(settings, "getStringForUser", new IdentityHook(KIND_ANDROID_ID), resolver, String.class, int.class);
        hookWith(resolver, "query", new SettingsQueryHook(), android.net.Uri.class,
                String[].class, String.class, String[].class, String.class);
        hookWith(resolver, "query", new SettingsQueryHook(), android.net.Uri.class,
                String[].class, String.class, String[].class, String.class,
                android.os.CancellationSignal.class);
    }

    private static void installLocale() throws ClassNotFoundException {
        Class<?> resources = Class.forName("android.content.res.Resources");
        hookWith(resources, "getConfiguration", new MethodHook() {
            @Override public void afterCall(Pine.CallFrame frame) {
                String language = prop("persist.sys.language");
                String country = prop(PROP_COUNTRY);
                if (language.isEmpty() || frame.hasThrowable()) return;
                android.content.res.Configuration source =
                    (android.content.res.Configuration) frame.getResult();
                if (source == null) return;
                android.content.res.Configuration result = new android.content.res.Configuration(source);
                result.setLocale(Locale.forLanguageTag(language + (country.isEmpty() ? "" : "-" + country)));
                frame.setResult(result);
            }
        });
        hookWith(TimeZone.class, "getDefault", new MethodHook() {
            @Override public void beforeCall(Pine.CallFrame frame) {
                String zone = prop("persist.sys.timezone");
                if (!zone.isEmpty()) frame.setResult(TimeZone.getTimeZone(zone));
            }
        });
    }

    private static void hookTm(Class<?> clazz, String name, int kind) {
        hookNoArg(clazz, name, kind);
        hookWithParams(clazz, name, kind, int.class);
    }

    private static void hookNoArg(Class<?> clazz, String name, int kind) {
        hookWithParams(clazz, name, kind);
    }

    private static void hookWithParams(Class<?> clazz, String name, int kind, Class<?>... params) {
        hookWith(clazz, name, new IdentityHook(kind), params);
    }

    private static void hookWith(Class<?> clazz, String name, MethodHook hook, Class<?>... params) {
        try {
            Method method = clazz.getDeclaredMethod(name, params);
            Pine.hook(method, hook);
        } catch (NoSuchMethodException ignored) {
            // Older builds legitimately lack some overloads.
        } catch (Throwable t) {
            log("hook " + clazz.getName() + "." + name + " failed", t);
        }
    }

    private static String resolve(int kind, int slot) {
        switch (kind) {
            case KIND_IMEI: {
                String value = slot >= 1 ? prop(PROP_IMEI1) : prop(PROP_IMEI0);
                return orNull(value);
            }
            case KIND_PRIMARY:
                return orNull(prop(PROP_SIM_SERIAL));
            case KIND_IMSI: {
                String numeric = firstNonEmpty(prop(PROP_SIM_NUMERIC), prop(PROP_CARRIER_NUMERIC));
                String serial = prop(PROP_SIM_SERIAL);
                if (numeric.isEmpty() || serial.isEmpty()) return null;
                int msin = 15 - numeric.length();
                if (msin < 1) return null;
                return numeric + tail(serial, msin);
            }
            case KIND_ALPHA:
                return orNull(firstNonEmpty(prop(PROP_CARRIER_ALPHA), prop(PROP_SIM_ALPHA)));
            case KIND_NUMERIC:
                return orNull(firstNonEmpty(prop(PROP_CARRIER_NUMERIC), prop(PROP_SIM_NUMERIC)));
            case KIND_COUNTRY: {
                String country = prop(PROP_COUNTRY);
                return country.isEmpty() ? null : country.toLowerCase(Locale.ROOT);
            }
            case KIND_SLOT_COUNT: {
                String count = prop(PROP_SIM_SLOT_COUNT);
                return count.isEmpty() ? "1" : count;
            }
            case KIND_ANDROID_ID:
                return orNull(prop(PROP_ANDROID_ID));
            case KIND_WIFI_MAC:
                return orNull(prop(PROP_WIFI_MAC));
            case KIND_BT_MAC:
                return orNull(prop(PROP_BT_MAC));
            case KIND_SERIAL:
                return orNull(prop(PROP_SERIAL));
            case KIND_UA:
                return orNull(prop(PROP_UA));
            case KIND_REFRESH_RATE:
                return orNull(prop("cloak.display.refresh"));
            case KIND_NETWORK_TYPE:
                switch (prop("gsm.network.type")) {
                    case "NR": return "20";
                    case "LTE": return "13";
                    case "HSPA": return "10";
                    case "UMTS": return "3";
                    case "GSM": return "16";
                    default: return "0";
                }
            default:
                return null;
        }
    }

    private static String prop(String key) {
        try {
            if (sPropGet == null) {
                synchronized (HookBridge.class) {
                    if (sPropGet == null) {
                        Class<?> sp = Class.forName("android.os.SystemProperties", false,
                                HookBridge.class.getClassLoader());
                        sPropGet = sp.getDeclaredMethod("get", String.class);
                    }
                }
            }
            return (String) sPropGet.invoke(null, key);
        } catch (Throwable t) {
            return "";
        }
    }

    private static String orNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static String firstNonEmpty(String a, String b) {
        return a.isEmpty() ? b : a;
    }

    private static String tail(String value, int length) {
        if (length >= value.length()) return value;
        return value.substring(value.length() - length);
    }

    private static void writeIfChanged(File target, byte[] bytes) throws IOException {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(bytes);
            out.getFD().sync();
        }
        if (!tmp.renameTo(target)) {
            if (target.exists() && !target.delete()) throw new IOException("cannot replace " + target);
            if (!tmp.renameTo(target)) throw new IOException("cannot move " + tmp);
        }
    }

    private static void log(String message) {
        log(message, null);
    }

    private static void log(String message, Throwable t) {
        try {
            Class<?> logClass = Class.forName("android.util.Log", false, HookBridge.class.getClassLoader());
            Method i = logClass.getDeclaredMethod("i", String.class, String.class);
            i.invoke(null, "CloakModule", "[cloak-hook] " + message + (t == null ? "" : ": " + t));
        } catch (Throwable ignored) {
        }
    }

    /** DeviceIdHook: MediaDrm DEVICE_ID reads answer from the widevine vault. */
    private static final class DeviceIdHook extends MethodHook {
        @Override public void beforeCall(Pine.CallFrame frame) {
            if (frame.args == null || frame.args.length < 1
                    || (!"deviceUniqueId".equals(frame.args[0])
                    && !"deviceId".equals(frame.args[0]))) {
                return;
            }
            String vault = prop(PROP_WIDEVINE);
            // One-shot evidence line: distinguishes "hook never fires" from
            // "hook fires with an empty vault" on real devices.
            if (!vault.isEmpty()) {
                log("widevine identity served");
            } else {
                log("widevine hook fired but vault empty");
            }
            frame.setResult(hexBytes(vault));
        }
    }

    /** AdvertisingIdHook: rebuilds the SDK Info record from the vault value. */
    private static final class AdvertisingIdHook extends MethodHook {
        private final ClassLoader appLoader;

        AdvertisingIdHook(ClassLoader appLoader) { this.appLoader = appLoader; }

        @Override public void beforeCall(Pine.CallFrame frame) {
            String value = prop(PROP_ADV_ID);
            if (value.isEmpty()) {
                return;
            }
            try {
                Class<?> info = Class.forName(
                        "com.google.android.gms.ads.identifier.AdvertisingIdClient$Info",
                        false, appLoader);
                Constructor<?> ctor = info.getConstructor(String.class, boolean.class);
                frame.setResult(ctor.newInstance(value, Boolean.FALSE));
            } catch (Throwable t) {
                log("advertising info rebuild failed", t);
            }
        }
    }

    /** GsfQueryHook: guarded rewrite of the gservices android_id row. */
    private static final class GsfQueryHook extends MethodHook {
        private final boolean bundleOverload;

        GsfQueryHook(boolean bundleOverload) {
            this.bundleOverload = bundleOverload;
        }

        @Override public void afterCall(Pine.CallFrame frame) {
            try {
                if (!(frame.getResult() instanceof android.database.Cursor)) {
                    return;
                }
                Object uriArg = frame.args != null && frame.args.length > 0 ? frame.args[0] : null;
                if (!(uriArg instanceof android.net.Uri)) {
                    return;
                }
                android.net.Uri uri = (android.net.Uri) uriArg;
                if (!"com.google.android.gsf.gservices".equals(uri.getAuthority())) {
                    return;
                }
                if (frame.args == null || frame.args.length < 4
                        || !(frame.args[3] instanceof String[])
                        || !matchesAndroidIdQuery((String[]) frame.args[3])) {
                    return;
                }
                android.database.Cursor original = (android.database.Cursor) frame.getResult();
                if (!original.moveToFirst()) {
                    return;
                }
                String[] columns = original.getColumnNames();
                int nameAt = -1;
                int valueAt = -1;
                for (int i = 0; i < columns.length; i++) {
                    if (ANDROID_ID_KEY.equals(columns[i])) nameAt = i;
                    if ("value".equals(columns[i])) valueAt = i;
                }
                if (nameAt < 0 || valueAt < 0) {
                    return;
                }
                android.database.MatrixCursor replacement =
                        new android.database.MatrixCursor(columns);
                Object[] row = new Object[columns.length];
                for (int i = 0; i < columns.length; i++) {
                    row[i] = i == valueAt ? prop(PROP_GSF) : original.getString(i);
                }
                replacement.addRow(row);
                original.close();
                frame.setResult(replacement);
            } catch (Throwable t) {
                log("gsf rewrite failed", t);
            }
        }

        private boolean matchesAndroidIdQuery(String[] names) {
            return names != null && names.length == 1 && ANDROID_ID_KEY.equals(names[0]);
        }
    }

    /**
     * CameraCharacteristicsHook: maps every characteristics object handed out by
     * getCameraCharacteristics to its profile spec. Facing is read real (the get
     * hook never touches it), so the occurrence order stays host-stable.
     */
    private static final class CameraCharacteristicsHook extends MethodHook {
        /** First-seen camera id -> occurrence index of its facing, in query order. */
        private final Map<String, Integer> occurrences = new HashMap<>();
        private final Map<Integer, Integer> seenPerFacing = new HashMap<>();


        @Override public void afterCall(Pine.CallFrame frame) {
            Object result = frame.getResult();
            if (!(result instanceof CameraCharacteristics)
                    || frame.args == null || frame.args.length < 1
                    || !(frame.args[0] instanceof String)) {
                return;
            }
            int count = parsePropInt(prop("cloak.camera.count"));
            if (count <= 0) {
                return;
            }
            CameraCharacteristics characteristics = (CameraCharacteristics) result;
            Integer realFacing = null;
            try {
                realFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
            } catch (Throwable t) {
                return;
            }
            if (realFacing == null) {
                return;
            }
            String id = (String) frame.args[0];
            Integer occurrence = occurrences.get(id);
            if (occurrence == null) {
                occurrence = seenPerFacing.getOrDefault(realFacing, 0);
                seenPerFacing.put(realFacing, occurrence + 1);
                occurrences.put(id, occurrence);
            }
            int[] spec = cameraSpec(realFacing, occurrence, count);
            if (spec == null) {
                return;
            }
            CameraCharacteristicsHookLookups.put(characteristics, spec);
        }

        /** nth published spec with the given facing, or null when absent or malformed. */
        static int[] cameraSpec(int facing, int occurrence, int count) {
            int seen = 0;
            for (int i = 0; i < count; i++) {
                String prefix = "cloak.camera." + i + ".";
                if (parsePropInt(prop(prefix + "facing")) != facing) {
                    continue;
                }
                if (seen++ != occurrence) {
                    continue;
                }
                int level = parsePropInt(prop(prefix + "level"));
                int width = parsePropInt(prop(prefix + "w"));
                int height = parsePropInt(prop(prefix + "h"));
                if (level < 0 || level > 4 || width <= 0 || height <= 0) {
                    return null;
                }
                return new int[]{level, width, height};
            }
            return null;
        }
    }

    /** Serves the profile's level and pixel array from mapped characteristics. */
    private static final class CameraMetadataGetHook extends MethodHook {
        @Override public void beforeCall(Pine.CallFrame frame) {
            if (frame.thisObject == null || frame.args == null || frame.args.length < 1
                    || !(frame.args[0] instanceof CameraCharacteristics.Key)) {
                return;
            }
            int[] spec = CameraCharacteristicsHookLookups.specFor(frame.thisObject);
            if (spec == null) {
                return;
            }
            CameraCharacteristics.Key<?> key = (CameraCharacteristics.Key<?>) frame.args[0];
            if (CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL.equals(key)) {
                frame.setResult((Integer) spec[0]);
            } else if (CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE.equals(key)) {
                frame.setResult(new Size(spec[1], spec[2]));
            }
        }
    }

    /** Indirection so the lookup map stays reachable from the static get hook. */
    private static final class CameraCharacteristicsHookLookups {
        private static final Map<Integer, int[]> SPECS = new HashMap<>();

        static void put(Object characteristics, int[] spec) {
            if (SPECS.size() > 128) {
                SPECS.clear();
            }
            SPECS.put(System.identityHashCode(characteristics), spec);
        }

        static int[] specFor(Object characteristics) {
            return SPECS.get(System.identityHashCode(characteristics));
        }
    }

    private static int parsePropInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    private static byte[] hexBytes(String hex) {
        if (hex == null || hex.isEmpty()) return null;
        int len = hex.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            out[i] = (byte) ((Character.digit(hex.charAt(2 * i), 16) << 4)
                    | Character.digit(hex.charAt(2 * i + 1), 16));
        }
        return out;
    }

    /** Only the single Android-ID row; never redirects general settings queries. */
    private static final class SettingsQueryHook extends MethodHook {
        @Override public void beforeCall(Pine.CallFrame frame) {
            if (frame.args == null || frame.args.length < 5
                    || !(frame.args[0] instanceof android.net.Uri)
                    || !(frame.args[2] instanceof String)
                    || !(frame.args[3] instanceof String[])) return;
            android.net.Uri uri = (android.net.Uri) frame.args[0];
            String[] names = (String[]) frame.args[3];
            if (!"settings".equals(uri.getAuthority()) || !"/secure".equals(uri.getPath())
                    || !((String) frame.args[2]).matches("\\s*name\\s*=\\s*\\?\\s*")
                    || names.length != 1 || !ANDROID_ID_KEY.equals(names[0])) return;
            String[] columns = frame.args[1] == null ? new String[]{"name", "value"}
                : (String[]) frame.args[1];
            Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if ("name".equals(columns[i])) row[i] = ANDROID_ID_KEY;
                else if ("value".equals(columns[i])) row[i] = orNull(prop(PROP_ANDROID_ID));
                else return;
            }
            android.database.MatrixCursor cursor = new android.database.MatrixCursor(columns);
            cursor.addRow(row);
            frame.setResult(cursor);
        }
    }

    private static final class IdentityHook extends MethodHook {
        private final int kind;

        IdentityHook(int kind) {
            this.kind = kind;
        }

        @Override public void beforeCall(Pine.CallFrame frame) {
            // Every other Settings.Secure key must retain its provider result.
            if (kind == KIND_ANDROID_ID && (frame.args == null || frame.args.length < 2
                    || !ANDROID_ID_KEY.equals(frame.args[1]))) return;
            int slot = 0;
            if (frame.args != null && frame.args.length > 0 && frame.args[0] instanceof Integer) {
                slot = (Integer) frame.args[0];
            }
            String value = resolve(kind, slot);
            if (kind == KIND_SLOT_COUNT || kind == KIND_NETWORK_TYPE) {
                frame.setResult(Integer.valueOf(value));
            } else if (kind == KIND_REFRESH_RATE) {
                frame.setResult(Float.valueOf(value));
            } else {
                frame.setResult(value);
            }
        }
    }
}
