package dev.cyclone.cloak.probe;

import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Optional digest-only evidence. Expectations always come from base fields, never Reality. */
final class LinkedParityReport {
    private LinkedParityReport() { }
    static Object at(JSONObject profile, String path) {
        Object value = profile;
        for (String part : path.split("\\.")) value = value instanceof JSONObject ? ((JSONObject) value).opt(part) : null;
        return value == JSONObject.NULL ? null : value;
    }
    static JSONObject build(JSONObject profile, JSONObject table, Map<String, RealityReport.Reader> readers) throws Exception {
        JSONArray rows = new JSONArray();
        JSONArray fields = table.getJSONArray("fields");
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);
            if (field.isNull("linked_base_path")) continue;
            String base = field.getString("linked_base_path"); String id = field.getString("field_path");
            rows.put(RealityReport.row(id, at(profile, base), readers.get(id)).put("linked_base_path", base));
        }
        Object mcc = at(profile, "telephony.mcc"), mnc = at(profile, "telephony.mnc");
        Object numeric = mcc instanceof String && mnc instanceof String ? (String)mcc + mnc : null;
        rows.put(RealityReport.row("linked.simOperator", numeric, readers.get("linked.simOperator")));
        rows.put(RealityReport.row("linked.activeModemCount", at(profile, "telephony.sim_slot_count"), readers.get("linked.activeModemCount")));
        Object width = at(profile, "display.width"), height = at(profile, "display.height"), refresh = at(profile, "display.refresh_rate_hz");
        RealityReport.Reader modes = readers.get("linked.supportedModes");
        final String[] aggregate = {null};
        boolean complete = width instanceof Number && height instanceof Number && refresh instanceof Number;
        JSONObject supported = RealityReport.row("linked.supportedModes", complete ? true : null, modes == null ? null : () -> {
            Object actual = modes.read();
            if (actual == null) return null;
            if (!(actual instanceof JSONArray)) throw new IllegalArgumentException("MALFORMED_SUPPORTED_MODES");
            JSONArray array = (JSONArray)actual; aggregate[0] = RealityReport.hash(array);
            if (!complete) return null; // No partial expectation may become a pass.
            boolean contains = false;
            for (int i = 0; i < array.length(); i++) {
                JSONObject mode = array.getJSONObject(i);
                for (String key : new String[]{"width", "height", "refresh_hz"})
                    if (!(mode.opt(key) instanceof Number) || !Double.isFinite(mode.getDouble(key)) || mode.getDouble(key) <= 0)
                        throw new IllegalArgumentException("MALFORMED_SUPPORTED_MODES");
                contains |= mode.getDouble("width") == ((Number)width).doubleValue() && mode.getDouble("height") == ((Number)height).doubleValue()
                    && mode.getDouble("refresh_hz") == ((Number)refresh).doubleValue();
            }
            return contains;
        }).put("aggregate_sha256", aggregate[0] == null ? JSONObject.NULL : aggregate[0]);
        if (!complete && aggregate[0] != null) supported.put("status", "OBSERVED").put("reason", "BASE_MODE_INCOMPLETE");
        rows.put(supported);
        return new JSONObject().put("format", 1).put("rows", rows)
            .put("display_diagnostics", displayDiagnostics(readers));
    }

    /** Explicit allowlist: screen geometry/rates only, never arbitrary reader values. */
    static JSONObject displayDiagnostics(Map<String, RealityReport.Reader> readers) throws Exception {
        JSONObject result = new JSONObject();
        for (String name : new String[]{"active_mode", "supported_modes", "refresh_rate_hz"}) {
            JSONObject entry = new JSONObject().put("value", JSONObject.NULL);
            RealityReport.Reader reader = readers.get("diagnostic." + name);
            try {
                Object value = reader == null ? null : reader.read();
                if (value != null && value != JSONObject.NULL) {
                    if (name.equals("supported_modes")) {
                        JSONArray values = (JSONArray)value, safe = new JSONArray();
                        for (int i = 0; i < values.length(); i++) safe.put(modeTuple(values.getJSONObject(i)));
                        value = safe;
                    } else if (name.equals("active_mode")) value = modeTuple((JSONObject)value);
                    else value = positiveNumber(value);
                }
                entry.put("status", reader == null ? "UNMAPPED" : value == null || value == JSONObject.NULL ? "MISSING" : "OBSERVED")
                    .put("value", value == null ? JSONObject.NULL : value);
            } catch (Exception error) {
                entry.put("status", error instanceof SecurityException ? "BLOCKED" : "MISSING")
                    .put("reason", error.getClass().getSimpleName());
            }
            result.put(name, entry);
        }
        return result;
    }

    private static Number positiveNumber(Object value) {
        if (!(value instanceof Number) || !Double.isFinite(((Number)value).doubleValue()) || ((Number)value).doubleValue() <= 0)
            throw new IllegalArgumentException("INVALID_DISPLAY_VALUE");
        return (Number)value;
    }

    private static JSONObject modeTuple(JSONObject value) throws Exception {
        return new JSONObject().put("width", positiveNumber(value.get("width")))
            .put("height", positiveNumber(value.get("height")))
            .put("refresh_hz", positiveNumber(value.get("refresh_hz")));
    }
}
