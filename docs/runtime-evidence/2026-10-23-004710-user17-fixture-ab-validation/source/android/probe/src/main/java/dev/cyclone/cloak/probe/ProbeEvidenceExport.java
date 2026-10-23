package dev.cyclone.cloak.probe;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Diagnostics-only external evidence, stripped of raw readings and exception messages. */
final class ProbeEvidenceExport {
    private static final Set<String> KEYS = new HashSet<>(Arrays.asList(
        "format", "fixture", "package", "uid", "pid", "request_id", "session_generation", "profile_sha256",
        "phase", "outcome", "timestamp_ms", "status", "reason", "error", "failed_row_id", "rows",
        "id", "source", "field_path", "surface", "expected_sha256", "actual_sha256", "sha256",
        "duration_ms", "byte_count", "line_count", "filesystem_observations", "filesystem_reader_failures",
        "device_reality", "runtime_envelope", "reality_runtime", "linked_parity", "failure_details",
        "exception_class", "message_redacted", "stack_trace", "stack_truncated", "class", "method", "line"));

    static File directory(File userExternalFiles) {
        if (userExternalFiles == null) throw new IllegalStateException("EXTERNAL_EVIDENCE_UNAVAILABLE");
        return new File(userExternalFiles, "standalone-evidence");
    }

    static JSONObject redact(JSONObject input) throws Exception {
        JSONObject output = new JSONObject();
        Iterator<String> keys = input.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!KEYS.contains(key)) continue;
            Object value = input.get(key);
            if (value instanceof JSONObject) value = redact((JSONObject) value);
            else if (value instanceof JSONArray) {
                JSONArray filtered = new JSONArray();
                JSONArray array = (JSONArray) value;
                for (int i = 0; i < array.length(); i++) {
                    Object item = array.get(i);
                    if (item instanceof JSONObject) filtered.put(redact((JSONObject) item));
                }
                value = filtered;
            }
            output.put(key, value);
        }
        return output;
    }

    static void write(File directory, String name, JSONObject input) throws Exception {
        if (!Arrays.asList("report.json", "capture-start.json", "capture-end.json").contains(name))
            throw new IllegalArgumentException("INVALID_EVIDENCE_FILE");
        Files.createDirectories(directory.toPath());
        File target = new File(directory, name);
        File partial = new File(directory, name + ".tmp");
        try {
            Files.write(partial.toPath(), redact(input).toString().getBytes(StandardCharsets.UTF_8));
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(partial.toPath()); }
    }
}
