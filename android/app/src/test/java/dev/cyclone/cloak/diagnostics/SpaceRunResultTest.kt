package dev.cyclone.cloak.diagnostics

import dev.cyclone.cloak.data.CloakBinding
import dev.cyclone.cloak.root.RootDoctorCode
import dev.cyclone.cloak.root.RootDoctorResult
import dev.cyclone.cloak.ui.BindingRunResult
import dev.cyclone.cloak.ui.bindingRunTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceRunResultTest {
    private val space = CloakBinding("Cloak_0123456789abcdef", 10, "org.example.app", "0123456789abcdef", 0, true, 1, "ready")
    private val spaceRun = BindingRun(operation = RunOperation.BIND, targets = listOf(bindingRunTarget(space)))
    private val cycloneBinding = space.copy(profileId = "Cyclone_0123456789abcdef", androidUserId = 11)
    private val cycloneRun = BindingRun(operation = RunOperation.BIND, targets = listOf(bindingRunTarget(cycloneBinding)))

    @Test
    fun aSpaceRunNeedsNoConnectorToBeReady() {
        val observation = BindingRunResult().apply { root = RootDoctorResult(RootDoctorCode.READY) }
        val result = observation.complete(spaceRun, listOf(space), 5, RunEventCapture())
        assertEquals(RunOutcome.READY, result.outcome)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun aCycloneRunStillNeedsTheConnector() {
        val observation = BindingRunResult().apply { root = RootDoctorResult(RootDoctorCode.READY) }
        val result = observation.complete(cycloneRun, listOf(cycloneBinding), 5, RunEventCapture())
        assertTrue("CONNECTOR_NOT_READY" in result.reasons)
        assertNotEquals(RunOutcome.READY, result.outcome)
    }
}
