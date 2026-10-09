package dev.cyclone.cloak.forge

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloakPifTest {
    @Test
    fun mirrorsTheForgePifKeys() {
        val profile = JSONObject()
            .put(
                "device",
                JSONObject()
                    .put("manufacturer", "Google")
                    .put("model", "Pixel 7")
                    .put("brand", "google")
                    .put("product", "panther")
                    .put("device", "panther")
                    .put("fingerprint", "google/panther/panther:13/TQ3A.230805.001/10193186:user/release-keys")
                    .put("security_patch", "2023-08-05")
                    .put("first_api_level", 33)
                    .put("build_id", "TQ3A.230805.001")
                    .put("version_incremental", "10193186")
                    .put("bootloader", "panther-1.0-8769422")
                    .put("baseband", "g5123-230712-230712-B10046521")
                    .put("version_release", "13")
                    .put("sdk_int", 33),
            )
        val pif = CloakPif.fromProfile(profile)
        assertEquals("Google", pif.getString("MANUFACTURER"))
        assertEquals("Pixel 7", pif.getString("MODEL"))
        assertEquals("google", pif.getString("BRAND"))
        assertEquals("panther", pif.getString("PRODUCT"))
        assertEquals("panther", pif.getString("DEVICE"))
        assertEquals(
            "google/panther/panther:13/TQ3A.230805.001/10193186:user/release-keys",
            pif.getString("FINGERPRINT"),
        )
        assertEquals("2023-08-05", pif.getString("SECURITY_PATCH"))
        assertEquals("33", pif.getString("FIRST_API_LEVEL"))
        assertEquals("TQ3A.230805.001", pif.getString("BUILD_ID"))
        assertEquals("10193186", pif.getString("INCREMENTAL"))
        assertEquals("panther-1.0-8769422", pif.getString("BOOTLOADER"))
        assertEquals("g5123-230712-230712-B10046521", pif.getString("BASEBAND"))
        assertEquals("13", pif.getString("VERSION_RELEASE"))
        assertEquals("33", pif.getString("SDK_INT"))
val names = pif.keys().asSequence().toSet()
        assertEquals(
            setOf(
                "MANUFACTURER", "MODEL", "BRAND", "PRODUCT", "DEVICE", "FINGERPRINT",
                "SECURITY_PATCH", "FIRST_API_LEVEL", "BUILD_ID", "INCREMENTAL",
                "BOOTLOADER", "BASEBAND", "VERSION_RELEASE", "SDK_INT",
            ),
            names,
        )
    }

    @Test
    fun toleratesAProfileWithoutADeviceBlock() {
        val pif = CloakPif.fromProfile(JSONObject())
        assertTrue(pif.length() == 0)
    }
}
