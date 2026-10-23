package dev.cyclone.cloak.probe;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class RuntimeEnvelopeReportTest {
    @Test public void equalNestedNumbersAndInventoryOrderingDoNotProduceFalseFailures() throws Exception {
        JSONArray expected = new JSONArray("[{\"type\":1,\"range\":10},{\"type\":2,\"range\":20}]");
        JSONArray actual = new JSONArray("[{\"range\":20.0,\"type\":2},{\"range\":10.0,\"type\":1}]");
        JSONObject row = RuntimeEnvelopeReport.row("envelope.sensors.inventory", expected, () -> actual);
        assertEquals("PASS", row.getString("status"));
        JSONObject report = RuntimeEnvelopeReport.report(new JSONArray().put(row));
        assertEquals("NOT_VERIFIED", report.getString("verification"));
        assertEquals("HASH_MATCH_ONLY", row.getString("reason"));
    }
    @Test public void deniedAndMissingReadsNeverBecomePasses() throws Exception {
        JSONObject denied = RuntimeEnvelopeReport.row("envelope.sensors.inventory", "private-value", () -> {
            throw new SecurityException("android.permission.BODY_SENSORS private-value");
        });
        assertEquals("BLOCKED", denied.getString("status"));
        assertEquals("SecurityException:android.permission.BODY_SENSORS", denied.getString("reason"));
        assertFalse(denied.toString().contains("private-value"));
        assertEquals("MISSING", RuntimeEnvelopeReport.row("envelope.compute.gpuRenderer", "x", () -> null).getString("status"));
        assertEquals("OBSERVED", RuntimeEnvelopeReport.row("envelope.memory.totalMem", null, () -> 8000000000L).getString("status"));
    }
    @Test public void rawValuesUnknownFieldsDuplicateIdsAndFalsePassesAreRejected() throws Exception {
        JSONObject row = RuntimeEnvelopeReport.row("envelope.display.realSize", 1, () -> 1);
        for (String key : new String[]{"raw", "token", "actual", "unexpected"}) {
            JSONObject changed = new JSONObject(row.toString()).put(key, "secret");
            assertThrows(IllegalArgumentException.class, () -> RuntimeEnvelopeReport.report(new JSONArray().put(changed)));
        }
        assertThrows(IllegalArgumentException.class, () -> RuntimeEnvelopeReport.report(new JSONArray().put(row).put(row)));
        JSONObject changed = new JSONObject(row.toString()).put("expected_sha256", JSONObject.NULL);
        assertThrows(IllegalArgumentException.class, () -> RuntimeEnvelopeReport.report(new JSONArray().put(changed)));
    }
    @Test public void expectedAvailabilityDoesNotGuessUnknownOrUnavailableFacts() throws Exception {
        for (String state : new String[]{"unknown", "unavailable"}) {
            JSONObject p = new JSONObject("{\"reality\":{\"compute\":{\"values\":{\"gpu\":\"do-not-use\"}}}}");
            p.getJSONObject("reality").getJSONObject("compute").put("availability", state);
            assertNull(RuntimeEnvelopeReport.expected(p, "compute", "gpu"));
        }
        assertNull(RuntimeEnvelopeReport.expected(new JSONObject(), "compute", "gpu"));
    }
    @Test public void oldProbeSectionsAndOrderedDisplayTupleRemainUntouched() throws Exception {
        JSONObject legacy = new JSONObject("{\"rows\":[],\"linked_parity\":{\"rows\":[]}}");
        String before = legacy.toString();
        JSONObject extension = RuntimeEnvelopeReport.report(new JSONArray());
        legacy.put("runtime_envelope", extension); legacy.remove("runtime_envelope");
        assertEquals(before, legacy.toString());
        assertEquals("FAIL", RuntimeEnvelopeReport.row("envelope.display.realSize", new JSONArray("[1080,2400]"),
            () -> new JSONArray("[2400,1080]")).getString("status"));
    }
}
