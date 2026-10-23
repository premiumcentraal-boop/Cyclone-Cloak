package dev.cyclone.cloak.probe;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Diagnostics-only checks. A shape check is never evidence of a profile hook. */
final class DiagnosticExpectation {
    private DiagnosticExpectation() { }
    static final Set<String> POLICIES = new HashSet<>(Arrays.asList(
        "EXACT", "STABLE_ACROSS_SWITCH", "PRESENT", "TYPED", "RANGE", "ENUM", "EXTERNAL_STATE"));

    static boolean missing(Object value) {
        return value == null || value == JSONObject.NULL || "".equals(value);
    }

    static boolean integer(Object value) {
        return value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long;
    }

    static boolean scalar(Object value) {
        return value == JSONObject.NULL || value instanceof String || value instanceof Boolean || integer(value);
    }

    static boolean typed(String type, Object value) throws Exception {
        switch (type) {
            case "boolean": return value instanceof Boolean;
            case "integer": return integer(value);
            case "string": return value instanceof String;
            case "mac": return value instanceof String && ((String) value).matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}");
            case "packages":
            case "libraries":
            case "sensors":
                if (!(value instanceof JSONArray)) return false;
                JSONArray list = (JSONArray) value;
                for (int i = 0; i < list.length(); i++) {
                    Object item = list.get(i);
                    if (type.equals("packages") && (!(item instanceof String) || !((String) item).matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*"))) return false;
                    if (type.equals("libraries") && (!(item instanceof String) || !((String) item).matches("[A-Za-z0-9_.+-]+\\.so"))) return false;
                    if (type.equals("sensors")) {
                        if (!(item instanceof JSONObject)) return false;
                        JSONObject sensor = (JSONObject) item;
                        if (!integer(sensor.opt("type")) || sensor.getLong("type") <= 0
                                || sensor.getLong("type") > Integer.MAX_VALUE
                                || !(sensor.opt("name") instanceof String) || !(sensor.opt("vendor") instanceof String)) return false;
                    }
                }
                return true;
            default: return false;
        }
    }

    static boolean equal(Object a, Object b) throws Exception {
        return ProbeReportPolicy.canonical(a).equals(ProbeReportPolicy.canonical(b));
    }

    static void validate(JSONObject rule) throws Exception {
        String policy = rule.getString("policy");
        if (!POLICIES.contains(policy)) throw new IllegalArgumentException("UNKNOWN_EXPECTATION_POLICY");
        Set<String> allowed = new HashSet<>(Arrays.asList("policy"));
        if (policy.equals("EXACT")) { allowed.add("value"); if (!rule.has("value")) throw new IllegalArgumentException(); }
        if (Arrays.asList("TYPED", "STABLE_ACROSS_SWITCH", "EXTERNAL_STATE").contains(policy)) {
            allowed.add("type");
            if (!Arrays.asList("boolean", "integer", "string", "mac", "packages", "libraries", "sensors").contains(rule.getString("type"))) throw new IllegalArgumentException();
        }
        if (policy.equals("STABLE_ACROSS_SWITCH")) {
            allowed.add("expected_change");
            if (rule.has("expected_change") && !(rule.get("expected_change") instanceof Boolean)) throw new IllegalArgumentException();
        }
        if (policy.equals("RANGE") || policy.equals("EXTERNAL_STATE")) {
            allowed.add("min"); allowed.add("max");
            if (policy.equals("RANGE") && (!rule.has("min") || !rule.has("max"))) throw new IllegalArgumentException();
            for (String key : Arrays.asList("min", "max")) if (rule.has(key) && !integer(rule.get(key))) throw new IllegalArgumentException();
            if (rule.has("min") && rule.has("max") && rule.getLong("min") > rule.getLong("max")) throw new IllegalArgumentException();
            if (policy.equals("EXTERNAL_STATE") && (rule.has("min") || rule.has("max")) && !rule.getString("type").equals("integer")) throw new IllegalArgumentException();
        }
        if (policy.equals("ENUM") || policy.equals("EXTERNAL_STATE")) {
            allowed.add("values");
            if (policy.equals("ENUM") && !rule.has("values")) throw new IllegalArgumentException();
            if (rule.has("values")) {
                JSONArray values = rule.getJSONArray("values");
                if (values.length() == 0) throw new IllegalArgumentException();
                for (int i = 0; i < values.length(); i++) if (!scalar(values.get(i))) throw new IllegalArgumentException();
            }
        }
        if (policy.equals("EXTERNAL_STATE")) {
            allowed.add("unavailable");
            JSONArray values = rule.getJSONArray("unavailable");
            for (int i = 0; i < values.length(); i++) {
                JSONObject item = values.getJSONObject(i);
                if (item.length() != 2 || !item.has("value") || !scalar(item.get("value")) || !item.getString("reason").matches("[A-Z][A-Z0-9_]*")) throw new IllegalArgumentException();
            }
        }
        java.util.Iterator<String> keys = rule.keys();
        while (keys.hasNext()) if (!allowed.contains(keys.next())) throw new IllegalArgumentException();
    }

    static JSONObject result(String status, String reason) throws Exception {
        return new JSONObject().put("status", status).put("reason", reason);
    }

    static JSONObject evaluate(JSONObject rule, Object actual) throws Exception {
        try { validate(rule); } catch (Exception malformed) { return result("FAIL", "MALFORMED_EXPECTATION"); }
        String policy = rule.getString("policy");
        if (policy.equals("EXACT")) return result(equal(rule.get("value"), actual) ? "PASS" : "FAIL", equal(rule.get("value"), actual) ? "EXACT_MATCH" : "EXACT_MISMATCH");
        if (policy.equals("EXTERNAL_STATE")) {
            JSONArray unavailable = rule.getJSONArray("unavailable");
            for (int i = 0; i < unavailable.length(); i++) {
                JSONObject item = unavailable.getJSONObject(i);
                if (equal(item.get("value"), actual)) return result("OBSERVED", "EXTERNAL_STATE_UNAVAILABLE:" + item.getString("reason"));
            }
            if (missing(actual)) return result("OBSERVED", "EXTERNAL_STATE_UNAVAILABLE:NO_VALUE");
        } else if (missing(actual)) return result("MISSING", "EXPECTATION_VALUE_UNAVAILABLE");
        if (policy.equals("PRESENT")) return result("PASS", "VALUE_PRESENT");
        if (rule.has("type") && !typed(rule.getString("type"), actual)) return result("FAIL", "EXPECTATION_TYPE_MISMATCH");
        if (policy.equals("RANGE") || rule.has("min") || rule.has("max")) {
            if (!integer(actual)) return result("FAIL", "EXPECTATION_TYPE_MISMATCH");
            long value = ((Number) actual).longValue();
            if ((rule.has("min") && value < rule.getLong("min")) || (rule.has("max") && value > rule.getLong("max"))) return result("FAIL", "EXPECTATION_OUT_OF_RANGE");
        }
        if (rule.has("values")) {
            boolean member = false;
            JSONArray values = rule.getJSONArray("values");
            for (int i = 0; i < values.length(); i++) member |= equal(values.get(i), actual);
            if (!member) return result("FAIL", "EXPECTATION_NOT_IN_ENUM");
        }
        if (policy.equals("EXTERNAL_STATE")) return result("OBSERVED", "EXTERNAL_STATE_AVAILABLE");
        if (policy.equals("STABLE_ACROSS_SWITCH")) return result("OBSERVED", "STABILITY_BASELINE_REQUIRED");
        return result("PASS", policy.equals("TYPED") ? "TYPE_VALID" : policy.equals("ENUM") ? "ENUM_VALID" : "RANGE_VALID");
    }

    static JSONObject fixture(JSONObject file, String label) throws Exception {
        if (file.length() != 3 || !integer(file.get("schema_version")) || file.getInt("schema_version") != 1
                || !label.equals(file.getString("fixture"))) throw new IllegalArgumentException("INVALID_EXPECTATION_FIXTURE");
        JSONObject rules = file.getJSONObject("rows");
        java.util.Iterator<String> keys = rules.keys();
        while (keys.hasNext()) validate(rules.getJSONObject(keys.next()));
        return rules;
    }
}
