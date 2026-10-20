package dev.cyclone.cloak.hook;

import org.json.JSONArray;
import org.json.JSONObject;

/** Immutable process snapshot. No host lookup, persistence, IPC, or identity derivation. */
public final class RuntimeEnvelope {
    private final JSONObject profile;
    public final boolean active;
    private static final java.util.Set<String> PACKAGES = java.util.Collections.unmodifiableSet(
        new java.util.HashSet<>(java.util.Arrays.asList("android", "com.android.vending",
            "com.google.android.gms", "com.google.android.gsf", "com.google.android.webview", "com.android.chrome")));

    public static final class Unavailable extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        public Unavailable() { super("PROFILE_SURFACE_UNAVAILABLE"); }
    }

    private RuntimeEnvelope(JSONObject value, boolean enabled) { profile = value; active = enabled; }
    public static RuntimeEnvelope parse(String text) {
        if (text == null || text.isEmpty()) return new RuntimeEnvelope(new JSONObject(), false);
        try {
            if (text.length() > 1048576) throw new Unavailable();
            JSONObject value = new JSONObject(text);
            // Legacy profiles opt out of new Reality readers; malformed 0.4 data fails closed.
            if (!java.util.Arrays.asList("0.2", "0.3", "0.4").contains(value.optString("schema_version")))
                return new RuntimeEnvelope(new JSONObject(), true);
            boolean enabled = "0.4".equals(value.optString("schema_version")) && value.has("reality");
            return new RuntimeEnvelope(value, enabled);
        } catch (Exception ignored) { return new RuntimeEnvelope(new JSONObject(), true); }
    }

    private JSONObject values(String section) {
        JSONObject reality = profile.optJSONObject("reality");
        JSONObject entry = reality == null ? null : reality.optJSONObject(section);
        if (entry == null || !"known".equals(entry.optString("availability"))) throw new Unavailable();
        if (!java.util.Arrays.asList("public reference", "approximated", "needs verification", "derived").contains(entry.optString("source")))
            throw new Unavailable();
        JSONObject result = entry.optJSONObject("values");
        if (result == null) throw new Unavailable();
        return result;
    }

    private static String string(JSONObject object, String key, int limit) {
        Object value = object.opt(key);
        if (!(value instanceof String) || ((String) value).isEmpty() || ((String) value).length() > limit
                || ((String) value).matches("(?s).*[\\x00-\\x1f\\x7f].*")) throw new Unavailable();
        return (String) value;
    }

    private static double number(JSONObject object, String key, double min, double max) {
        Object value = object.opt(key);
        if (!(value instanceof Number)) throw new Unavailable();
        double n = ((Number) value).doubleValue();
        if (!Double.isFinite(n) || n < min || n > max) throw new Unavailable();
        return n;
    }

    public String gpu() { return string(values("compute"), "gpu", 96); }
    public String soc() { return string(values("compute"), "soc", 96); }

    /** Physical cores and advertised SKU sizes have no equivalent public process-capacity API. */
    public Object unsupportedCapacity() { throw new Unavailable(); }

    public long availableBytes(long filesystemTotal) {
        double gib = number(values("memory"), "free_storage_gib", 0, 2048);
        long bytes = (long) (gib * 1073741824.0);
        if (filesystemTotal < 0 || bytes > filesystemTotal) throw new Unavailable();
        return bytes;
    }
    public long availableBytes(long filesystemTotal, long blockSize) {
        long bytes = availableBytes(filesystemTotal);
        if (blockSize <= 0 || bytes % blockSize != 0) throw new Unavailable();
        return bytes;
    }

    public JSONObject sensor(int type) {
        JSONArray items = values("sensors").optJSONArray("inventory");
        if (items == null || items.length() > 128) throw new Unavailable();
        JSONObject found = null;
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) throw new Unavailable();
            double raw = number(item, "type", 1, 65535);
            if (raw != (int) raw || !seen.add((int) raw)) throw new Unavailable();
            string(item, "name", 96);
            if ((int) raw == type) found = item;
        }
        if (found == null) throw new Unavailable();
        try { return new JSONObject(found.toString()); } catch (Exception e) { throw new Unavailable(); }
    }

    public java.util.Set<Integer> sensorTypes() {
        JSONArray items = values("sensors").optJSONArray("inventory");
        if (items == null || items.length() > 128) throw new Unavailable();
        java.util.Set<Integer> result = new java.util.HashSet<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) throw new Unavailable();
            double type = number(item, "type", 1, 65535);
            if (type != (int) type || !result.add((int) type)) throw new Unavailable();
            sensor((int) type);
        }
        return result;
    }

    public Object sensorValue(int type, String field) {
        JSONObject item = sensor(type);
        if ("name".equals(field)) return string(item, field, 96);
        double value = number(item, field, field.endsWith("delay_us") ? 0 : 0.000001,
            field.endsWith("delay_us") ? 10000000 : 1000000);
        if (field.endsWith("delay_us")) {
            if (value != (int) value) throw new Unavailable();
            if (item.has("max_delay_us") && item.has("min_delay_us")
                    && item.optDouble("max_delay_us") < item.optDouble("min_delay_us")) throw new Unavailable();
            return (int) value;
        }
        return (float) value;
    }

    public Object sensorValueWithinHost(int type, String field, Object actual) {
        Object selected = sensorValue(type, field);
        if ("name".equals(field)) return selected;
        if (!(actual instanceof Number) || !Double.isFinite(((Number) actual).doubleValue())) throw new Unavailable();
        double declared = ((Number) selected).doubleValue(), host = ((Number) actual).doubleValue();
        if (("range".equals(field) || "max_delay_us".equals(field)) && declared > host
                || ("resolution".equals(field) || "min_delay_us".equals(field)) && declared < host) throw new Unavailable();
        return selected;
    }

    public static boolean allowlisted(String name) { return PACKAGES.contains(name); }
    public JSONObject packageVersion(String name) {
        if (!allowlisted(name)) return null;
        JSONArray items = values("packages").optJSONArray("installed");
        if (items == null || items.length() > 64) throw new Unavailable();
        java.util.Set<String> seen = new java.util.HashSet<>();
        JSONObject found = null;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) throw new Unavailable();
            String pkg = string(item, "package", 160);
            if (!pkg.matches("android|[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+") || !seen.add(pkg)) throw new Unavailable();
            if (name.equals(pkg)) found = item;
        }
        if (found == null) throw new Unavailable();
        string(found, "version_name", 96);
        double code = number(found, "version_code", 0, 9007199254740991.0);
        if (code != Math.floor(code)) throw new Unavailable();
        try { return new JSONObject(found.toString()); } catch (Exception e) { throw new Unavailable(); }
    }

    /** The real visible provider must match. Never invent an installed provider or switch engines. */
    public JSONObject webProvider(String realPackage) {
        JSONObject web = values("webview");
        if (!string(web, "provider", 160).equals(realPackage)) throw new Unavailable();
        String version = string(web, "version", 64);
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")) throw new Unavailable();
        JSONObject pkg = packageVersion(realPackage);
        if (pkg == null || !version.equals(pkg.optString("version_name"))) throw new Unavailable();
        return pkg;
    }

    public String userAgent() {
        String web = string(values("webview"), "user_agent", 512);
        JSONObject ua = profile.optJSONObject("ua");
        if (ua == null || !web.equals(ua.optString("value"))) throw new Unavailable();
        return web;
    }

    public String drmLevel(String actual) {
        String expected = string(values("drm"), "security_level", 2);
        if (!expected.matches("L[123]") || !expected.equals(actual)) throw new Unavailable();
        return actual; // Match-only gate, never an elevation or synthesized security level.
    }

    public int simState(int slot) {
        JSONObject telephony = profile.optJSONObject("telephony");
        if (telephony == null) throw new Unavailable();
        double raw = number(telephony, "sim_slot_count", 1, 2);
        if (raw != (int) raw) throw new Unavailable();
        if (slot < 0 || slot >= (int) raw) return 0; // SIM_STATE_UNKNOWN, never another slot's state.
        JSONObject carrier = values("carrier");
        if (number(carrier, "slots", 0, 4) != raw) throw new Unavailable();
        String state = string(carrier, "sim_state", 32);
        switch (state) {
            case "absent": return 1; case "pin_required": return 2;
            case "puk_required": return 3; case "ready": return 5;
            default: throw new Unavailable();
        }
    }

    public int[] displaySize() {
        JSONObject display = profile.optJSONObject("display");
        if (display == null) throw new Unavailable();
        double w = number(display, "width", 240, 10000), h = number(display, "height", 240, 10000);
        if (w != (int) w || h != (int) h) throw new Unavailable();
        return new int[]{(int) w, (int) h};
    }
}
