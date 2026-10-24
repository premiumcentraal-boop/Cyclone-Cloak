package dev.cyclone.cloak.probe;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class RealityReportTest {
    @Test public void hashMatchIsOnlyAMatchNotVerification() throws Exception {
        JSONObject row = RealityReport.row("reality.radio.bluetooth_name", "private-name", () -> "private-name");
        assertEquals("PASS", row.getString("status")); assertEquals("HASH_MATCH_ONLY", row.getString("reason"));
        assertFalse(row.toString().contains("private-name"));
    }
    @Test public void mismatchesNeverExposeValues() throws Exception {
        JSONObject row = RealityReport.row("reality.webview.provider", "private-expected", () -> "private-actual");
        assertEquals("FAIL", row.getString("status"));
        assertFalse(row.toString().contains("private-"));
    }
    @Test public void unknownMissingAndUnmappedAreDistinct() throws Exception {
        assertEquals("OBSERVED", RealityReport.row("reality.power.power_save", null, () -> false).getString("status"));
        assertEquals("MISSING", RealityReport.row("reality.power.power_save", true, () -> null).getString("status"));
        assertEquals("UNMAPPED", RealityReport.row("reality.compute.gpu", "Mali", null).getString("status"));
    }
    @Test public void denialAndApiErrorsStayPreciseAndRedacted() throws Exception {
        JSONObject denied = RealityReport.row("reality.radio.bluetooth_name", null, () -> { throw new SecurityException("android.permission.BLUETOOTH_CONNECT private-name"); });
        assertEquals("BLOCKED", denied.getString("status")); assertEquals("SecurityException:android.permission.BLUETOOTH_CONNECT", denied.getString("reason"));
        assertFalse(denied.toString().contains("private-name"));
        assertEquals("MISSING", RealityReport.row("reality.drm.security_level", null, () -> { throw new IllegalStateException("private-data"); }).getString("status"));
    }
    @Test public void hashIsStableAcrossObjectKeyOrder() throws Exception {
        assertEquals(RealityReport.hash(new JSONObject("{\"a\":1,\"b\":2}")), RealityReport.hash(new JSONObject("{\"b\":2,\"a\":1}")));
    }
    @Test public void equivalentNumericRefreshRatesMatchWithoutCoercingTypes() throws Exception {
        assertEquals("PASS", RealityReport.row("reality.panel.refresh_hz", 90, () -> 90.0f).getString("status"));
        assertEquals("FAIL", RealityReport.row("reality.panel.refresh_hz", 90, () -> 90.01f).getString("status"));
        assertEquals("FAIL", RealityReport.row("number", 1, () -> true).getString("status"));
        assertEquals("FAIL", RealityReport.row("number", 90, () -> "90").getString("status"));
        assertEquals("MISSING", RealityReport.row("number", 90, () -> Float.NaN).getString("status"));
        assertEquals(RealityReport.hash(120), RealityReport.hash(120.0d));
    }

    @Test public void maskedCompletenessRowsAlwaysProduceAResult() throws Exception {
        // Completeness gate: the three self-visibility rows must always be emitted.
        for (String id : new String[] {"reality.masked.package_list", "reality.masked.maps", "reality.masked.services"}) {
            JSONObject present = RealityReport.row(id, null, () -> "NOT_PRESENT");
            assertEquals("OBSERVED", present.getString("status"));
            assertEquals(id, present.getString("id"));
            JSONObject found = RealityReport.row(id, null, () -> "FOUND:x");
            assertEquals("OBSERVED", found.getString("status"));
            assertFalse(found.toString().contains("FOUND:x"));
            JSONObject denied = RealityReport.row(id, null, () -> { throw new SecurityException("private"); });
            assertEquals("BLOCKED", denied.getString("status"));
        }
    }

}
