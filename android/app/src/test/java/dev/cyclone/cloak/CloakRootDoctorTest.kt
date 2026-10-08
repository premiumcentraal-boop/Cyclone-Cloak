package dev.cyclone.cloak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CloakRootDoctorTest {
    @Test
    fun readyRequiresSuccessfulRootCommand() {
        val result = CloakRootDoctor.parseResult(
            "CLOAK_ROOT=OK\nCLOAK_MODULE_VERSION=v0.5.0-alpha.3\nCLOAK_DOCTOR=READY",
            0,
        )

        assertEquals(RootDoctorCode.READY, result.code)
        assertEquals("v0.5.0-alpha.3", result.moduleVersion)
        assertTrue(result.published)
    }

    @Test
    fun moduleProblemsAreReportedPrecisely() {
        assertEquals(
            RootDoctorCode.MODULE_OUTDATED,
            CloakRootDoctor.parseResult(
                "CLOAK_ROOT=OK\nCLOAK_MODULE_VERSION=v0.5.0-alpha.2\nCLOAK_DOCTOR=MODULE_OUTDATED",
                46,
            ).code,
        )
        assertEquals(
            RootDoctorCode.MODULE_DISABLED,
            CloakRootDoctor.parseResult("CLOAK_ROOT=OK\nCLOAK_DOCTOR=MODULE_DISABLED", 45).code,
        )
    }

    @Test
    fun rootDenialIsDistinctFromWriteFailure() {
        assertEquals(RootDoctorCode.ROOT_REQUIRED, CloakRootDoctor.parseResult("Permission denied", 1).code)
        assertEquals(
            RootDoctorCode.PUBLISH_FAILED,
            CloakRootDoctor.parseResult("CLOAK_ROOT=OK\ncp: permission denied", 1).code,
        )
    }

    @Test
    fun generatedRootScriptKeepsShellVariablesAndChecksMatchingModule() {
        val script = CloakRootDoctor.publishScript(
            File("/data/user/0/dev.cyclone.cloak/cache/state-staging"),
            "arm64-v8a",
            "0.5.0-alpha.3",
            emptyList(),
        )

        assertTrue(script.contains("if [ \"\$uid\" != 0 ]"))
        assertTrue(script.contains("\$module/zygisk/\$abi.so"))
        assertTrue(script.contains("\$module_version"))
        assertTrue(script.contains("'v0.5.0-alpha.3'"))
    }
}
