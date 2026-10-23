package dev.cyclone.cloak.probe;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Exactly the same synthetic vectors are evaluated by Python. Not device evidence. */
@RunWith(Parameterized.class)
public class DiagnosticExpectationTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> cases() throws Exception {
        JSONArray cases;
        try (InputStream input = DiagnosticExpectationTest.class.getResourceAsStream("/expectation-cases.json")) {
            assertNotNull(input);
            cases = new JSONArray(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        Collection<Object[]> result = new ArrayList<>();
        for (int i = 0; i < cases.length(); i++) result.add(new Object[]{cases.getJSONObject(i).getString("name"), cases.getJSONObject(i)});
        return result;
    }
    private final JSONObject example;
    public DiagnosticExpectationTest(String name, JSONObject example) { this.example = example; }
    @Test public void evaluateSharedCase() throws Exception {
        JSONObject actual = DiagnosticExpectation.evaluate(example.getJSONObject("rule"), example.get("actual"));
        assertEquals(example.getString("status"), actual.getString("status"));
        assertEquals(example.getString("reason"), actual.getString("reason"));
    }
}
