package dev.cyclone.cloak.probe;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiagnosticFixtureTest {
    @Test public void validFixtureAndUnknownVersion() throws Exception {
        JSONObject fixture = new JSONObject("{\"schema_version\":1,\"fixture\":\"a\",\"rows\":{\"x\":{\"policy\":\"PRESENT\"}}}");
        assertEquals("PRESENT", DiagnosticExpectation.fixture(fixture, "a").getJSONObject("x").getString("policy"));
        fixture.put("schema_version", 2);
        assertThrows(Exception.class, () -> DiagnosticExpectation.fixture(fixture, "a"));
    }
    @Test public void labelsAndMalformedRulesAreRejected() throws Exception {
        JSONObject fixture = new JSONObject("{\"schema_version\":1,\"fixture\":\"b\",\"rows\":{\"x\":{\"policy\":\"PRESENT\"}}}");
        assertThrows(Exception.class, () -> DiagnosticExpectation.fixture(fixture, "a"));
        fixture.getJSONObject("rows").getJSONObject("x").put("policy", "VERIFIED");
        assertThrows(Exception.class, () -> DiagnosticExpectation.fixture(fixture, "b"));
    }
}
