package dev.cyclone.cloak.probe;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class LinkedParityReportTest {
    private JSONObject profile() throws Exception { return new JSONObject("{\"display\":{\"width\":1080,\"height\":2400,\"density\":420,\"refresh_rate_hz\":120},\"telephony\":{\"mcc\":\"310\",\"mnc\":\"01\",\"sim_slot_count\":2},\"reality\":{\"panel\":{\"values\":{\"width\":999}}}}"); }
    private JSONObject table() throws Exception { return new JSONObject("{\"fields\":[{\"field_path\":\"reality.panel.values.width\",\"linked_base_path\":\"display.width\"}]}"); }
    private Map<String, RealityReport.Reader> readers() {
        Map<String, RealityReport.Reader> r = new HashMap<>();
        r.put("reality.panel.values.width", () -> 1080); r.put("linked.simOperator", () -> "31001");
        r.put("linked.activeModemCount", () -> 2);
        r.put("linked.supportedModes", () -> new JSONArray("[{\"width\":1080,\"height\":2400,\"refresh_hz\":120}]"));
        return r;
    }
    @Test public void baseWinsAndOnlyHashesLeaveTheReader() throws Exception {
        JSONObject result = LinkedParityReport.build(profile(), table(), readers());
        JSONArray rows = result.getJSONArray("rows"); assertEquals(4, rows.length());
        for (int i=0; i<rows.length(); i++) assertEquals("PASS", rows.getJSONObject(i).getString("status"));
        assertFalse(result.toString().contains("31001")); assertFalse(result.toString().contains("1080"));
        assertTrue(rows.getJSONObject(3).getString("aggregate_sha256").matches("[0-9a-f]{64}"));
    }
    @Test public void missingBaseNeverPromotesRealityOrModeObservation() throws Exception {
        JSONObject p=profile(); p.getJSONObject("display").remove("width");
        JSONArray rows=LinkedParityReport.build(p,table(),readers()).getJSONArray("rows");
        assertEquals("OBSERVED",rows.getJSONObject(0).getString("status"));
        assertEquals("OBSERVED",rows.getJSONObject(3).getString("status"));
        assertEquals("BASE_MODE_INCOMPLETE",rows.getJSONObject(3).getString("reason"));
    }
    @Test public void mismatchDenialAndAbsentReaderRemainExplicit() throws Exception {
        Map<String,RealityReport.Reader> r=readers(); r.put("reality.panel.values.width",()->999);
        r.put("linked.simOperator",()->{throw new SecurityException("private text");}); r.remove("linked.activeModemCount");
        JSONArray rows=LinkedParityReport.build(profile(),table(),r).getJSONArray("rows");
        assertEquals("FAIL",rows.getJSONObject(0).getString("status"));
        assertEquals("BLOCKED",rows.getJSONObject(1).getString("status"));
        assertFalse(rows.toString().contains("private text")); assertEquals("UNMAPPED",rows.getJSONObject(2).getString("status"));
    }
    @Test public void supportedModesMustContainExactBaseTuple() throws Exception {
        Map<String,RealityReport.Reader> r=readers();
        r.put("linked.supportedModes",()->new JSONArray("[{\"width\":1080,\"height\":2400,\"refresh_hz\":60}]"));
        assertEquals("FAIL",LinkedParityReport.build(profile(),table(),r).getJSONArray("rows").getJSONObject(3).getString("status"));
        r.put("linked.supportedModes",()->new JSONArray("[{\"width\":\"bad\"}]"));
        assertEquals("MISSING",LinkedParityReport.build(profile(),table(),r).getJSONArray("rows").getJSONObject(3).getString("status"));
    }
}
