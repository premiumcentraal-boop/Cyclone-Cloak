package dev.cyclone.cloak.forge

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneDraftTest {
    private fun errors(draft: PhoneDraft) = draft.findings().filter { it.isError }

    @Test
    fun everyBuiltInPhoneRoundTripsThroughTheBuilderUnchanged() {
        for (phone in TestPhones.all) {
            val draft = PhoneDraft.from(phone)
            assertTrue(phone.id, draft.autoFingerprint)
            assertEquals(phone.id, emptyList<ProfileValidator.Finding>(), draft.findings())
            val again = draft.toTemplate()
            for ((name, block) in listOf("device" to again.device, "telephony" to again.telephony, "display" to again.display, "locale" to again.locale, "network" to again.network)) {
                val original = phone.toJson().getJSONObject(name)
                assertTrue("${phone.id}.$name: $original vs $block", original.similar(block))
            }
        }
    }

    @Test
    fun aBlankPhoneSaysWhatIsMissingFieldByField() {
        val fields = errors(PhoneDraft.blank("x")).map { it.field }.toSet()
        assertTrue(fields.containsAll(listOf("device.manufacturer", "device.model", "device.version_release", "device.sdk_int")))
        // Required fields come first; the finer rules wait until they're filled in.
        assertTrue(fields.all { it!!.startsWith("device.") })
    }

    @Test
    fun choosingAnAndroidVersionSetsItsSdk() {
        assertEquals("34", PhoneDraft.blank("x").withRelease("14").sdkInt)
        assertEquals("36", PhoneDraft.blank("x").withRelease("16").sdkInt)
        // An unknown release leaves the SDK as typed.
        assertEquals("29", PhoneDraft.blank("x").copy(sdkInt = "29").withRelease("10").sdkInt)
    }

    @Test
    fun theFingerprintIsComposedFromItsPartsSoThePartsCantDisagree() {
        val draft = PhoneDraft.from(TestPhones["pixel_7"]).copy(brand = "acme", product = "rocket", device = "rocket")
        assertEquals("acme/rocket/rocket:13/TQ3A.230805.001/10193186:user/release-keys", draft.effectiveFingerprint)
        assertTrue(errors(draft).none { it.code.startsWith("FP_") })
    }

    @Test
    fun aPastedFingerprintThatDisagreesIsMarkedOnTheField() {
        val draft = PhoneDraft.from(TestPhones["pixel_7"]).copy(
            autoFingerprint = false,
            fingerprint = "samsung/panther/panther:13/TQ3A.230805.001/10193186:user/release-keys",
        )
        assertEquals(listOf("device.brand"), errors(draft).map { it.field })
    }

    @Test
    fun aMissingBuildDateIsThePatchDay() {
        val draft = PhoneDraft.from(TestPhones["pixel_7"]).copy(buildDateUtc = "", securityPatch = "2024-03-05")
        assertEquals(1709596800L, draft.toTemplate().device.getLong("build_date_utc"))
        assertTrue(draft.findings().none { it.code == "PATCH_AFTER_BUILD" })
    }

    @Test
    fun textInANumberFieldIsMarkedNotDropped() {
        val draft = PhoneDraft.from(TestPhones["pixel_7"]).copy(width = "wide", sdkInt = "33")
        assertEquals(listOf("display.width"), errors(draft).map { it.field })
    }

    @Test
    fun aDumpDraftCanBeFinishedInTheBuilder() {
        val dump = javaClass.classLoader!!.getResource("dumps.json")!!.readText()
            .let { JSONObject(it).getJSONArray("cases").getJSONObject(1).getString("text") }
        val draft = PhoneDraft.from(PropImport.draftPhone(dump, emptySet()))
        assertFalse(draft.findings().none { it.isError })
        assertEquals("Vodafone.de", draft.carrierName)
        assertEquals("2", draft.simSlots)
        val finished = draft.copy(width = "1080", height = "2340", refreshRate = "120", screenClass = "large")
        assertEquals(emptyList<ProfileValidator.Finding>(), finished.findings().filter { it.isError })
        // And an identity made from it holds together as a whole profile.
        val profile = CloakForge.forgeProfile("From dump", "ef".repeat(32), finished.toTemplate())
        assertEquals(emptyList<String>(), ProfileValidator.errors(profile))
    }
}
