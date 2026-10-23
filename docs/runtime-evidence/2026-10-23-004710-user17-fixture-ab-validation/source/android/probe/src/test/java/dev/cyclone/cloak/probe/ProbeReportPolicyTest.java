package dev.cyclone.cloak.probe;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProbeReportPolicyTest {
    @Test public void optionalValuesAreObservedEvenWhenNull() {
        assertEquals("OBSERVED", ProbeReportPolicy.status(false, false, false));
        assertEquals("OBSERVED", ProbeReportPolicy.status(false, true, false));
    }
    @Test public void expectedValuesDistinguishMissingMismatchAndMatch() {
        assertEquals("MISSING", ProbeReportPolicy.status(true, true, false));
        assertEquals("FAIL", ProbeReportPolicy.status(true, false, false));
        assertEquals("PASS", ProbeReportPolicy.status(true, false, true));
    }
    @Test public void fixtureBStaysBAndStandardDefaultStaysA() {
        assertEquals("b", ProbeReportPolicy.fixture("b", true, false, true));
        assertEquals("a", ProbeReportPolicy.fixture(null, false, false, false));
    }
    @Test public void externalProfilesNeverDefaultToA() {
        assertEquals("external", ProbeReportPolicy.fixture(null, true, false, false));
        assertEquals("external", ProbeReportPolicy.fixture("external", true, false, false));
    }
    @Test public void mislabeledFixtureIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> ProbeReportPolicy.fixture("a", true, false, true));
        assertEquals("FIXTURE_PROFILE_MISMATCH", error.getMessage());
        assertThrows(IllegalArgumentException.class,
            () -> ProbeReportPolicy.fixture("b", true, true, false));
    }
    @Test public void bodyMatchIgnoresFormattingButChecksValuesBeyondId() throws Exception {
        JSONObject a = new JSONObject("{\"id\":\"same\",\"device\":{\"model\":\"a\",\"sdk\":33}}");
        JSONObject b = new JSONObject("{\"device\":{\"sdk\":33,\"model\":\"a\"},\"id\":\"same\"}");
        assertEquals(ProbeReportPolicy.canonical(a), ProbeReportPolicy.canonical(b));
        b.getJSONObject("device").put("model", "b");
        assertNotEquals(ProbeReportPolicy.canonical(a), ProbeReportPolicy.canonical(b));
    }
    @Test public void wifiPermissionGatesAreSpecificToRead() {
        assertArrayEquals(new String[]{"android.permission.ACCESS_WIFI_STATE"}, ProbeReportPolicy.permissions("wifi.serviceMac", 36));
        assertArrayEquals(new String[]{"android.permission.ACCESS_WIFI_STATE", "android.permission.ACCESS_FINE_LOCATION"}, ProbeReportPolicy.permissions("wifi.ssid", 36));
        assertArrayEquals(ProbeReportPolicy.permissions("wifi.ssid", 36), ProbeReportPolicy.permissions("wifi.bssid", 36));
        assertEquals(0, ProbeReportPolicy.permissions("wifi.objectMac", 36).length);
    }
    @Test public void bluetoothPermissionRespectsApiLevel() {
        assertEquals(0, ProbeReportPolicy.permissions("bluetooth.name", 30).length);
        assertArrayEquals(new String[]{"android.permission.BLUETOOTH_CONNECT"}, ProbeReportPolicy.permissions("bluetooth.name", 31));
        assertArrayEquals(ProbeReportPolicy.permissions("bluetooth.name", 36), ProbeReportPolicy.permissions("bluetooth.address", 36));
    }
    @Test public void deniedWifiStateBlocksServiceButNotObjectRead() {
        assertEquals("PERMISSION_DENIED:android.permission.ACCESS_WIFI_STATE",
            ProbeReportPolicy.permissionFailure("wifi.serviceMac", 36, p -> false));
        assertNull(ProbeReportPolicy.permissionFailure("wifi.objectMac", 36, p -> false));
    }
    @Test public void deniedLocationDoesNotBlockMacButDoesBlockSsid() {
        assertNull(ProbeReportPolicy.permissionFailure("wifi.serviceMac", 36, p -> p.endsWith("ACCESS_WIFI_STATE")));
        assertEquals("PERMISSION_DENIED:android.permission.ACCESS_FINE_LOCATION",
            ProbeReportPolicy.permissionFailure("wifi.ssid", 36, p -> p.endsWith("ACCESS_WIFI_STATE")));
    }
    @Test public void grantedPermissionsAllowReadAndBluetoothDenialIsExplicit() {
        assertNull(ProbeReportPolicy.permissionFailure("wifi.ssid", 36, p -> true));
        assertNull(ProbeReportPolicy.permissionFailure("bluetooth.name", 36, p -> true));
        assertEquals("PERMISSION_DENIED:android.permission.BLUETOOTH_CONNECT",
            ProbeReportPolicy.permissionFailure("bluetooth.name", 36, p -> false));
    }
}
