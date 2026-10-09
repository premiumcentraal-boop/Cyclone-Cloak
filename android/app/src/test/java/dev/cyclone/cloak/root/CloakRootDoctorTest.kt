package dev.cyclone.cloak.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
        assertTrue(CloakRootDoctor.parseResult("CLOAK_ROOT=OK\ncp: permission denied", 1).message.contains("permission denied"))
    }

    @Test
    fun repairOnlyReportsRebootAfterMagiskSchedulesTheModule() {
        val result = CloakRootDoctor.parseInstallResult(
            "Magisk module install scheduled\nCLOAK_DOCTOR=MODULE_REBOOT_REQUIRED",
            0,
            "0.5.0-alpha.4",
        )

        assertEquals(RootDoctorCode.MODULE_REBOOT_REQUIRED, result.code)
        assertEquals("v0.5.0-alpha.4", result.moduleVersion)
        assertFalse(CloakRootDoctor.parseInstallResult("CLOAK_DOCTOR=MODULE_INSTALL_FAILED", 1, "0.5.0-alpha.4").published)
    }

    @Test
    fun zygiskMustBeEnabledBeforeDoctorReportsReady() {
        assertEquals(
            RootDoctorCode.ZYGISK_DISABLED,
            CloakRootDoctor.parseResult("CLOAK_ROOT=OK\nCLOAK_DOCTOR=ZYGISK_DISABLED", 56).code,
        )
        assertEquals(
            RootDoctorCode.ZYGISK_STATUS_UNKNOWN,
            CloakRootDoctor.parseResult("CLOAK_ROOT=OK\nCLOAK_DOCTOR=ZYGISK_STATUS_UNKNOWN", 57).code,
        )
    }

    @Test
    fun matchingModuleArchiveMustContainExpectedIdentityVersionAndAbi() {
        val valid = moduleArchive()
        val wrongVersion = moduleArchive(moduleProp = "id=cyclone_cloak\nversion=v0.5.0-alpha.3\n")
        val wrongId = moduleArchive(moduleProp = "id=another_module\nversion=v0.5.0-alpha.4\n")
        val missingAbi = moduleArchive(abi = "armeabi-v7a")
        val unsafePath = moduleArchive(extraEntries = mapOf("../unexpected" to byteArrayOf(1)))
        try {
            assertTrue(CloakRootDoctor.validateModuleArchive(valid, "0.5.0-alpha.4", "arm64-v8a"))
            assertFalse(CloakRootDoctor.validateModuleArchive(wrongVersion, "0.5.0-alpha.4", "arm64-v8a"))
            assertFalse(CloakRootDoctor.validateModuleArchive(wrongId, "0.5.0-alpha.4", "arm64-v8a"))
            assertFalse(CloakRootDoctor.validateModuleArchive(missingAbi, "0.5.0-alpha.4", "arm64-v8a"))
            assertFalse(CloakRootDoctor.validateModuleArchive(unsafePath, "0.5.0-alpha.4", "arm64-v8a"))
        } finally {
            valid.delete()
            wrongVersion.delete()
            wrongId.delete()
            missingAbi.delete()
            unsafePath.delete()
        }
    }

    @Test
    fun installerScriptUsesMagiskCliAndQuotesBundledModulePath() {
        val script = CloakRootDoctor.installModuleScript(File("/data/user/0/dev.cyclone.cloak/cache/it's.zip"), "0.5.0-alpha.4")

        assertTrue(script.contains("--install-module"))
        assertTrue(script.contains("modules_update/cyclone_cloak"))
        assertTrue(script.contains("'\\''"))
        assertTrue(script.contains("'v0.5.0-alpha.4'"))
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
        assertTrue(script.contains("pending_version"))
        assertTrue(script.contains("modules_update/cyclone_cloak"))
        assertTrue(script.contains("SELECT value FROM settings WHERE key='zygisk'"))
        assertTrue(script.contains("'v0.5.0-alpha.3'"))
    }

    private fun moduleArchive(
        moduleProp: String = "id=cyclone_cloak\nversion=v0.5.0-alpha.4\n",
        abi: String = "arm64-v8a",
        extraEntries: Map<String, ByteArray> = emptyMap(),
    ): File {
        val archive = File.createTempFile("cyclone-cloak-test", ".zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("module.prop"))
            zip.write(moduleProp.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("zygisk/$abi.so"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
            extraEntries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return archive
    }
}
