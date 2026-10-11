package dev.cyclone.cloak.hook;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
}
