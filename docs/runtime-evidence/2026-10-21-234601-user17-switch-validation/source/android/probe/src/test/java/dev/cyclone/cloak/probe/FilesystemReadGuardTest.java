package dev.cyclone.cloak.probe;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class FilesystemReadGuardTest {
    private JSONObject table() throws Exception {
        String[] ids = {"build_prop", "proc_version", "proc_cpuinfo", "maps_snapshot"};
        String[] paths = {"/system/build.prop", "/proc/version", "/proc/cpuinfo", "/proc/self/maps"};
        JSONArray rows = new JSONArray();
        for (int i = 0; i < ids.length; i++) rows.put(new JSONObject()
            .put("field_path", "diagnostics.filesystem." + ids[i]).put("surface", paths[i]));
        return new JSONObject().put("observation_surfaces", rows);
    }
    @Test public void nullStreamsBecomeMissingForEverySurface() throws Exception {
        JSONArray failures = new JSONArray();
        JSONObject report = FilesystemReadGuard.collect(table(), path -> null, 1000, failures);
        assertEquals(4, report.getJSONArray("rows").length());
        for (int i = 0; i < 4; i++) {
            assertEquals("MISSING", report.getJSONArray("rows").getJSONObject(i).getString("status"));
            assertTrue(report.getJSONArray("rows").getJSONObject(i).isNull("sha256"));
        }
    }
    @Test public void emptyStreamsRemainObservationsForEverySurface() throws Exception {
        JSONObject report = FilesystemReadGuard.collect(table(), path -> new ByteArrayInputStream(new byte[0]),
            1000, new JSONArray());
        for (int i = 0; i < 4; i++) {
            JSONObject row = report.getJSONArray("rows").getJSONObject(i);
            assertEquals("OBSERVED", row.getString("status"));
            assertEquals(0, row.getInt("line_count"));
            assertEquals(0, row.getInt("byte_count"));
        }
    }
    @Test public void absentStreamsRemainMissingForEverySurface() throws Exception {
        JSONObject report = FilesystemReadGuard.collect(table(), path -> { throw new FileNotFoundException("private"); },
            1000, new JSONArray());
        for (int i = 0; i < 4; i++)
            assertEquals("MISSING", report.getJSONArray("rows").getJSONObject(i).getString("status"));
        assertFalse(report.toString().contains("private"));
    }
    @Test public void malformedAndThrowingReadersNeverAbortCollection() throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            final int selected = mode;
            JSONArray failures = new JSONArray();
            FilesystemObservation.Reader reader = selected == 0 ? null : path -> {
                if (selected == 1) throw new NullPointerException("secret-token");
                return new InputStream() {
                    public int read() { return 0; }
                    @Override public int read(byte[] buffer, int offset, int length) { return length + 1; }
                };
            };
            JSONObject report = FilesystemReadGuard.collect(table(), reader, 1000, failures);
            assertEquals(4, failures.length());
            assertEquals(4, report.getJSONArray("rows").length());
            assertFalse(failures.toString().contains("secret-token"));
        }
    }
    @Test public void eachSlowSurfaceTimesOutWithoutBlockingOtherRows() throws Exception {
        String[] paths = {"/system/build.prop", "/proc/version", "/proc/cpuinfo", "/proc/self/maps"};
        for (String slow : paths) {
            JSONArray failures = new JSONArray();
            JSONObject report = FilesystemReadGuard.collect(table(), path -> {
                if (path.equals(slow)) {
                    try { Thread.sleep(60_000); }
                    catch (InterruptedException cancelled) { throw new IOException(); }
                }
                return new ByteArrayInputStream(new byte[0]);
            }, 50, failures);
            assertEquals(1, failures.length());
            assertEquals("READ_TIMEOUT", failures.getJSONObject(0).getString("reason"));
            int observed = 0;
            for (int i = 0; i < 4; i++)
                if (report.getJSONArray("rows").getJSONObject(i).getString("status").equals("OBSERVED")) observed++;
            assertEquals(3, observed);
        }
    }
    @Test public void readAndCloseHangsAlsoHaveDeadlines() throws Exception {
        for (boolean close : new boolean[]{false, true}) {
            JSONArray failures = new JSONArray();
            JSONObject report = FilesystemReadGuard.collect(table(), path -> new ByteArrayInputStream(new byte[0]) {
                private void delay() {
                    try { Thread.sleep(60_000); }
                    catch (InterruptedException expected) { Thread.currentThread().interrupt(); }
                }
                @Override public synchronized int read(byte[] b, int off, int len) { if (!close) delay(); return -1; }
                @Override public void close() { if (close) delay(); }
            }, 30, failures);
            assertEquals(4, failures.length());
            for (int i = 0; i < 4; i++) {
                assertEquals("MISSING", report.getJSONArray("rows").getJSONObject(i).getString("status"));
                assertEquals("READ_TIMEOUT", failures.getJSONObject(i).getString("reason"));
            }
        }
    }
    @Test public void lateWorkerCannotMutateReturnedReport() throws Exception {
        CountDownLatch release = new CountDownLatch(1), closed = new CountDownLatch(1);
        JSONArray failures = new JSONArray();
        try {
            JSONObject report = FilesystemReadGuard.collect(table(), path -> {
                if (!path.equals("/system/build.prop")) return new ByteArrayInputStream(new byte[0]);
                while (true) {
                    try { release.await(); break; } catch (InterruptedException ignored) { }
                }
                return new ByteArrayInputStream(new byte[0]) {
                    @Override public void close() { closed.countDown(); }
                };
            }, 40, failures);
            String before = report.toString();
            release.countDown();
            assertTrue(closed.await(2, TimeUnit.SECONDS));
            assertEquals(before, report.toString());
            assertEquals("READ_TIMEOUT", failures.getJSONObject(0).getString("reason"));
        } finally { release.countDown(); }
    }
    @Test public void permissionDenialStaysBlockedAndOversizeKeepsNoDigest() throws Exception {
        JSONObject blocked = FilesystemReadGuard.collect(table(), path -> { throw new SecurityException("private"); },
            1000, new JSONArray());
        JSONObject oversize = FilesystemReadGuard.collect(table(), path -> new ByteArrayInputStream(
            new byte[FilesystemObservation.MAX_BYTES + 1]), 1000, new JSONArray());
        for (int i = 0; i < 4; i++) {
            assertEquals("BLOCKED", blocked.getJSONArray("rows").getJSONObject(i).getString("status"));
            assertEquals("READ_LIMIT_EXCEEDED", oversize.getJSONArray("rows").getJSONObject(i).getString("reason"));
            assertTrue(oversize.getJSONArray("rows").getJSONObject(i).isNull("sha256"));
        }
    }
}
