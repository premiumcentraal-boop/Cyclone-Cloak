package dev.cyclone.cloak.probe;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProbeFailureEvidenceTest {
    @Test public void typeRowAndSafeStackSurviveWithoutPrivateMessage() throws Exception {
        JSONObject report = ProbeFailureEvidence.describe(new NullPointerException("secret-token account@example.com"),
            "capture.reality");
        assertEquals("java.lang.NullPointerException", report.getString("exception_class"));
        assertEquals("capture.reality", report.getString("failed_row_id"));
        assertTrue(report.getJSONArray("stack_trace").length() > 0);
        assertTrue(report.getBoolean("message_redacted"));
        assertFalse(report.toString().contains("secret-token"));
        assertFalse(report.toString().contains("account@example.com"));
    }
    @Test public void stackIsBoundedAndSourcePathsAreNeverExported() throws Exception {
        Exception error = new Exception("private");
        StackTraceElement[] stack = new StackTraceElement[100];
        for (int i = 0; i < stack.length; i++)
            stack[i] = new StackTraceElement("example.Reader", "read", "/private/secret-file", 12);
        error.setStackTrace(stack);
        JSONObject report = ProbeFailureEvidence.describe(error, null);
        assertEquals(32, report.getJSONArray("stack_trace").length());
        assertTrue(report.getBoolean("stack_truncated"));
        assertEquals("capture.unknown", report.getString("failed_row_id"));
        assertFalse(report.toString().contains("/private/secret-file"));
    }
}
