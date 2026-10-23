package dev.cyclone.cloak.probe;

import java.io.File;
import java.nio.file.Files;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ProbeEvidenceExportTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void directoryUsesProvidedUserScopedExternalRoot() {
        for (int user : new int[]{0, 17}) {
            File root = new File("/storage/emulated/" + user + "/Android/data/probe/files");
            assertEquals(new File(root, "standalone-evidence"), ProbeEvidenceExport.directory(root));
        }
    }

    @Test public void unavailableStorageDoesNotFallBackToUserZero() {
        try { ProbeEvidenceExport.directory(null); fail("missing root accepted"); }
        catch (IllegalStateException expected) { assertEquals("EXTERNAL_EVIDENCE_UNAVAILABLE", expected.getMessage()); }
    }

    @Test public void exportRemovesRawValuesButPreservesDigestsMarkersAndDenials() throws Exception {
        JSONObject report = new JSONObject().put("request_id", "user17-capture1").put("uid", 1712345)
            .put("token", "secret").put("rows", new JSONArray().put(new JSONObject()
                .put("id", "sample").put("actual", "private").put("expected", "private")
                .put("actual_sha256", "digest").put("status", "BLOCKED").put("reason", "PERMISSION_DENIED")));
        File directory = temporary.newFolder();
        ProbeEvidenceExport.write(directory, "report.json", report);
        String text = new String(Files.readAllBytes(new File(directory, "report.json").toPath()), java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(text.contains("private")); assertFalse(text.contains("secret"));
        assertTrue(text.contains("digest")); assertTrue(text.contains("PERMISSION_DENIED"));
        assertEquals(1712345, new JSONObject(text).getInt("uid"));
        assertFalse(new File(directory, "report.json.tmp").exists());
    }

    @Test public void filenamesCannotEscapeEvidenceDirectory() throws Exception {
        try { ProbeEvidenceExport.write(temporary.newFolder(), "../raw.json", new JSONObject()); fail("path accepted"); }
        catch (IllegalArgumentException expected) { assertEquals("INVALID_EVIDENCE_FILE", expected.getMessage()); }
    }
}
