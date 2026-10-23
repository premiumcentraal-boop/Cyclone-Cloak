package dev.cyclone.cloak.probe;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded, read-only observations. No raw content, expectations or verified results. */
final class FilesystemObservation {
    static final int MAX_BYTES = 1024 * 1024;
    interface Reader { InputStream open(String path) throws IOException; }
    private FilesystemObservation() { }

    static JSONObject collect(JSONObject table) throws Exception {
        return collect(table, path -> Files.newInputStream(Paths.get(path)));
    }

    static JSONObject collect(JSONObject table, Reader reader) throws Exception {
        JSONArray rows = new JSONArray();
        JSONArray surfaces = table.getJSONArray("observation_surfaces");
        if (surfaces.length() != 4) throw new IllegalArgumentException("INCOMPLETE_FILESYSTEM_SURFACES");
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < surfaces.length(); i++) {
            JSONObject surface = surfaces.getJSONObject(i);
            String id = surface.getString("field_path");
            String path = surface.getString("surface");
            if (!allowed(id, path) || !seen.add(id)) throw new IllegalArgumentException("UNKNOWN_FILESYSTEM_SURFACE");
            rows.put(observe(id, path, reader));
        }
        return new JSONObject().put("format", 1).put("rows", rows);
    }

    private static boolean allowed(String id, String path) {
        switch (id) {
            case "diagnostics.filesystem.build_prop": return path.equals("/system/build.prop");
            case "diagnostics.filesystem.proc_version": return path.equals("/proc/version");
            case "diagnostics.filesystem.proc_cpuinfo": return path.equals("/proc/cpuinfo");
            case "diagnostics.filesystem.maps_snapshot": return path.equals("/proc/self/maps");
            default: return false;
        }
    }

    private static JSONObject observe(String id, String path, Reader reader) throws Exception {
        long start = System.nanoTime();
        JSONObject row = new JSONObject().put("id", id).put("source", path)
            .put("status", "MISSING").put("reason", "READ_UNAVAILABLE")
            .put("sha256", JSONObject.NULL).put("byte_count", JSONObject.NULL)
            .put("line_count", JSONObject.NULL);
        try (InputStream input = reader.open(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[4096];
            int total = 0, lines = 0, last = -1, count;
            while ((count = input.read(buffer, 0, Math.min(buffer.length, MAX_BYTES - total + 1))) != -1) {
                if (count == 0) throw new IOException();
                total += count;
                if (total > MAX_BYTES) {
                    return row.put("reason", "READ_LIMIT_EXCEEDED")
                        .put("duration_ms", (System.nanoTime() - start) / 1000000);
                }
                digest.update(buffer, 0, count);
                for (int i = 0; i < count; i++) if (buffer[i] == '\n') lines++;
                last = buffer[count - 1];
            }
            if (last != -1 && last != '\n') lines++;
            StringBuilder hex = new StringBuilder();
            for (byte value : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", value & 255));
            row.put("status", "OBSERVED").put("reason", "OBSERVATION_ONLY_NO_EXPECTATION")
                .put("sha256", hex.toString()).put("byte_count", total).put("line_count", lines);
        } catch (SecurityException denied) {
            row.put("status", "BLOCKED").put("reason", "PERMISSION_DENIED");
        } catch (java.nio.file.AccessDeniedException denied) {
            row.put("status", "BLOCKED").put("reason", "PERMISSION_DENIED");
        } catch (IOException unavailable) {
            row.put("reason", "READ_UNAVAILABLE");
        }
        return row.put("duration_ms", (System.nanoTime() - start) / 1000000);
    }
}
