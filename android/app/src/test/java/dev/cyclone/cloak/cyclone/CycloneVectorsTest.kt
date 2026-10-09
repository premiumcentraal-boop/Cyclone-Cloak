package dev.cyclone.cloak.cyclone

import com.cyclone.connector.client.CycloneConnectorException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Cloak's bridge against Cyclone's own answers: `vectors.json` from the Cyclone 5.0.0-alpha.122.dev1 connector kit
 * (Cyclone-Connector-Schemas zip, `tools/cyclone-connector-sdk/schemas/vectors.json`). Cyclone's build runs the same
 * file against its connector code, so these are the answers a real Cyclone gives.
 */
class CycloneVectorsTest {
    private val vectors = JSONObject(
        javaClass.classLoader!!.getResource("cyclone-connector/vectors.json")!!.readText(),
    )

    /** Answers a request with the vector for the default caller whose request matches exactly. */
    private inner class VectorApi(private val profile: String? = null) : CycloneApi {
        private fun answer(method: String, args: JSONObject = JSONObject()): JSONObject {
            val cases = vectors.getJSONArray("cases")
            for (i in 0 until cases.length()) {
                val case = cases.getJSONObject(i)
                if (case.has("caller") && case.optJSONObject("caller")?.has("manifest") != true) continue
                if (case.has("caller") && profile != "config") continue
                if (!case.has("caller") && profile == "config") continue
                val request = case.optJSONObject("request") ?: continue
                if (request.optString("method") != method) continue
                if (!sameJson(request.optJSONObject("args") ?: JSONObject(), args)) continue
                val answer = case.getJSONObject("answer")
                if (answer.optBoolean("ok")) return answer.getJSONObject("result")
                throw CycloneConnectorException(answer.getJSONObject("error").getString("code"), "vector ${case.getString("name")}")
            }
            throw AssertionError("no vector for $method $args")
        }

        override fun hello() = answer("hello", JSONObject().put("contract", "cyclone.connector/1"))
        override fun profiles() = answer("profiles")
        private fun tuple(p: String, u: Int, k: String) = JSONObject().put("profileId", p).put("androidUserId", u).put("packageName", k)
        override fun getConfig(profileId: String, androidUserId: Int, packageName: String) =
            answer("config.get.v1", tuple(profileId, androidUserId, packageName))
        override fun setConfig(profileId: String, androidUserId: Int, packageName: String, value: JSONObject?) =
            answer("config.set.v1", tuple(profileId, androidUserId, packageName).put("value", value ?: JSONObject.NULL))
        override fun configStatus(profileId: String, androidUserId: Int, packageName: String, state: String) =
            answer("config.status.v1", tuple(profileId, androidUserId, packageName).put("state", state))
        override fun events(since: Long) = answer("events", JSONObject().put("since", since))
        override fun rootStatus() = answer("root.status.v1")
        override fun requestOpenProfile(profileId: String) = answer("profiles.open.request.v1", JSONObject().put("profileId", profileId))
    }

    private fun code(block: () -> Unit): String = try {
        block()
        fail("expected a refusal")
        ""
    } catch (error: CycloneConnectorException) {
        error.code
    }

    @Test
    fun helloSaysMinorThree() {
        val gate = CycloneGate.of(VectorApi().hello())
        assertEquals(3, gate.minor)
        assertTrue(gate.approved)
        assertTrue("profiles.apps.read" in gate.pending)
        // The vector caller has neither scope: both features stay hidden.
        assertFalse(gate.rootStatus)
        assertFalse(gate.openRequests)
        assertFalse(gate.bindings)
    }

    @Test
    fun rootStatusAndOpenRequestWithoutTheirScopesAreRefused() {
        assertEquals("SCOPE_NOT_GRANTED", code { VectorApi().rootStatus() })
        assertEquals("SCOPE_NOT_GRANTED", code { VectorApi().requestOpenProfile("owner") })
        val outcome = OpenRequests.ask(VectorApi(), CycloneProfile("owner", "This phone", "owner", "ready", null, null))
        assertTrue(outcome.message.contains("Settings → Connectors"))
    }

    @Test
    fun profilesWithoutAppsReadGiveNothingToBind() {
        val snapshot = ProfilesSnapshot.parse(VectorApi().profiles())
        assertEquals("Cyclone_aaaaaaaaaaaaaaaa", snapshot.current)
        assertTrue(snapshot.profiles.any { it.isOwner })
        assertTrue(snapshot.registry.all { it.packages == null })
        val plan = ReconcilePlanner.plan(
            Placement.Main, snapshot,
            listOf(binding("Cyclone_aaaaaaaaaaaaaaaa", 10, "com.instagram.android")),
        ) { JSONObject() }
        assertTrue(plan.desired.isEmpty())
    }

    @Test
    fun eventsVectorSwitchesAndThenHasNothingNew() {
        val first = CycloneEvents.pull(VectorApi(), CycloneEvents.Cursor(0, 0))
        assertEquals(1, first.events.size)
        assertTrue(first.helloAgain)
        assertEquals(CycloneEvents.Cursor(1, 1), first.cursor)
        val second = CycloneEvents.pull(VectorApi(), first.cursor)
        assertTrue(second.events.isEmpty())
        assertFalse(second.reconcile)
    }

    @Test
    fun theWholeSyncRunsAgainstTheVectorsWithoutWritingAnything() {
        val local = FakeLocal(0, listOf(binding("Cyclone_aaaaaaaaaaaaaaaa", 10, "com.instagram.android")))
        val report = CycloneSyncEngine(VectorApi(), local, BindingReconciler(VectorApi(), sleep = {})).sync()
        assertEquals("Connected", report.headline)
        assertTrue(report.message.contains("profiles.config"))
        assertEquals(CycloneEvents.Cursor(1, 1), local.cursor)
        assertEquals(null, local.pushed)
    }

    @Test
    fun configVectorsMatchWhatTheReconcilerExpects() {
        val api = VectorApi("config")
        val cases = vectors.getJSONArray("cases")
        val names = (0 until cases.length()).map { cases.getJSONObject(it).getString("name") }
        assertTrue("config empty" in names && "config wrong user" in names && "config unscoped package" in names)
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val request = case.optJSONObject("request") ?: continue
            if (!request.optString("method").startsWith("config.")) continue
            val answer = case.getJSONObject("answer")
            if (!answer.optBoolean("ok")) {
                // Refusals Cloak handles per binding, or as a whole-connector stop.
                assertTrue(answer.getJSONObject("error").getString("code") in
                    setOf("NO_SUCH_PROFILE", "BAD_REQUEST", "SCOPE_NOT_GRANTED"))
                continue
            }
            val result = answer.getJSONObject("result")
            assertEquals(1, result.getInt("version"))
            assertTrue(result.get("androidUserId") is Int)
            assertTrue(result.getString("state") in setOf("unknown", "ready", "degraded", "failed"))
            assertTrue(result.isNull("value") || result.get("value") is JSONObject)
        }
        // And the empty tuple reads as "nothing held": the reconciler would write it.
        val empty = (0 until cases.length()).map { cases.getJSONObject(it) }.first { it.getString("name") == "config empty" }
        val args = empty.getJSONObject("request").getJSONObject("args")
        val held = api.getConfig(args.getString("profileId"), args.getInt("androidUserId"), args.getString("packageName"))
        assertTrue(held.optJSONObject("value") == null)
    }
}
