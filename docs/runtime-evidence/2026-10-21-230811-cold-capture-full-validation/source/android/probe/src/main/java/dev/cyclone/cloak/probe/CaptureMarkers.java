package dev.cyclone.cloak.probe;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.json.JSONObject;

/** Private, fixed-shape lifecycle evidence; contains no readings or exception text. */
final class CaptureMarkers {
    static void write(File directory, String requestId, long generation, int uid, int pid,
            String phase, String outcome) throws Exception {
        if (!("start".equals(phase) && "STARTED".equals(outcome))
                && !("end".equals(phase) && ("COMPLETED".equals(outcome) || "FAILED".equals(outcome))))
            throw new IllegalArgumentException("INVALID_CAPTURE_MARKER");
        JSONObject marker = new JSONObject().put("format", 1).put("request_id", requestId)
            .put("session_generation", generation).put("uid", uid).put("pid", pid)
            .put("phase", phase).put("outcome", outcome).put("timestamp_ms", System.currentTimeMillis());
        File target = new File(directory, "capture-" + phase + ".json");
        File partial = new File(directory, "capture-" + phase + ".json.tmp");
        try {
            Files.write(partial.toPath(), marker.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(partial.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(partial.toPath()); }
    }
}
