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
        if (!Arrays.asList("report.json", "capture-start.json", "capture-end.json", "authoring.json").contains(name))
            throw new IllegalArgumentException("INVALID_EVIDENCE_FILE");
        Files.createDirectories(directory.toPath());
        File target = new File(directory, name);
        File partial = new File(directory, name + ".tmp");
        try {
            JSONObject output = "authoring.json".equals(name) ? authoring(input) : redact(input);
            byte[] bytes = output.toString().getBytes(StandardCharsets.UTF_8);
            if ("authoring.json".equals(name) && bytes.length > 262144)
                throw new IllegalArgumentException("EVIDENCE_SIZE_LIMIT");
            Files.write(partial.toPath(), bytes);
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(partial.toPath()); }
    }

    /** Explicit authoring export only; normal reports retain the digest-only policy. */
    static JSONObject authoring(JSONObject input) throws Exception {
        if (!"authoring-raw".equals(input.getString("format"))
                || input.getLong("captured_at_ms") <= 0 || input.getInt("captured_sdk_int") < 29)
            throw new IllegalArgumentException("INVALID_AUTHORING_EXPORT");
        JSONObject values = input.getJSONObject("values"), selected = new JSONObject();
        for (String reader : Arrays.asList("compute.gpu", "memory.free_storage_gib", "sensors.inventory",
                "packages.installed", "webview.provider", "webview.version", "webview.user_agent", "webview.default_ua",
                "device.manufacturer", "device.brand", "device.model", "device.product", "device.device", "device.hardware",
                "device.fingerprint", "device.version_release", "device.sdk_int", "device.security_patch", "device.build_id",
                "device.version_incremental", "device.build_date_utc", "device.first_api_level", "device.bootloader", "device.baseband",
                "identifiers.android_id", "identifiers.serial", "telephony.sim_slot_count", "telephony.carrier_name", "telephony.mcc", "telephony.mnc", "telephony.network_type_property",
                "display.width", "display.height", "display.refresh_rate_hz", "display.density", "locale.language", "locale.country", "locale.timezone")) {
            if (values.has(reader)) selected.put(reader, values.get(reader));
        }
        return new JSONObject().put("format", "authoring-raw")
            .put("captured_at_ms", input.getLong("captured_at_ms"))
            .put("captured_sdk_int", input.getInt("captured_sdk_int")).put("values", selected);
    }
}
