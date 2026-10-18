package dev.cyclone.cloak.probe;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import org.json.JSONObject;

/** Optional v1 extension. Only digests leave the reader; never raw names, packages or settings. */
final class RealityReport {
    interface Reader { Object read() throws Exception; }
    private RealityReport() { }
    static String hash(Object value) throws Exception {
        // Android returns refresh rates as float; schema expectations may be integers.
        // Normalize scalar JSON numbers only, preserving string/boolean type distinctions.
        String canonical = value instanceof Number ? new java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
            : ProbeReportPolicy.canonical(value);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
        StringBuilder text = new StringBuilder();
        for (byte b : digest) text.append(String.format(Locale.ROOT, "%02x", b & 255));
        return text.toString();
    }
    static JSONObject row(String id, Object expected, Reader reader) throws Exception {
        long start = System.nanoTime();
        JSONObject result = new JSONObject().put("id", id).put("source", "Android read-only API")
            .put("expected_sha256", JSONObject.NULL).put("actual_sha256", JSONObject.NULL);
        try {
            Object actual = reader == null ? null : reader.read();
            boolean hasExpected = expected != null && expected != JSONObject.NULL;
            if (hasExpected) result.put("expected_sha256", hash(expected));
            if (actual != null && actual != JSONObject.NULL) result.put("actual_sha256", hash(actual));
            String status = reader == null ? "UNMAPPED" : actual == null || actual == JSONObject.NULL ? "MISSING"
                : !hasExpected ? "OBSERVED" : hash(expected).equals(hash(actual)) ? "PASS" : "FAIL";
            result.put("status", status).put("reason", status.equals("PASS") ? "HASH_MATCH_ONLY"
                : status.equals("FAIL") ? "HASH_MISMATCH" : status.equals("OBSERVED") ? "NO_EXPECTATION" : status);
        } catch (Exception error) {
            result.put("status", error instanceof SecurityException ? "BLOCKED" : "MISSING")
                .put("reason", ProbeReportPolicy.failureReason(error));
        }
        return result.put("duration_ms", (System.nanoTime() - start) / 1000000);
    }
}
