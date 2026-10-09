package dev.cyclone.cloak.cyclone

import dev.cyclone.cloak.data.*
import dev.cyclone.cloak.forge.*
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What Cloak writes, read the way Cyclone reads it (CycloneCloakProfileBinding, alpha.121+), with the envelope cases of
 * Cyclone's CycloneCloakProfileBindingTest.
 */
class CycloneEnvelopeTest {
    private val b = "Cyclone_0123456789abcdef"
    private val pkg = "com.example.app"

    private fun envelope(user: Any, value: JSONObject, profileId: String = b, packageName: String = pkg) = JSONObject()
        .put("profileId", profileId).put("androidUserId", user).put("packageName", packageName)
        .put("value", value).put("state", "ready")

    private val profile = CloakForge.forgeProfile("Work phone", "ef".repeat(32), TestPhones["pixel_7"])

    @Test
    fun aValidVersionOneSummaryIsBoundAndShown() {
        val value = CloakIdentity.summary("pixel-8-work", profile)
        val read = CycloneReader.summary(envelope(11, value), b, 11, pkg)!!
        assertEquals("Work phone", read["name"])
        assertEquals("Google", read["manufacturer"])
        assertEquals("Pixel 7", read["model"])
        assertEquals("13", read["androidRelease"])
        assertEquals(33, read["sdkInt"])
        assertTrue(value.toString().toByteArray().size <= 4096)
    }

    @Test
    fun anUnknownIdentityVersionIsStillBoundButShowsNoSummary() {
        val value = CloakIdentity.summary("pixel-8-work", profile).put("identityVersion", 2)
        assertEquals("pixel-8-work", CycloneReader.cloakProfileId(envelope(11, value), b, 11, pkg))
        assertNull(CycloneReader.summary(envelope(11, value), b, 11, pkg))
    }

    @Test
    fun aMismatchedTupleOrAStringUserNumberNeverCounts() {
        val value = CloakIdentity.summary("pixel-8-work", profile)
        assertNull(CycloneReader.cloakProfileId(envelope(12, value), b, 11, pkg))
        assertNull(CycloneReader.cloakProfileId(envelope("11", value), b, 11, pkg))
        assertNull(CycloneReader.cloakProfileId(envelope(11, value, packageName = "com.other"), b, 11, pkg))
        assertNull(CycloneReader.cloakProfileId(envelope(11, value, profileId = "Cyclone_ffffffffffffffff"), b, 11, pkg))
        assertNull(CycloneReader.cloakProfileId(envelope(11, JSONObject().put("cloakProfileId", " ")), b, 11, pkg))
    }

    @Test
    fun mainIsRefusedAndCloakNeverAsks() {
        val api = FakeCyclone().apply { profiles += FakeCyclone.Profile(b, "B", 11, listOf(pkg)) }
        val refused = runCatching { api.setConfig(CycloneProfile.OWNER, 0, pkg, JSONObject()) }.exceptionOrNull()
        assertEquals("NO_SUCH_PROFILE", (refused as com.cyclone.connector.client.CycloneConnectorException).code)
        // A binding naming Main can't even be stored, and the planner never targets Main.
        assertFalse(runCatching { CloakBindingStore.validate(CycloneProfile.OWNER, 0, pkg) }.isSuccess)
        val snapshot = ProfilesSnapshot.parse(api.profiles())
        assertFalse(snapshot.byId(CycloneProfile.OWNER)!!.bindable)
    }

    @Test
    fun theSummaryNeverCarriesSecretsOrHardwareIdentifiers() {
        val text = CloakIdentity.summary("pixel-8-work", profile).toString()
        val identifiers = profile.getJSONObject("identifiers")
        for (key in identifiers.keys()) {
            val value = identifiers.optString(key)
            if (value.length >= 6) assertFalse("$key leaked", text.contains(value))
        }
        assertFalse(text.contains(profile.getJSONObject("device").getString("fingerprint")))
        assertEquals(
            setOf("cloakProfileId", "identityVersion", "name", "manufacturer", "model", "androidRelease", "sdkInt"),
            JSONObject(text).keys().asSequence().toSet(),
        )
    }

    @Test
    fun longOrOddFieldsStayInsideCyclonesLimits() {
        val odd = JSONObject(profile.toString()).put("name", "A\u0000" + "x".repeat(200))
        odd.getJSONObject("device").put("sdk_int", 5000)
        val value = CloakIdentity.summary("c".repeat(300), odd)
        assertEquals(160, value.getString("cloakProfileId").length)
        assertEquals(80, value.getString("name").length)
        assertFalse(value.has("sdkInt"))
        assertNotNull(CycloneReader.cloakProfileId(envelope(11, value), b, 11, pkg))
    }
}
