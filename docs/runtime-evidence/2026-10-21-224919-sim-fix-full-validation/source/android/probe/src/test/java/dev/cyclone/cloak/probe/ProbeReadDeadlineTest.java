package dev.cyclone.cloak.probe;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProbeReadDeadlineTest {
    @Test public void nullAndSuccessfulValuesReturnWithoutInventingEvidence() throws Exception {
        assertNull(ProbeReadDeadline.call(() -> null));
        assertEquals("value", ProbeReadDeadline.call(() -> "value"));
    }
    @Test public void failuresKeepTheirTypeAndDenialPolicy() {
        SecurityException error = assertThrows(SecurityException.class,
            () -> ProbeReadDeadline.call(() -> { throw new SecurityException("private"); }));
        assertEquals("SecurityException", ProbeReportPolicy.failureReason(error));
    }
    @Test public void blockedReaderTimesOutAndFollowingReaderStillCompletes() throws Exception {
        Exception error = assertThrows(ProbeReadDeadline.ReadFailure.class,
            () -> ProbeReadDeadline.call(() -> { Thread.sleep(60_000); return null; }, 30));
        assertEquals("READ_TIMEOUT", ProbeReportPolicy.failureReason(error));
        assertEquals("next", ProbeReadDeadline.call(() -> "next"));
    }
    @Test public void aReadFailureNeverBecomesPass() throws Exception {
        org.json.JSONObject row = RealityReport.row("test.read", "expected", () -> {
            throw new ProbeReadDeadline.ReadFailure("READ_TIMEOUT");
        });
        assertEquals("MISSING", row.getString("status"));
        assertEquals("READ_TIMEOUT", row.getString("reason"));
    }
}
