package dev.cyclone.cloak.probe;

import java.lang.reflect.InvocationTargetException;
import org.junit.Test;
import static org.junit.Assert.*;

/** Test-only seam types, never bundled as counterfeit Play Services classes. */
public class AdvertisingIdReadTest {
    public static class Client {
        public static Info getAdvertisingIdInfo(Object context) { return new Info(); }
    }
    public static class Info { public String getId() { return "measured-id"; } }
    public static class Denied {
        public static Info getAdvertisingIdInfo(Object context) { throw new SecurityException("denied"); }
    }
    private ClassLoader loader(Class<?> result) {
        return new ClassLoader() {
            @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
                assertEquals("com.google.android.gms.ads.identifier.AdvertisingIdClient", name);
                if (result == null) throw new ClassNotFoundException(name);
                return result;
            }
        };
    }
    @Test public void invokesSdkAndReturnsMeasuredValue() throws Exception {
        assertEquals("measured-id", AdvertisingIdRead.read(loader(Client.class), Object.class, new Object()));
    }
    @Test public void absentSdkRemainsBlockedInsteadOfFabricatingValue() {
        ClassNotFoundException error = assertThrows(ClassNotFoundException.class,
            () -> AdvertisingIdRead.read(loader(null), Object.class, new Object()));
        assertEquals("SDK_OR_API_ABSENT", ProbeReportPolicy.failureReason(error));
    }
    @Test public void deniedSdkPreservesCauseForBlockedReason() {
        InvocationTargetException error = assertThrows(InvocationTargetException.class,
            () -> AdvertisingIdRead.read(loader(Denied.class), Object.class, new Object()));
        assertTrue(error.getCause() instanceof SecurityException);
        assertEquals("SecurityException", ProbeReportPolicy.failureReason(error));
    }
    @Test public void serviceErrorsAndPermissionReasonsStayExplicitAndRedacted() {
        assertEquals("IOException", ProbeReportPolicy.failureReason(new InvocationTargetException(new java.io.IOException("private-service-detail"))));
        assertEquals("SecurityException:android.permission.ACCESS_WIFI_STATE",
            ProbeReportPolicy.failureReason(new SecurityException("private-value android.permission.ACCESS_WIFI_STATE")));
    }
}
