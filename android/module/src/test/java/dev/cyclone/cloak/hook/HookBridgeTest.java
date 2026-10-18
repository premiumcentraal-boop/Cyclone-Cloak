package dev.cyclone.cloak.hook;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

/** Real callback bodies with controlled properties; does not claim ART hook installation. */
public class HookBridgeTest {
    private static final Map<String, String> PROPS = new HashMap<>();
    public static String property(String name) { return PROPS.getOrDefault(name, ""); }

    @Before public void properties() throws Exception {
        PROPS.clear();
        Field getter = HookBridge.class.getDeclaredField("sPropGet");
        getter.setAccessible(true);
        getter.set(null, HookBridgeTest.class.getMethod("property", String.class));
    }

    private MethodHook identity(String kind) throws Exception {
        Field number = HookBridge.class.getDeclaredField(kind);
        number.setAccessible(true);
        Class<?> callback = Class.forName(HookBridge.class.getName() + "$IdentityHook");
        Constructor<?> constructor = callback.getDeclaredConstructor(int.class);
        constructor.setAccessible(true);
        return (MethodHook) constructor.newInstance(number.getInt(null));
    }

    private Pine.CallFrame frame(Object... args) throws Exception {
        // HookRecord does not install a hook; it just supplies the target Member.
        Pine.HookRecord record = new Pine.HookRecord(
            HookBridgeTest.class.getMethod("property", String.class), 0L);
        return new Pine.CallFrame(record, null, args);
    }

    @Test public void unrelatedSettingsKeysAreNotIntercepted() throws Throwable {
        PROPS.put("cloak.android_id", "1234567890abcdef");
        Pine.CallFrame call = frame(null, "accessibility_enabled");
        identity("KIND_ANDROID_ID").beforeCall(call);
        assertNull(call.getResult());
        Field early = Pine.CallFrame.class.getDeclaredField("returnEarly");
        early.setAccessible(true);
        assertFalse(early.getBoolean(call));
    }

    @Test public void androidIdReadsUseProfileValue() throws Throwable {
        PROPS.put("cloak.android_id", "1234567890abcdef");
        Pine.CallFrame call = frame(null, "android_id");
        identity("KIND_ANDROID_ID").beforeCall(call);
        assertEquals("1234567890abcdef", call.getResult());
    }

    @Test public void modemCountHasTheFrameworkIntegerReturnType() throws Throwable {
        PROPS.put("cloak.sim_slot_count", "2");
        Pine.CallFrame call = frame();
        identity("KIND_SLOT_COUNT").beforeCall(call);
        assertTrue(call.getResult() instanceof Integer);
        assertEquals(2, call.getResult());
    }

    @Test public void imeiReadsSelectTheRequestedSlot() throws Throwable {
        PROPS.put("cloak.imei0", "PRIMARY");
        PROPS.put("cloak.imei1", "SECONDARY");
        Pine.CallFrame first = frame(0), second = frame(1);
        identity("KIND_IMEI").beforeCall(first);
        identity("KIND_IMEI").beforeCall(second);
        assertEquals("PRIMARY", first.getResult());
        assertEquals("SECONDARY", second.getResult());
    }

    @Test public void dataNetworkTypeUsesAnIntegerFrameworkConstant() throws Throwable {
        PROPS.put("gsm.network.type", "NR");
        Pine.CallFrame call = frame();
        identity("KIND_NETWORK_TYPE").beforeCall(call);
        assertEquals(20, call.getResult());
    }

    @Test public void refreshRateIsServedAsFloat() throws Throwable {
        PROPS.put("cloak.display.refresh", "120");
        Pine.CallFrame call = frame();
        identity("KIND_REFRESH_RATE").beforeCall(call);
        assertEquals(120.0f, call.getResult());
    }

    @Test public void displayMetricsRewriteTouchesOnlyProfileFields() throws Throwable {
        PROPS.put("cloak.display.width", "1080");
        PROPS.put("cloak.display.height", "2340");
        // Density intentionally absent: the field must keep its host value.
        java.lang.reflect.Method hook = HookBridge.class.getDeclaredMethod("installDisplay");
        assertNotNull(hook);
        Class<?> callback = Class.forName(HookBridge.class.getName() + "$DisplayMetricsHook");
        java.lang.reflect.Constructor<?> ctor = callback.getDeclaredConstructor();
        ctor.setAccessible(true);
        MethodHook displayHook = (MethodHook) ctor.newInstance();

        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        metrics.widthPixels = 1;
        metrics.heightPixels = 2;
        metrics.densityDpi = 3;
        Pine.CallFrame call = frame(metrics);
        call.setResult(metrics);
        displayHook.afterCall(call);
        assertEquals(1080, metrics.widthPixels);
        assertEquals(2340, metrics.heightPixels);
        assertEquals(3, metrics.densityDpi);

        Pine.CallFrame nonMetrics = frame(new Object());
        nonMetrics.setResult(new Object());
        displayHook.afterCall(nonMetrics);
        assertNotNull(nonMetrics.getResult());
    }

    @Test public void voidDisplayMethodsRewriteTheOutParameter() throws Throwable {
        PROPS.put("cloak.display.width", "1080");
        PROPS.put("cloak.display.height", "2340");
        PROPS.put("ro.sf.lcd_density", "420");
        Class<?> callback = Class.forName(HookBridge.class.getName() + "$DisplayMetricsHook");
        java.lang.reflect.Constructor<?> ctor = callback.getDeclaredConstructor();
        ctor.setAccessible(true);
        MethodHook displayHook = (MethodHook) ctor.newInstance();

        android.util.DisplayMetrics out = new android.util.DisplayMetrics();
        out.widthPixels = 1;
        out.heightPixels = 2;
        out.densityDpi = 3;
        Pine.CallFrame call = frame(out); // void getRealMetrics(out): result stays null
        call.setResult(null);
        displayHook.afterCall(call);
        assertEquals(1080, out.widthPixels);
        assertEquals(2340, out.heightPixels);
        assertEquals(420, out.densityDpi);
    }

    @Test public void widevineVaultDecodesToRawBytes() throws Throwable {
        String hex = "f2b9c7904bec4ec0755b7aec3eca856e1deb43ab9cbd564f038af27837d568d3";
        PROPS.put("cloak.widevine_id", hex);
        Class<?> callback = Class.forName(HookBridge.class.getName() + "$DeviceIdHook");
        java.lang.reflect.Constructor<?> drmCtor = callback.getDeclaredConstructor();
        drmCtor.setAccessible(true);
        MethodHook drmHook = (MethodHook) drmCtor.newInstance();
        Pine.CallFrame call = frame("deviceId");
        drmHook.beforeCall(call);
        byte[] bytes = (byte[]) call.getResult();
        assertEquals(32, bytes.length);
        StringBuilder encoded = new StringBuilder();
        for (byte b : bytes) encoded.append(String.format("%02x", b & 0xff));
        assertEquals(hex, encoded.toString());

        Pine.CallFrame other = frame("algorithms");
        drmHook.beforeCall(other);
        assertNull(other.getResult());
    }

    @Test public void widevineVaultServesDeviceUniqueIdProperty() throws Throwable {
        String hex = "aa0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
        PROPS.put("cloak.widevine_id", hex);
        Class<?> callback = Class.forName(HookBridge.class.getName() + "$DeviceIdHook");
        java.lang.reflect.Constructor<?> drmCtor = callback.getDeclaredConstructor();
        drmCtor.setAccessible(true);
        MethodHook drmHook = (MethodHook) drmCtor.newInstance();
        Pine.CallFrame call = frame("deviceUniqueId");
        drmHook.beforeCall(call);
        byte[] bytes = (byte[]) call.getResult();
        assertNotNull(bytes);
        assertEquals(32, bytes.length);
    }

    abstract static class AbstractSettings {
        public abstract String getUserAgentString();
    }

    static class ConcreteSettings extends AbstractSettings {
        @Override public String getUserAgentString() { return "concrete"; }
    }

    static class DeeperSettings extends ConcreteSettings { }

    @Test public void uaResolverFindsTheConcreteDeclaration() throws Exception {
        Method resolved = HookBridge.findConcreteMethod(DeeperSettings.class, "getUserAgentString");
        assertEquals(ConcreteSettings.class, resolved.getDeclaringClass());
        assertNull(HookBridge.findConcreteMethod(AbstractSettings.class, "getUserAgentString"));
        assertNull(HookBridge.findConcreteMethod(DeeperSettings.class, "getNothing"));
    }

    @Test public void cameraSpecsMatchByFacingOccurrence() throws Exception {
        PROPS.put("cloak.camera.count", "3");
        PROPS.put("cloak.camera.0.facing", "1");
        PROPS.put("cloak.camera.0.level", "1");
        PROPS.put("cloak.camera.0.w", "8160");
        PROPS.put("cloak.camera.0.h", "6144");
        PROPS.put("cloak.camera.1.facing", "1");
        PROPS.put("cloak.camera.1.level", "0");
        PROPS.put("cloak.camera.1.w", "4032");
        PROPS.put("cloak.camera.1.h", "3024");
        PROPS.put("cloak.camera.2.facing", "0");
        PROPS.put("cloak.camera.2.level", "0");
        PROPS.put("cloak.camera.2.w", "4000");
        PROPS.put("cloak.camera.2.h", "3000");
        Class<?> callback = Class.forName(HookBridge.class.getName() + "$CameraCharacteristicsHook");
        Method spec = callback.getDeclaredMethod("cameraSpec", int.class, int.class, int.class);
        spec.setAccessible(true);
        assertArrayEquals(new int[]{1, 8160, 6144}, (int[]) spec.invoke(null, 1, 0, 3));
        assertArrayEquals(new int[]{0, 4032, 3024}, (int[]) spec.invoke(null, 1, 1, 3));
        assertNull(spec.invoke(null, 1, 2, 3));
        assertArrayEquals(new int[]{0, 4000, 3000}, (int[]) spec.invoke(null, 0, 0, 3));
    }
    @Test public void simAndNetworkOperatorPreserveMccMncComposition() throws Throwable {
        for (String mnc : new String[]{"01", "001"}) {
            String composed = "310" + mnc;
            PROPS.put("gsm.operator.numeric", composed);
            PROPS.put("gsm.sim.operator.numeric", composed);
            Pine.CallFrame sim = frame(), network = frame();
            identity("KIND_NUMERIC").beforeCall(sim); identity("KIND_NUMERIC").beforeCall(network);
            assertEquals(composed, sim.getResult()); assertEquals(composed, network.getResult());
            PROPS.remove("gsm.operator.numeric");
            Pine.CallFrame fallback = frame(); identity("KIND_NUMERIC").beforeCall(fallback);
            assertEquals(composed, fallback.getResult());
        }
    }
    @Test public void phoneAndModemCountsUseSameBaseSlotCount() throws Throwable {
        for (String count : new String[]{"1", "2"}) {
            PROPS.put("cloak.sim_slot_count", count);
            Pine.CallFrame phone = frame(), modem = frame();
            identity("KIND_SLOT_COUNT").beforeCall(phone); identity("KIND_SLOT_COUNT").beforeCall(modem);
            assertEquals(Integer.valueOf(count), phone.getResult()); assertEquals(phone.getResult(), modem.getResult());
        }
    }
    @Test public void languageCountryTimezoneUseBaseDefaultsAndAbsencePreservesHost() {
        java.util.Locale locale = java.util.Locale.getDefault(); java.util.TimeZone zone = java.util.TimeZone.getDefault();
        try {
            PROPS.put("persist.sys.language", "de"); PROPS.put("persist.sys.country", "DE");
            PROPS.put("persist.sys.timezone", "Europe/Berlin");
            HookBridge.applyLocaleDefaults();
            assertEquals("de", java.util.Locale.getDefault().getLanguage());
            assertEquals("DE", java.util.Locale.getDefault().getCountry());
            assertEquals("Europe/Berlin", java.util.TimeZone.getDefault().getID());
            PROPS.clear(); HookBridge.applyLocaleDefaults();
            assertEquals("de", java.util.Locale.getDefault().getLanguage());
            assertEquals("Europe/Berlin", java.util.TimeZone.getDefault().getID());
        } finally { java.util.Locale.setDefault(locale); java.util.TimeZone.setDefault(zone); }
    }

}
