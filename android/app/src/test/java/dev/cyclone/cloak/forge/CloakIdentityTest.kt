package dev.cyclone.cloak.forge

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CloakIdentityTest {
    private val profile = CloakForge.forgeProfile("Vault 01", "ab".repeat(32), TestPhones["pixel_7"])

    @Test
    fun summaryCarriesOnlyDisplayFields() {
        val summary = CloakIdentity.summary("cloak-1", profile)
        assertEquals(1, summary.getInt("identityVersion"))
        assertEquals("cloak-1", summary.getString("cloakProfileId"))
        // Deterministic: reconcile compares it with what Cyclone holds.
        assertFalse(summary.has("boundAt"))
        assertEquals(summary.toString(), CloakIdentity.summary("cloak-1", profile).toString())
        assertEquals("Vault 01", summary.getString("name"))
        assertEquals("Google", summary.getString("manufacturer"))
        assertEquals("Pixel 7", summary.getString("model"))
        assertEquals("13", summary.getString("androidRelease"))
        assertEquals(33, summary.getInt("sdkInt"))
    }

    @Test
    fun summaryNeverCarriesIdentifiers() {
        val summary = CloakIdentity.summary("cloak-1", profile)
        assertFalse(summary.has("identifiers"))
        assertFalse(summary.has("fingerprint"))
        assertFalse(summary.has("imei_primary"))
        assertFalse(summary.has("serial"))
        val text = summary.toString()
        assertFalse(text.contains(profile.getJSONObject("identifiers").getString("imei_primary")))
        assertFalse(text.contains(profile.getJSONObject("identifiers").getString("serial")))
    }

    @Test
    fun summaryToleratesAMissingProfile() {
        val summary = CloakIdentity.summary("cloak-gone", null)
        assertEquals("cloak-gone", summary.getString("cloakProfileId"))
        assertFalse(summary.has("manufacturer"))
        assertFalse(summary.has("model"))
    }
}
