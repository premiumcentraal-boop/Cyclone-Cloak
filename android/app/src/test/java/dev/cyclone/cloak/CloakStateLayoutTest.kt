package dev.cyclone.cloak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloakStateLayoutTest {
    @Test
    fun keyMatchesReferenceVector() {
        assertEquals(
            "d531e13ba21eb8f7c08a1afc89d49f148ac076b065ce3f9bd8aebbf42fb0b122",
            CloakStateLayout.key("Cyclone_0123456789abcdef", 7, "com.example.app"),
        )
    }

    @Test
    fun layoutConstantsAreStable() {
        assertEquals("cyclone-profile-state-v1", CloakStateLayout.ROOT_DIR)
        assertEquals("profile.json", CloakStateLayout.PROFILE_FILE)
        assertEquals("index.json", CloakStateLayout.INDEX_FILE)
        assertEquals("/data/adb/cyclone_cloak/state-v1", CloakStateLayout.MODULE_STATE_DIR)
        assertEquals("state-staging", CloakStateLayout.STAGING_DIR)
    }

    @Test
    fun validationRejectsBadInput() {
        val thrown = runCatching { CloakStateLayout.validateInputs("nope", 0, "com.example.app") }
        assertTrue(thrown.isFailure)
    }
}