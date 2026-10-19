package dev.cyclone.cloak.forge

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlaySurfaceContractTest {
    private fun vectors() = JSONObject(javaClass.classLoader!!.getResource("play-surface.json")!!.readText())
    private fun surface() = vectors().getJSONObject("surface")
    private fun record() = vectors().getJSONObject("readiness")
    private fun expectedIssues(a: JSONArray) = (0 until a.length()).map { i ->
        val item = a.getJSONObject(i)
        PlaySurfaceContract.Issue(item.getString("field"), item.getString("code"))
    }
    @Test fun sharedSurfaceVectors() {
        val cases = vectors().getJSONArray("surface_cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            assertEquals(c.getString("name"), expectedIssues(c.getJSONArray("issues")), PlaySurfaceContract.validateSurface(c.opt("value")))
        }
    }
    @Test fun sharedReadinessVectors() {
        val cases = vectors().getJSONArray("readiness_cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            assertEquals(c.getString("name"), expectedIssues(c.getJSONArray("issues")), PlaySurfaceContract.validateReadiness(c.opt("value")))
        }
    }
    @Test fun sharedServiceVectors() {
        val cases = vectors().getJSONArray("service_cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            assertEquals(c.getString("name"), expectedIssues(c.getJSONArray("issues")), PlaySurfaceContract.serviceConflicts(surface(), c.optJSONObject("observed")))
        }
    }
    @Test fun allModesAndRoundtrip() {
        for (mode in PlaySurfaceContract.accountModes) {
            val s = surface(); val a = s.getJSONObject("account").put("mode", mode)
            if (mode == "none") a.put("slot_id", JSONObject.NULL).put("shared", false)
            assertTrue(s.similar(PlaySurfaceContract.parseSurface(PlaySurfaceContract.encodeSurface(s))))
        }
        val r = record()
        assertTrue(r.similar(PlaySurfaceContract.parseReadiness(PlaySurfaceContract.encodeReadiness(r))))
        assertEquals(listOf("READY", "DEGRADED", "BLOCKED", "UNKNOWN"), PlaySurfaceContract.readinessStates)
    }
    @Test fun constantsAndMessagesMatchSharedSource() {
        val v = vectors()
        fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }
        assertEquals(strings(v.getJSONArray("account_modes")), PlaySurfaceContract.accountModes)
        assertEquals(strings(v.getJSONArray("readiness_states")), PlaySurfaceContract.readinessStates)
        assertEquals(strings(v.getJSONArray("reason_codes")), PlaySurfaceContract.reasonCodes)
        val messages = v.getJSONObject("messages")
        messages.keys().asSequence().forEach { assertEquals(messages.getString(it), PlaySurfaceContract.Issue("contract", it).message) }
    }
    @Test fun strictParsingAndRedactedErrors() {
        val invalid = listOf("{\"format\":1,\"format\":1}", "{\"password\":\"DO_NOT_ECHO_SECRET\"}",
            "{\"format\":NaN}", "{\"format\":Infinity}", "{} trailing", "[]", " ".repeat(131073),
            "{\"nested\":".repeat(40) + "0" + "}".repeat(40))
        for (text in invalid) for (parser in listOf(PlaySurfaceContract::parseSurface, PlaySurfaceContract::parseReadiness)) {
            val error = assertThrows(PlaySurfaceContract.ContractException::class.java) { parser(text) }
            assertFalse(error.message.orEmpty().contains("DO_NOT_ECHO_SECRET"))
            assertFalse(error.message.orEmpty().contains("password"))
        }
    }
    private fun assignment(id: String, active: Boolean = true, shared: Boolean = false): PlaySurfaceContract.Assignment {
        val s = surface(); s.getJSONObject("account").put("shared", shared)
        return PlaySurfaceContract.Assignment(id, active, JSONObject().put("play_surface", s))
    }
    @Test fun accountSlotConsentAndInactiveProfiles() {
        val values = listOf(assignment("one"), assignment("two"))
        val before = values.map { it.profile.toString() }
        assertEquals(2, PlaySurfaceContract.accountSlotConflicts(values).size)
        assertEquals(before, values.map { it.profile.toString() })
        assertTrue(PlaySurfaceContract.accountSlotConflicts(listOf(assignment("one"), assignment("two", active = false))).isEmpty())
        assertEquals(2, PlaySurfaceContract.accountSlotConflicts(listOf(assignment("one", shared = true), assignment("two"))).size)
        assertTrue(PlaySurfaceContract.accountSlotConflicts(listOf(assignment("one", shared = true), assignment("two", shared = true))).isEmpty())
        assertThrows(PlaySurfaceContract.ContractException::class.java) { PlaySurfaceContract.accountSlotConflicts(listOf(assignment("one"), assignment("one"))) }
    }
    @Test fun evidenceBindingDowngradesEveryMismatchedBinding() {
        val r = record(); val before = r.toString()
        fun bind(id: String = "profile-a", hash: String = "b".repeat(64), artifact: String? = "c".repeat(64), evidence: String? = "a".repeat(64)) =
            PlaySurfaceContract.bindReadiness(r, id, hash, artifact, evidence)
        assertEquals("READY", bind().getString("state"))
        for ((result, code) in listOf(
            bind(id = "profile-b") to "PS_PROFILE_MISMATCH", bind(hash = "d".repeat(64)) to "PS_PROFILE_MISMATCH",
            bind(artifact = "d".repeat(64)) to "PS_ARTIFACT_MISMATCH", bind(evidence = "d".repeat(64)) to "PS_EVIDENCE_MISMATCH",
            bind(evidence = null) to "PS_NO_EVIDENCE")) {
            assertEquals("UNKNOWN", result.getString("state"))
            assertTrue(result.getJSONArray("reason_codes").toString().contains(code))
            assertEquals(r.getString("profile_hash"), result.getString("profile_hash"))
        }
        assertEquals(before, r.toString())
        r.put("state", "UNKNOWN").put("reason_codes", JSONArray(listOf("PS_REVIEW_REQUIRED")))
        assertEquals("UNKNOWN", bind().getString("state"))
    }
    @Test fun partialObservationsPreserveKnownConflictsAndMissingFacts() {
        val s = surface(); val observed = JSONObject(s.getJSONObject("services").toString())
        observed.getJSONObject("play_services").put("state", "unknown").put("version_name", "different")
        assertTrue(PlaySurfaceContract.serviceConflicts(s, observed).any { it.field.endsWith("play_services") && it.code == "PS_PACKAGE_CONFLICT" })
        observed.getJSONObject("play_services").remove("version_name")
        assertTrue(PlaySurfaceContract.serviceConflicts(s, observed).any { it.field.endsWith("play_services") && it.code == "PS_EXPECTATION_UNKNOWN" })
    }
    @Test fun existingProfilesRemainUnchangedAndSectionValidationIsIntegrated() {
        for (version in listOf("0.2", "0.3", "0.4")) {
            val profile = JSONObject(javaClass.classLoader!!.getResource("reality.json")!!.readText()).getJSONObject("profile")
            profile.remove("reality"); profile.put("schema_version", version)
            val before = profile.toString(); val prior = ProfileValidator.validateProfile(profile)
            assertTrue(PlaySurfaceContract.validateProfileSection(profile).isEmpty())
            profile.put("play_surface", surface())
            assertEquals(prior, ProfileValidator.validateProfile(profile))
            profile.getJSONObject("play_surface").getJSONObject("account").put("mode", "future")
            assertTrue(ProfileValidator.validateProfile(profile).any { it.code == "PS_VALUE" })
            profile.remove("play_surface")
            assertEquals(before, profile.toString())
        }
    }
}
