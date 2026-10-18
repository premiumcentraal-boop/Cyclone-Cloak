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
    @Test public void linkedRefreshComparesFrameworkFloatToIntegerExpectation() throws Exception {
        JSONObject t = new JSONObject("{\"fields\":[{\"field_path\":\"reality.panel.values.refresh_hz\",\"linked_base_path\":\"display.refresh_rate_hz\"}]}");
        Map<String,RealityReport.Reader> r=readers(); r.put("reality.panel.values.refresh_hz",()->120.0f);
        assertEquals("PASS", LinkedParityReport.build(profile(),t,r).getJSONArray("rows").getJSONObject(0).getString("status"));
        r.put("reality.panel.values.refresh_hz",()->90.0f);
        assertEquals("FAIL", LinkedParityReport.build(profile(),t,r).getJSONArray("rows").getJSONObject(0).getString("status"));
    }

    @Test public void rawDisplayDiagnosticsAreAllowlistedWithoutIdentifierLeak() throws Exception {
        Map<String,RealityReport.Reader> r=readers();
        r.put("diagnostic.active_mode",()->new JSONObject("{\"width\":1080,\"height\":2400,\"refresh_hz\":90,\"secret\":\"private\"}"));
        r.put("diagnostic.supported_modes",()->new JSONArray("[{\"width\":1080,\"height\":2400,\"refresh_hz\":60},{\"width\":1080,\"height\":2400,\"refresh_hz\":90}]"));
        r.put("diagnostic.refresh_rate_hz",()->90f);
        JSONObject report=LinkedParityReport.build(profile(),table(),r), d=report.getJSONObject("display_diagnostics");
        assertEquals(90,d.getJSONObject("active_mode").getJSONObject("value").getInt("refresh_hz"));
        assertEquals(2,d.getJSONObject("supported_modes").getJSONArray("value").length());
        assertEquals(90,d.getJSONObject("refresh_rate_hz").getDouble("value"),0);
        assertFalse(report.toString().contains("private")); assertFalse(report.toString().contains("31001"));
        assertEquals("OBSERVED",d.getJSONObject("active_mode").getString("status"));
    }
    @Test public void rawDisplayFailuresRemainPreciseAndNeverLeakMessages() throws Exception {
        Map<String,RealityReport.Reader> r=readers();
        r.put("diagnostic.active_mode",()->{throw new SecurityException("private");});
        r.put("diagnostic.refresh_rate_hz",()->Float.NaN);
        JSONObject d=LinkedParityReport.displayDiagnostics(r);
        assertEquals("BLOCKED",d.getJSONObject("active_mode").getString("status"));
        assertEquals("SecurityException",d.getJSONObject("active_mode").getString("reason"));
        assertEquals("MISSING",d.getJSONObject("refresh_rate_hz").getString("status"));
        assertEquals("UNMAPPED",d.getJSONObject("supported_modes").getString("status"));
        assertFalse(d.toString().contains("private"));
    }

}
