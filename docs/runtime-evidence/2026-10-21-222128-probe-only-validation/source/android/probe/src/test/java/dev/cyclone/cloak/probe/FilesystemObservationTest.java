package dev.cyclone.cloak.probe;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class FilesystemObservationTest {
    private JSONObject table() throws Exception {
        String[] ids = {"build_prop", "proc_version", "proc_cpuinfo", "maps_snapshot"};
        String[] paths = {"/system/build.prop", "/proc/version", "/proc/cpuinfo", "/proc/self/maps"};
        JSONArray rows = new JSONArray();
        for (int i = 0; i < ids.length; i++) rows.put(new JSONObject()
            .put("field_path", "diagnostics.filesystem." + ids[i]).put("surface", paths[i]));
        return new JSONObject().put("observation_surfaces", rows);
    }
    @Test public void observationsHaveOnlyDigestsAndCounts() throws Exception {
        byte[] bytes = "secret-content\nsecond-line".getBytes(StandardCharsets.UTF_8);
        JSONObject report = FilesystemObservation.collect(table(), path -> new ByteArrayInputStream(bytes));
        assertEquals(4, report.getJSONArray("rows").length());
        String expected = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        for (int i = 0; i < 4; i++) {
            JSONObject row = report.getJSONArray("rows").getJSONObject(i);
            assertEquals("OBSERVED", row.getString("status"));
            assertEquals(expected, row.getString("sha256"));
            assertEquals(2, row.getInt("line_count"));
            assertEquals(bytes.length, row.getInt("byte_count"));
            assertFalse(row.has("expected_sha256"));
        }
        assertFalse(report.toString().contains("secret-content"));
    }
    @Test public void emptyAndTerminatedLinesAreCountedExactly() throws Exception {
        for (String text : new String[]{"", "a", "a\n", "\n\n"}) {
            JSONObject row = FilesystemObservation.collect(table(), path -> new ByteArrayInputStream(
                text.getBytes(StandardCharsets.UTF_8))).getJSONArray("rows").getJSONObject(0);
            assertEquals(text.isEmpty() ? 0 : text.equals("\n\n") ? 2 : 1, row.getInt("line_count"));
        }
    }
    @Test public void deniedAndMissingHaveFixedReasonsWithoutContent() throws Exception {
        for (boolean denied : new boolean[]{true, false}) {
            JSONObject report = FilesystemObservation.collect(table(), path -> {
                if (denied) throw new SecurityException("private-error");
                throw new IOException("private-error");
            });
            JSONObject row = report.getJSONArray("rows").getJSONObject(0);
            assertEquals(denied ? "BLOCKED" : "MISSING", row.getString("status"));
            assertEquals(denied ? "PERMISSION_DENIED" : "READ_UNAVAILABLE", row.getString("reason"));
            assertTrue(row.isNull("sha256"));
            assertFalse(report.toString().contains("private-error"));
        }
    }
    @Test public void oversizedReadNeverReportsTruncatedDigest() throws Exception {
        JSONObject row = FilesystemObservation.collect(table(), path -> new ByteArrayInputStream(
            new byte[FilesystemObservation.MAX_BYTES + 1])).getJSONArray("rows").getJSONObject(0);
        assertEquals("READ_LIMIT_EXCEEDED", row.getString("reason"));
        assertEquals("MISSING", row.getString("status"));
        assertTrue(row.isNull("sha256"));
        assertTrue(row.isNull("line_count"));
    }
    @Test public void boundarySizeIsCompleteAndStreamIsClosed() throws Exception {
        int[] closed = {0};
        FilesystemObservation.collect(table(), path -> new ByteArrayInputStream(new byte[FilesystemObservation.MAX_BYTES]) {
            @Override public void close() throws IOException { closed[0]++; super.close(); }
        });
        assertEquals(4, closed[0]);
    }
    @Test public void unknownOrDuplicateSurfaceFailsClosed() throws Exception {
        for (boolean duplicate : new boolean[]{true, false}) {
            JSONObject table = table();
            JSONArray rows = table.getJSONArray("observation_surfaces");
            if (duplicate) rows.put(1, rows.getJSONObject(0));
            else rows.getJSONObject(0).put("surface", "/private-file");
            assertThrows(IllegalArgumentException.class, () -> FilesystemObservation.collect(table,
                path -> new ByteArrayInputStream(new byte[0])));
        }
    }
}
