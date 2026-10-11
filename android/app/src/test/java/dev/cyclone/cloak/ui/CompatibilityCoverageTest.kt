package dev.cyclone.cloak.ui

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityCoverageTest {
    private fun source() = javaClass.classLoader!!.getResource("compatibility-coverage.json")!!.readText()

    @Test
    fun bundledMatrixHasEveryAreaAndDoesNotClaimWholeAreaVerification() {
        val coverage = CompatibilityCoverage.parse(source())
        assertEquals(13, coverage.rows.size)
        assertEquals(CompatibilityCoverage.REQUIRED_AREAS, coverage.rows.map { it.id }.toSet())
        assertTrue(coverage.scopeNote.contains("not a live device check"))
        assertEquals(13, coverage.rows.sumOf { it.verifiedReads.size })
        coverage.rows.forEach { row ->
            if (row.verifiedReads.isEmpty()) assertNull(row.lastVerifiedUtc)
            else {
                assertNotNull(row.lastVerifiedUtc)
                assertEquals(CoverageTestStatus.PARTIAL_DEVICE_PROOF, row.testStatus)
                assertEquals(CoverageImplementation.PARTIAL, row.implementation)
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun aVerificationDateWithoutNamedProofIsRejected() {
        val json = JSONObject(source())
        json.getJSONArray("rows").getJSONObject(1).put("last_verified_utc", "2026-10-10")
        CompatibilityCoverage.parse(json.toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingAreaIsRejected() {
        val json = JSONObject(source())
        json.getJSONArray("rows").remove(0)
        CompatibilityCoverage.parse(json.toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateAreaIsRejected() {
        val json = JSONObject(source())
        json.getJSONArray("rows").getJSONObject(1).put("id", "build")
        CompatibilityCoverage.parse(json.toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun proofStatusWithoutNamedProofIsRejected() {
        val json = JSONObject(source())
        json.getJSONArray("rows").getJSONObject(1).put("test_status", "partial_device_proof")
        CompatibilityCoverage.parse(json.toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun callbackStatusRequiresATestReference() {
        val json = JSONObject(source())
        json.getJSONArray("rows").getJSONObject(2).getJSONArray("unit_evidence").remove(0)
        CompatibilityCoverage.parse(json.toString())
    }
}
