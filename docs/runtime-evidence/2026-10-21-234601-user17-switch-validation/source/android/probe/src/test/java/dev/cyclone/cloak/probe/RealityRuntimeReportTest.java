package dev.cyclone.cloak.probe;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class RealityRuntimeReportTest {
    private JSONObject table(String path, String surface) throws Exception {
        return new JSONObject().put("fields", new JSONArray().put(new JSONObject().put("field_path", path)
            .put("surface", surface == null ? JSONObject.NULL : surface).put("reason", "DESCRIPTIVE_ONLY")));
    }
    private JSONObject report(JSONObject row) throws Exception { return new JSONObject().put("rows", new JSONArray().put(row)); }
    private JSONObject profile() throws Exception { return new JSONObject().put("reality", new JSONObject()); }
    @Test public void legacyProfileHasNoRuntimeSectionRows() throws Exception {
        JSONObject r = RealityRuntimeReport.build(table("reality.inventory_id", null), new JSONObject(), report(new JSONObject()));
        assertEquals("not present", r.getString("presence")); assertEquals(0, r.getJSONArray("rows").length());
    }
    @Test public void onlyExistingComparisonCanSupplyMatch() throws Exception {
        JSONObject row = RealityReport.row("reality.radio.bluetooth_name", "private-label", () -> "private-label");
        JSONObject r = RealityRuntimeReport.build(table("reality.radio.values.bluetooth_name", "BluetoothAdapter.getName()"), profile(), report(row));
        assertEquals("PASS", r.getJSONArray("rows").getJSONObject(0).getString("observation_status"));
        assertFalse(r.toString().contains("private-label"));
        row.put("actual_sha256", "0".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> RealityRuntimeReport.build(table("reality.radio.values.bluetooth_name", "BluetoothAdapter.getName()"), profile(), report(row)));
    }
    @Test public void observationDenialAndUnavailableKeepTheirReasons() throws Exception {
        for (String status : new String[]{"OBSERVED", "BLOCKED", "MISSING", "UNMAPPED", "FAIL"}) {
            JSONObject row = RealityReport.row("reality.radio.bluetooth_name", "expected", () -> "different");
            row.put("status", status).put("reason", "PRECISE_REASON");
            JSONObject r = RealityRuntimeReport.build(table("reality.radio.values.bluetooth_name", "BluetoothAdapter.getName()"), profile(), report(row));
            JSONObject output = r.getJSONArray("rows").getJSONObject(0);
            assertEquals(status, output.getString("observation_status")); assertEquals("PRECISE_REASON", output.getString("reason"));
            assertFalse(r.toString().contains("different"));
        }
    }
    @Test public void nestedUnmeasuredAndMetadataNeverInheritParentPass() throws Exception {
        JSONObject row = RealityReport.row("reality.sensors.inventory", "sensitive", () -> "sensitive");
        for (String path : new String[]{"reality.sensors.values.inventory[].name", "reality.inventory_id"}) {
            JSONObject r = RealityRuntimeReport.build(table(path, path.contains("[]") ? "Sensor.getName()" : null), profile(), report(row));
            assertEquals("UNMAPPED", r.getJSONArray("rows").getJSONObject(0).getString("observation_status"));
            assertTrue(r.getJSONArray("rows").getJSONObject(0).isNull("digest"));
        }
    }
    @Test public void duplicateEvidenceCannotBeSilentlyReused() throws Exception {
        JSONObject row = RealityReport.row("reality.radio.bluetooth_name", null, () -> "hidden");
        JSONObject evidence = report(row); evidence.getJSONArray("rows").put(row);
        assertThrows(IllegalArgumentException.class, () -> RealityRuntimeReport.build(table("reality.radio.values.bluetooth_name", "BluetoothAdapter.getName()"), profile(), evidence));
    }
}
