package dev.cyclone.cloak.probe;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class SimStateExpectationTest {
    private JSONObject envelope(Object expectation) throws Exception {
        Integer state = RuntimeEnvelopeReport.simStateExpectation(expectation);
        return RuntimeEnvelopeReport.report(new JSONArray()
            .put(RuntimeEnvelopeReport.row("envelope.telephony.simState", state, () -> 5))
            .put(RuntimeEnvelopeReport.row("envelope.telephony.simStateSlot0", state, () -> 5)));
    }
    @Test public void nullExpectationProducesObservationWithoutUnboxing() throws Exception {
        JSONObject report = envelope(null);
        for (int i = 0; i < 2; i++) {
            JSONObject row = report.getJSONArray("rows").getJSONObject(i);
            assertEquals("OBSERVED", row.getString("status"));
            assertTrue(row.isNull("expected_sha256"));
        }
    }
    @Test public void absentUnavailableAndMalformedExpectationsRemainUnknown() throws Exception {
        for (JSONObject profile : new JSONObject[]{new JSONObject(),
                new JSONObject("{\"reality\":{\"carrier\":{\"availability\":\"known\",\"values\":{}}}}"),
                new JSONObject("{\"reality\":{\"carrier\":{\"availability\":\"unknown\"}}}")}) {
            Object state = RuntimeEnvelopeReport.expected(profile, "carrier", "sim_state");
            assertNull(RuntimeEnvelopeReport.simStateExpectation(state));
            envelope(state);
        }
        for (Object value : new Object[]{JSONObject.NULL, "unknown", "", 5, new JSONObject()})
            assertNull(RuntimeEnvelopeReport.simStateExpectation(value));
    }
    @Test public void knownMappingsRetainTheirExactValues() throws Exception {
        String[] labels = {"absent", "pin_required", "puk_required", "ready"};
        int[] values = {1, 2, 3, 5};
        for (int i = 0; i < labels.length; i++)
            assertEquals(Integer.valueOf(values[i]), RuntimeEnvelopeReport.simStateExpectation(labels[i]));
        assertEquals("PASS", envelope("ready").getJSONArray("rows").getJSONObject(0).getString("status"));
        assertEquals("FAIL", envelope("absent").getJSONArray("rows").getJSONObject(0).getString("status"));
    }
}
