package dev.cyclone.cloak.hook;

import org.junit.Test;
import top.canyie.pine.PineConfig;
import static org.junit.Assert.*;

/** Engine configuration is independent of the SDK presented to the scoped app. */
public class RuntimeSdkTest {
    @Test public void debugProbeUsesDebuggableArtConfiguration() {
        int previous = PineConfig.sdkLevel;
        boolean debug = PineConfig.debuggable;
        try {
            PineConfig.debuggable = false;
            HookBridge.configureRuntime(36, (1 << 8) | 1);
            assertTrue(PineConfig.debuggable);
            assertEquals(36, PineConfig.sdkLevel);
        } finally { PineConfig.sdkLevel = previous; PineConfig.debuggable = debug; }
    }

    @Test public void releaseAndUnrelatedFlagsDoNotEnableDebuggableMode() {
        int previous = PineConfig.sdkLevel;
        boolean debug = PineConfig.debuggable;
        try {
            PineConfig.debuggable = true;
            HookBridge.configureRuntime(36, 1 | (1 << 7));
            assertFalse(PineConfig.debuggable);
        } finally { PineConfig.sdkLevel = previous; PineConfig.debuggable = debug; }
    }

    @Test public void android16HostDoesNotUseAndroid14ProfileSdk() {
        int previous = PineConfig.sdkLevel;
        try {
            PineConfig.sdkLevel = 34;
            HookBridge.configureRuntimeSdk(36);
            assertEquals(36, PineConfig.sdkLevel);
        } finally { PineConfig.sdkLevel = previous; }
    }

    @Test public void android16HostDoesNotUseAndroid13ProfileSdk() {
        int previous = PineConfig.sdkLevel;
        try {
            PineConfig.sdkLevel = 33;
            HookBridge.configureRuntimeSdk(36);
            assertEquals(36, PineConfig.sdkLevel);
        } finally { PineConfig.sdkLevel = previous; }
    }

    @Test public void unsupportedHostNeverReconfiguresEngine() {
        int previous = PineConfig.sdkLevel;
        try {
            for (int sdk : new int[]{0, -1, 25}) {
                try { HookBridge.configureRuntimeSdk(sdk); fail("Expected invalid host SDK rejection"); }
                catch (IllegalArgumentException expected) {
                    assertEquals("HOST_RUNTIME_SDK_UNAVAILABLE", expected.getMessage());
                }
                assertEquals(previous, PineConfig.sdkLevel);
            }
        } finally { PineConfig.sdkLevel = previous; }
    }
}
