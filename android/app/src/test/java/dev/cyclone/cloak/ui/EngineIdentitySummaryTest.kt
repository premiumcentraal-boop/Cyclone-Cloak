package dev.cyclone.cloak.ui

import dev.cyclone.cloak.forge.DefaultDeviceProfileEngine
import dev.cyclone.cloak.forge.IdentityEngine
import dev.cyclone.cloak.forge.TestPhones
import org.junit.Assert.*
import org.junit.Test

class EngineIdentitySummaryTest {
    private fun bundle() = DefaultDeviceProfileEngine().generate_unique(IdentityEngine.GenerationRequest("Display", "ab".repeat(32), TestPhones["pixel_7"]), emptyList())
    @Test fun generatedLabelsShowAllFourDescriptiveFields() {
        val raw = bundle()
        val text = EngineIdentitySummary.labels(raw.getJSONObject("profile"), raw.getJSONObject("metadata")).joinToString()
        for (label in listOf("Engine version: 1", "Profile schema version: 0.3", "Template ID: pixel_7", "Source confidence: needs verification")) assertTrue(text.contains(label))
    }
    @Test fun legacyHasExplicitMissingMetadataWithoutInventedEngineClaims() {
        val labels = EngineIdentitySummary.labels(bundle().getJSONObject("profile"), null).joinToString()
        assertTrue(labels.contains("Engine version: not recorded"))
        assertTrue(labels.contains("Source confidence: not recorded"))
        assertTrue(labels.contains("Template ID: pixel_7"))
    }
    @Test fun summaryNeverDisplaysSeedOrDistinguishingValues() {
        val raw = bundle(); val profile = raw.getJSONObject("profile")
        val labels = EngineIdentitySummary.labels(profile, raw.getJSONObject("metadata")).joinToString()
        assertFalse(labels.contains(profile.getString("seed")))
        profile.getJSONObject("identifiers").keys().asSequence().forEach { assertFalse(labels.contains(profile.getJSONObject("identifiers").getString(it))) }
    }
}
