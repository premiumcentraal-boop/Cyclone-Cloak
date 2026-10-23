package dev.cyclone.cloak.probe;

import java.io.File;
import java.nio.file.Files;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class CaptureMarkersTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void startAndEndRetainRequestAndProcessWithoutReadings() throws Exception {
        File directory = temporary.newFolder();
        CaptureMarkers.write(directory, "capture-2", 1, 1712345, 42, "start", "STARTED");
        CaptureMarkers.write(directory, "capture-2", 1, 1712345, 42, "end", "COMPLETED");
        for (String phase : new String[]{"start", "end"}) {
            JSONObject marker = new JSONObject(new String(Files.readAllBytes(
                new File(directory, "capture-" + phase + ".json").toPath()), java.nio.charset.StandardCharsets.UTF_8));
            assertEquals("capture-2", marker.getString("request_id"));
            assertEquals(1712345, marker.getInt("uid"));
            assertEquals(42, marker.getInt("pid"));
            assertEquals(phase, marker.getString("phase"));
            assertEquals(8, marker.length());
            assertFalse(new File(directory, "capture-" + phase + ".json.tmp").exists());
        }
    }

    @Test public void failedEndIsExplicitAndNextStartDoesNotRelabelOldEnd() throws Exception {
        File directory = temporary.newFolder();
        CaptureMarkers.write(directory, "old", 1, 12345, 42, "end", "FAILED");
        CaptureMarkers.write(directory, "new", 2, 12345, 42, "start", "STARTED");
        String end = new String(Files.readAllBytes(new File(directory, "capture-end.json").toPath()), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("old", new JSONObject(end).getString("request_id"));
        assertEquals("FAILED", new JSONObject(end).getString("outcome"));
    }

    @Test public void unknownMarkerPhasesAndOutcomesAreRejected() throws Exception {
        for (String[] pair : new String[][]{{"other", "STARTED"}, {"start", "COMPLETED"}, {"end", "private error"}}) {
            try {
                CaptureMarkers.write(temporary.newFolder(), "id", 1, 1, 1, pair[0], pair[1]);
                fail("invalid marker accepted");
            } catch (IllegalArgumentException expected) {
                assertEquals("INVALID_CAPTURE_MARKER", expected.getMessage());
            }
        }
    }
}
