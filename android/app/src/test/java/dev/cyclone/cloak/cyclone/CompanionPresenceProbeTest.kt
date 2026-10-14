package dev.cyclone.cloak.cyclone

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompanionPresenceProbeTest {
    @Test fun scriptIsReadOnlyAndUsesExactUser() {
        val script = CompanionPresenceProbe.script(16)
        assertTrue(script.contains("--user 16"))
        assertTrue(script.contains("/data/user/16/dev.cyclone.cloak/files"))
        for (forbidden in listOf("install-existing", "start-user", "mkdir", "touch", "rm ", "test -w", "pm enable")) assertFalse(script.contains(forbidden))
        assertThrows(IllegalArgumentException::class.java) { CompanionPresenceProbe.script(-1) }
    }
    @Test fun localStorageCheckUsesCurrentAppDirectory() {
        val context = RuntimeEnvironment.getApplication()
        context.filesDir
        val result = CompanionPresenceProbe.local(context)
        assertEquals(PresenceCheck.YES, result.installed)
        assertEquals(PresenceCheck.YES, result.storageExists)
        assertEquals(PresenceCheck.YES, result.storageWritable)
    }
}
