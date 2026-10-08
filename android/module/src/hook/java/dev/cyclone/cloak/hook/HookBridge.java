package dev.cyclone.cloak.hook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;

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
 * identifiers.mac, identifiers.bt_mac, identifiers.serial, telephony.sim_slot_count.
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

    private static volatile Method sPropGet;

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
    }

    private static void installSettings() throws ClassNotFoundException {
        Class<?> settings = Class.forName("android.provider.Settings$Secure", false, HookBridge.class.getClassLoader());
        Class<?> resolver = Class.forName("android.content.ContentResolver", false, HookBridge.class.getClassLoader());
        hookWith(settings, "getString", new IdentityHook(KIND_ANDROID_ID), resolver, String.class);
        hookWith(settings, "getStringForUser", new IdentityHook(KIND_ANDROID_ID), resolver, String.class, int.class);
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
                return country.isEmpty() ? null : country.toLowerCase();
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

    private static final class IdentityHook extends MethodHook {
        private final int kind;

        IdentityHook(int kind) {
            this.kind = kind;
        }

        @Override public void beforeCall(Pine.CallFrame frame) {
            int slot = 0;
            if (frame.args != null && frame.args.length > 0 && frame.args[0] instanceof Integer) {
                slot = (Integer) frame.args[0];
            }
            frame.setResult(resolve(kind, slot));
        }
    }
}
