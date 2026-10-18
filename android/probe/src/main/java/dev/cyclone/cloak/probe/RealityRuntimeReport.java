package dev.cyclone.cloak.probe;

import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Reuses digest-only comparisons; never turns contract capability into observed evidence. */
final class RealityRuntimeReport {
    private RealityRuntimeReport() { }
    static JSONObject build(JSONObject table, JSONObject profile, JSONObject evidence) throws Exception {
        JSONArray result = new JSONArray();
        if (!profile.has("reality")) return new JSONObject().put("format", 1).put("presence", "not present").put("rows", result);
        Map<String, JSONObject> readings = new HashMap<>();
        JSONArray previous = evidence.getJSONArray("rows");
        for (int i = 0; i < previous.length(); i++) {
            JSONObject row = previous.getJSONObject(i);
            if (readings.put(row.getString("id"), row) != null) throw new IllegalArgumentException("DUPLICATE_REALITY_ROW");
        }
        JSONArray fields = table.getJSONArray("fields");
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);
            String path = field.getString("field_path");
            JSONObject reading = path.contains("[]") || field.isNull("surface") ? null
                : readings.get(path.replace(".values.", "."));
            JSONObject row = new JSONObject().put("field_path", path).put("surface", field.get("surface"))
                .put("observation_status", reading == null ? "UNMAPPED" : reading.getString("status"))
                .put("digest", reading == null ? JSONObject.NULL : reading.opt("actual_sha256"))
                .put("duration_ms", reading == null ? 0 : reading.getLong("duration_ms"))
                .put("reason", reading == null ? field.getString("reason") : reading.getString("reason"));
            // PASS is copied only from the existing expectation-backed comparison.
            if (reading != null && ("PASS".equals(reading.optString("status")) || "FAIL".equals(reading.optString("status")))) {
                String actual = reading.getString("actual_sha256"), expected = reading.getString("expected_sha256");
                if (!actual.matches("[0-9a-f]{64}") || !expected.matches("[0-9a-f]{64}")
                    || actual.equals(expected) != "PASS".equals(reading.getString("status")))
                    throw new IllegalArgumentException("UNPROVEN_REALITY_COMPARISON");
                row.put("expected_digest", expected);
            }
            result.put(row);
        }
        return new JSONObject().put("format", 1).put("presence", "present").put("rows", result);
    }
}
