package dev.cyclone.cloak.hook;

import android.view.Display;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Real framework return objects; callback installation on ART remains device-verification work. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34, 35}, manifest = Config.NONE)
public class DisplayModeParityTest {
    private static final Map<String, String> PROPS = new HashMap<>();
    public static String property(String name) { return PROPS.getOrDefault(name, ""); }
    @Before public void setup() throws Exception {
        PROPS.clear();
        Field getter = HookBridge.class.getDeclaredField("sPropGet"); getter.setAccessible(true);
        getter.set(null, DisplayModeParityTest.class.getMethod("property", String.class));
    }
    private Display.Mode mode(int id, int w, int h, float hz) throws Exception {
        Constructor<Display.Mode> constructor = Display.Mode.class.getDeclaredConstructor(int.class, int.class, int.class, float.class);
        constructor.setAccessible(true); return constructor.newInstance(id, w, h, hz);
    }
    @Test public void modeUsesBasePropertiesAndPreservesHostObject() throws Exception {
        Display.Mode host = mode(3, 1440, 3120, 90);
        PROPS.put("cloak.display.width", "1080"); PROPS.put("cloak.display.height", "2400"); PROPS.put("cloak.display.refresh", "120");
        Display.Mode actual = HookBridge.profileMode(host);
        assertEquals(1080, actual.getPhysicalWidth()); assertEquals(2400, actual.getPhysicalHeight()); assertEquals(120, actual.getRefreshRate(), 0);
        assertEquals(3, actual.getModeId()); assertEquals(1440, host.getPhysicalWidth()); assertEquals(90, host.getRefreshRate(), 0);
    }
    @Test public void supportedModesIncludeSameProfileModeWithoutDuplicateIds() throws Exception {
        Display.Mode host = mode(3, 1440, 3120, 90), other = mode(4, 720, 1560, 60);
        Display.Mode[] original = {host, other}; PROPS.put("cloak.display.width", "1080");
        Display.Mode[] actual = HookBridge.profileModes(original, host);
        assertEquals(2, actual.length); assertEquals(1080, actual[0].getPhysicalWidth()); assertSame(other, actual[1]); assertSame(host, original[0]);
        assertEquals(HookBridge.profileMode(host), actual[0]);
        Display.Mode alreadyRewritten = HookBridge.profileMode(host);
        assertEquals(1080, HookBridge.profileModes(new Display.Mode[]{other}, alreadyRewritten)[1].getPhysicalWidth());
    }
    @Test public void absentInvalidAndPartialPropertiesAreNoOpOrPreserveMissingValues() throws Exception {
        Display.Mode host = mode(1, 1440, 3120, 90); Display.Mode[] all = {host};
        assertSame(host, HookBridge.profileMode(host)); assertSame(all, HookBridge.profileModes(all, host));
        PROPS.put("cloak.display.refresh", "NaN"); PROPS.put("cloak.display.width", "-1");
        assertSame(host, HookBridge.profileMode(host)); assertSame(all, HookBridge.profileModes(all, host));
        PROPS.put("cloak.display.height", "2400");
        Display.Mode changed = HookBridge.profileMode(host);
        assertEquals(1440, changed.getPhysicalWidth()); assertEquals(2400, changed.getPhysicalHeight()); assertEquals(90, changed.getRefreshRate(), 0);
        assertNull(HookBridge.profileMode(null)); assertNull(HookBridge.profileModes(null, host));
    }
    @Test public void actualCallbacksAndFrameworkFailuresArePreserved() throws Throwable {
        android.hardware.display.DisplayManager manager = org.robolectric.RuntimeEnvironment.getApplication().getSystemService(android.hardware.display.DisplayManager.class);
        Display display = manager.getDisplay(Display.DEFAULT_DISPLAY); Display.Mode host = display.getMode();
        PROPS.put("cloak.display.width", "1080"); PROPS.put("cloak.display.height", "2400"); PROPS.put("cloak.display.refresh", "120");
        Class<?> callback = Class.forName(HookBridge.class.getName() + "$DisplayModeHook");
        Constructor<?> ctor = callback.getDeclaredConstructor(boolean.class); ctor.setAccessible(true);
        top.canyie.pine.callback.MethodHook single = (top.canyie.pine.callback.MethodHook)ctor.newInstance(false);
        top.canyie.pine.callback.MethodHook multiple = (top.canyie.pine.callback.MethodHook)ctor.newInstance(true);
        top.canyie.pine.Pine.HookRecord record = new top.canyie.pine.Pine.HookRecord(Display.class.getMethod("getMode"), 0L);
        top.canyie.pine.Pine.CallFrame frame = new top.canyie.pine.Pine.CallFrame(record, display, new Object[0]);
        frame.setResult(host); single.afterCall(frame);
        assertEquals(1080, ((Display.Mode)frame.getResult()).getPhysicalWidth());
        assertEquals(120f, ((Display.Mode)frame.getResult()).getRefreshRate(), 0f);
        frame.setResult(new Display.Mode[]{host}); multiple.afterCall(frame);
        Display.Mode[] modes = (Display.Mode[])frame.getResult();
        assertEquals(1, modes.length); assertEquals(2400, modes[0].getPhysicalHeight());
        RuntimeException failure = new RuntimeException("framework"); frame.setThrowable(failure); single.afterCall(frame);
        assertSame(failure, frame.getThrowable());
    }

}
