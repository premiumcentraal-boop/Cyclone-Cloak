package dev.cyclone.cloak.probe;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProbeCaptureSessionTest {
    private ProbeCaptureSession session(long generation, String request) {
        return new ProbeCaptureSession(generation, request, "a", false, 0);
    }

    private JSONObject row(String id) throws Exception {
        return new JSONObject().put("id", id).put("status", "PASS").put("reason", "MATCH")
            .put("source", "test").put("expected", JSONObject.NULL).put("actual", JSONObject.NULL)
            .put("detail", "MATCH").put("duration_ms", 0);
    }

    private ProbeCaptureSession complete(long generation) throws Exception {
        ProbeCaptureSession capture = session(generation, "request");
        for (String id : ProbeCaptureSession.EXPECTED_IDS) capture.rows.put(row(id));
        return capture;
    }

    @Test public void normalCaptureHasExactlyTheWholeReport() throws Exception {
        ProbeCaptureSession capture = complete(1);
        capture.validateComplete();
        assertEquals(89, capture.rows.length());
        CaptureCoordinator gate = new CaptureCoordinator();
        long token = gate.begin();
        assertTrue(gate.commit(token, capture::validateComplete));
    }

    @Test public void javaAndHostCompletenessInventoriesAgree() throws Exception {
        org.json.JSONArray ids = new org.json.JSONArray(new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get("src/main/assets/report-row-ids.json")), java.nio.charset.StandardCharsets.UTF_8));
        java.util.Set<String> host = new java.util.LinkedHashSet<>();
        for (int i = 0; i < ids.length(); i++) host.add(ids.getString(i));
        assertEquals(89, ids.length());
        assertEquals(ProbeCaptureSession.EXPECTED_IDS, host);
    }

    @Test public void supersededWorkerCannotWriteOrUpdateUi() throws Exception {
        CaptureCoordinator gate = new CaptureCoordinator();
        long older = gate.begin(), newer = gate.begin();
        AtomicReference<String> report = new AtomicReference<>("initial");
        assertFalse(gate.commit(older, () -> report.set("old")));
        assertTrue(gate.commit(newer, () -> report.set("new")));
        assertFalse(gate.commit(older, () -> report.set("old-ui")));
        assertEquals("new", report.get());
    }

    @Test public void overlappingIntentsKeepWorkerStateSeparate() throws Exception {
        CaptureCoordinator gate = new CaptureCoordinator();
        ProbeCaptureSession older = session(gate.begin(), "old");
        older.profile = new JSONObject().put("id", "old-profile");
        ProbeCaptureSession newer = session(gate.begin(), "new");
        newer.profile = new JSONObject().put("id", "new-profile");
        newer.diagnosticExpectations.put("new-rule", true);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicReference<String> output = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                finish.await();
                older.rows.put(row("build.MODEL"));
                gate.commit(older.generation, () -> output.set(older.requestId));
            } catch (Throwable e) { failure.set(e); }
        });
        worker.start();
        newer.rows.put(row("build.MODEL"));
        gate.commit(newer.generation, () -> output.set(newer.requestId));
        finish.countDown();
        worker.join(2000);
        assertFalse(worker.isAlive());
        assertNull(failure.get());
        assertEquals("new", output.get());
        assertEquals(1, older.rows.length());
        assertEquals(1, newer.rows.length());
        assertEquals("old-profile", older.profile.getString("id"));
        assertEquals(0, older.diagnosticExpectations.length());
    }

    @Test public void duplicateIdIsStructuredFailureEvenWithEightyNineRows() throws Exception {
        ProbeCaptureSession capture = complete(1);
        capture.rows.put(88, row("build.MODEL"));
        ProbeCaptureSession.InvalidCapture e = assertThrows(ProbeCaptureSession.InvalidCapture.class,
            capture::validateComplete);
        assertEquals("DUPLICATE_ROW_ID", e.reason);
        assertEquals("build.MODEL", e.rowId);
    }

    @Test public void emptyIdIsRejected() throws Exception {
        ProbeCaptureSession capture = complete(1);
        capture.rows.put(0, row(" "));
        assertEquals("ROW_ID_EMPTY", assertThrows(ProbeCaptureSession.InvalidCapture.class,
            capture::validateComplete).reason);
    }

    @Test public void missingLastRowIsNotACompleteReport() throws Exception {
        ProbeCaptureSession capture = complete(1);
        capture.rows.remove(88);
        assertEquals("REPORT_INCOMPLETE", assertThrows(ProbeCaptureSession.InvalidCapture.class,
            capture::validateComplete).reason);
    }

    @Test public void unknownIdDoesNotSubstituteForMissingRow() throws Exception {
        ProbeCaptureSession capture = complete(1);
        capture.rows.put(88, row("unknown.row"));
        assertEquals("UNEXPECTED_ROW_ID", assertThrows(ProbeCaptureSession.InvalidCapture.class,
            capture::validateComplete).reason);
    }

    @Test public void rowWithoutRequiredReportFieldsIsRejected() throws Exception {
        ProbeCaptureSession capture = complete(1);
        capture.rows.getJSONObject(0).remove("duration_ms");
        assertEquals("ROW_INCOMPLETE", assertThrows(ProbeCaptureSession.InvalidCapture.class,
            capture::validateComplete).reason);
    }

    @Test public void requestMetadataDoesNotFollowLaterIntentValues() {
        String[] intent = {"original-request", "a"};
        ProbeCaptureSession capture = new ProbeCaptureSession(1, intent[0], intent[1], true, 17);
        intent[0] = "replacement-request";
        intent[1] = "b";
        assertEquals("original-request", capture.requestId);
        assertEquals("a", capture.requestedFixture);
        assertEquals(17, capture.expectedUser);
    }

    @Test public void permissionCallbackCanStartSessionOnlyOnce() {
        ProbeCaptureSession capture = session(1, "request");
        assertTrue(capture.startOnce());
        assertFalse(capture.startOnce());
    }

    @Test public void destroyingOldActivityDoesNotInvalidateNewActivity() throws Exception {
        CaptureCoordinator gate = new CaptureCoordinator();
        long old = gate.begin(), current = gate.begin();
        gate.cancel(old);
        assertTrue(gate.commit(current, () -> { }));
        gate.cancel(current);
        assertFalse(gate.commit(current, () -> fail("destroyed worker wrote")));
    }
}
