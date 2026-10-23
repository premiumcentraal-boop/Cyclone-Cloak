package dev.cyclone.cloak.probe;

import org.json.JSONArray;
import org.json.JSONObject;

/** Pure report policy; observation is not a successful profile comparison. */
final class ProbeReportPolicy {
    private ProbeReportPolicy() { }

    static String status(boolean hasExpectation, boolean missing, boolean matches) {
        if (!hasExpectation) return "OBSERVED";
        return missing ? "MISSING" : matches ? "PASS" : "FAIL";
    }

    static String fixture(String requested, boolean external, boolean matchesA, boolean matchesB) {
        if (!external) return "b".equals(requested) ? "b" : "a";
        if ("a".equals(requested) || "b".equals(requested)) {
            if (!("a".equals(requested) ? matchesA : matchesB)) {
                throw new IllegalArgumentException("FIXTURE_PROFILE_MISMATCH");
            }
            return requested;
        }
        // An external saved profile is never silently labelled fixture a.
        return "external";
    }

    static String[] permissions(String id, int api) {
        if (id.equals("bluetooth.name") || id.equals("bluetooth.address")) {
            return api >= 31 ? new String[]{"android.permission.BLUETOOTH_CONNECT"} : new String[0];
        }
        if (id.equals("wifi.ssid") || id.equals("wifi.bssid")) {
            return new String[]{"android.permission.ACCESS_WIFI_STATE", "android.permission.ACCESS_FINE_LOCATION"};
        }
        if (id.equals("wifi.serviceMac") || id.equals("wifi.linkSpeed")) {
            return new String[]{"android.permission.ACCESS_WIFI_STATE"};
        }
        return new String[0];
    }

    static String permissionFailure(String id, int api, java.util.function.Predicate<String> granted) {
        for (String permission : permissions(id, api)) {
            if (!granted.test(permission)) return "PERMISSION_DENIED:" + permission;
        }
        return null;
    }

    static String failureReason(Throwable error) {
        if (error instanceof ProbeReadDeadline.ReadFailure) return ((ProbeReadDeadline.ReadFailure) error).reason;
        Throwable cause = error instanceof java.lang.reflect.InvocationTargetException && error.getCause() != null
            ? error.getCause() : error;
        if (cause instanceof ClassNotFoundException) return "SDK_OR_API_ABSENT";
          if (cause.getClass().getName().equals("dev.cyclone.cloak.hook.RuntimeEnvelope$Unavailable"))
              return "PROFILE_SURFACE_UNAVAILABLE";
        String reason = cause.getClass().getSimpleName();
        // Record only a permission name, never exception text or identifier data.
        if (cause instanceof SecurityException && cause.getMessage() != null) {
            java.util.regex.Matcher permission = java.util.regex.Pattern.compile("android\\.permission\\.[A-Z_]+")
                .matcher(cause.getMessage());
            if (permission.find()) reason += ":" + permission.group();
        }
        return reason;
    }

    // Full-body comparison, independent of JSON whitespace and key order.
    static String canonical(Object value) throws Exception {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            java.util.TreeSet<String> keys = new java.util.TreeSet<>();
            object.keys().forEachRemaining(keys::add);
            StringBuilder out = new StringBuilder("{");
            for (String key : keys) out.append(JSONObject.quote(key)).append(':').append(canonical(object.get(key))).append(',');
            return out.append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < array.length(); i++) out.append(canonical(array.get(i))).append(',');
            return out.append(']').toString();
        }
        return value instanceof String ? JSONObject.quote((String) value) : String.valueOf(value);
    }
}
