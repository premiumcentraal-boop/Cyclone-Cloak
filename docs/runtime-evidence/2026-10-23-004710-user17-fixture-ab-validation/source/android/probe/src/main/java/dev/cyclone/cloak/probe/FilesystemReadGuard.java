package dev.cyclone.cloak.probe;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.*;

/** Deadline boundary for the existing filesystem observation collector. */
final class FilesystemReadGuard {
    static final long TIMEOUT_MS = 3000;
    // Native I/O may ignore interruption. Bound stranded workers across captures.
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(0, 4, 30,
        TimeUnit.SECONDS, new SynchronousQueue<>(), task -> {
            Thread worker = new Thread(task, "cloak-filesystem-reader");
            worker.setDaemon(true);
            return worker;
        });
    private FilesystemReadGuard() { }
    static JSONObject collect(JSONObject table, JSONArray failures) throws Exception {
        return collect(table, path -> Files.newInputStream(Paths.get(path)), TIMEOUT_MS, failures);
    }
    static JSONObject collect(JSONObject table, FilesystemObservation.Reader reader,
                              long timeoutMs, JSONArray failures) throws Exception {
        if (timeoutMs <= 0 || timeoutMs > TIMEOUT_MS) throw new IllegalArgumentException("INVALID_READ_BUDGET");
        return FilesystemObservation.collect(table, path -> {
            Future<byte[]> work = null;
            try {
                work = WORKERS.submit(() -> snapshot(reader, path));
                return new ByteArrayInputStream(work.get(timeoutMs, TimeUnit.MILLISECONDS));
            } catch (TimeoutException timeout) {
                record(failures, path, "READ_TIMEOUT", timeout);
                throw new IOException();
            } catch (RejectedExecutionException busy) {
                record(failures, path, "READER_CAPACITY_UNAVAILABLE", busy);
                throw new IOException();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                record(failures, path, "READ_INTERRUPTED", interrupted);
                throw new IOException();
            } catch (ExecutionException failed) {
                Throwable error = failed.getCause();
                String reason = error instanceof ReadFault ? ((ReadFault) error).reason
                    : error instanceof SecurityException || error instanceof java.nio.file.AccessDeniedException
                    ? "PERMISSION_DENIED" : error instanceof IOException ? "READ_UNAVAILABLE" : "READER_EXCEPTION";
                record(failures, path, reason, error);
                if (error instanceof SecurityException) throw (SecurityException) error;
                if (error instanceof java.nio.file.AccessDeniedException) throw (java.nio.file.AccessDeniedException) error;
                throw new IOException();
            } finally { if (work != null) work.cancel(true); }
        });
    }

    private static byte[] snapshot(FilesystemObservation.Reader reader, String path) throws IOException {
        if (reader == null) throw new ReadFault("NULL_READER");
        try (InputStream stream = reader.open(path)) {
            if (stream == null) throw new ReadFault("NULL_STREAM");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            while (bytes.size() <= FilesystemObservation.MAX_BYTES) {
                if (Thread.currentThread().isInterrupted()) throw new ReadFault("READ_INTERRUPTED");
                int requested = Math.min(buffer.length, FilesystemObservation.MAX_BYTES + 1 - bytes.size());
                int count = stream.read(buffer, 0, requested);
                if (count == -1) break;
                if (count <= 0 || count > requested) throw new ReadFault("MALFORMED_STREAM");
                bytes.write(buffer, 0, count);
            }
            // One extra byte lets the existing collector reject oversize content.
            return bytes.toByteArray();
        }
    }

    private static void record(JSONArray failures, String path, String reason, Throwable error) {
        String id;
        switch (path) {
            case "/system/build.prop": id = "diagnostics.filesystem.build_prop"; break;
            case "/proc/version": id = "diagnostics.filesystem.proc_version"; break;
            case "/proc/cpuinfo": id = "diagnostics.filesystem.proc_cpuinfo"; break;
            case "/proc/self/maps": id = "diagnostics.filesystem.maps_snapshot"; break;
            default: id = "capture.filesystem";
        }
        try { failures.put(ProbeFailureEvidence.describe(error, id).put("reason", reason)); }
        catch (Exception ignored) { /* Failure metadata must not block the report. */ }
    }

    private static final class ReadFault extends IOException {
        final String reason;
        ReadFault(String reason) { this.reason = reason; }
    }
}
