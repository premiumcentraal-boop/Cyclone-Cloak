package dev.cyclone.cloak.probe;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class OptionalExpectationsTest {
    @Test public void subscriberExpectationRequiresAnAvailableSerial() throws Exception {
        JSONObject profile = new JSONObject().put("telephony", new JSONObject().put("mcc", "204").put("mnc", "08"));
        assertNull(ProbeReportPolicy.subscriberExpectation(profile));
        profile.put("identifiers", new JSONObject().put("sim_serial", "123"));
        assertNull(ProbeReportPolicy.subscriberExpectation(profile));
        profile.getJSONObject("identifiers").put("sim_serial", "89012345678901234567");
        assertEquals("204088901234567", ProbeReportPolicy.subscriberExpectation(profile));
    }

    @Test public void unavailableIdentifiersHaveNoExpectationAndCannotBecomePass() throws Exception {
        JSONObject profile = new JSONObject().put("identifiers", new JSONObject().put("android_id", "1234567890abcdef"));
        assertNull(ProbeReportPolicy.expectedAt(profile, "identifiers.serial"));
        assertNull(ProbeReportPolicy.expectedAt(profile, "camera.cameras"));
        assertEquals("OBSERVED", ProbeReportPolicy.status(false, false, true));
        assertEquals("1234567890abcdef", ProbeReportPolicy.expectedAt(profile, "identifiers.android_id"));
    }

    @Test public void explicitNullOrMalformedParentNeverSuppliesAnExpectation() throws Exception {
        assertNull(ProbeReportPolicy.expectedAt(new JSONObject().put("identifiers", JSONObject.NULL), "identifiers.serial"));
        assertNull(ProbeReportPolicy.expectedAt(new JSONObject().put("identifiers", "not-an-object"), "identifiers.serial"));
        assertNull(ProbeReportPolicy.expectedAt(new JSONObject(), null));
    }
}
