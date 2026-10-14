package dev.cyclone.cloak.diagnostics

import dev.cyclone.cloak.ui.DiagnosticFilter
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipInputStream

class DiagnosticsRegistryTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun schemaContainsRequiredFieldsAndTiming() {
        DiagnosticsRegistry(process = "42").use { r ->
            r.event("profiles", "save.1", Transition.COMPLETED, "dev.cyclone.cloak", "SAVED", durationMs = 4)
            assertTrue(r.flush())
            val e = r.events().single().json()
            for (key in listOf("timestamp","severity","source","subsystem","action_id","thread","process","target_id","message","error_summary","duration_ms")) assertTrue(key, e.has(key))
            assertEquals(4L, e.getLong("duration_ms")); assertEquals("42", e.getString("process"))
        }
    }
    @Test fun exceptionsDoNotLeakMessagesOrPrivatePayloads() {
        DiagnosticsRegistry().use { r ->
            r.event("profiles", "import", Transition.FAILED, "token=secret-password",
                "Authorization: Bearer secret-token", IllegalArgumentException("account message clipboard secret-key"))
            val report = r.bundle("0.11.0-alpha.8", null, 34, "arm64-v8a").toString()
            for (secret in listOf("secret-password","secret-token","secret-key","clipboard","Bearer")) assertFalse(report.contains(secret))
            assertTrue(report.contains("IllegalArgumentException")); assertTrue(report.contains("UNSPECIFIED"))
        }
    }
    @Test fun ringKeepsOnlyLatestEvents() {
        DiagnosticsRegistry(capacity = 3).use { r ->
            repeat(8) { r.event("startup", "a$it", Transition.COMPLETED); assertTrue(r.flush()) }
            assertEquals(listOf("a5","a6","a7"), r.events().map { it.actionId })
        }
    }
    @Test fun rotationHasHardCapAndRecoversLatestEvents() {
        val dir = temp.newFolder()
        DiagnosticsRegistry(dir, capacity = 8, fileCap = 2048).use { r ->
            repeat(30) { r.event("profiles", "save$it", Transition.COMPLETED); r.flush() }
            assertTrue(dir.resolve("previous.jsonl").isFile)
            assertTrue(dir.listFiles()!!.all { it.length() <= 2048 })
        }
        DiagnosticsRegistry(dir, capacity = 8, fileCap = 2048).use { r -> assertEquals("save29", r.events().last().actionId) }
    }
    @Test fun countersAndActiveActionsAreSeparateFromEviction() {
        DiagnosticsRegistry(capacity = 4).use { r ->
            r.event("profiles", "import", Transition.STARTED); r.flush()
            assertEquals(1, r.bundle("app", null, 34, "arm64").getJSONArray("current_actions").length())
            r.event("profiles", "import", Transition.FAILED, error = IllegalStateException()); r.flush()
            assertEquals(1L, r.counters()["profiles.FAILED"])
            assertEquals(0, r.bundle("app", null, 34, "arm64").getJSONArray("current_actions").length())
        }
    }
    @Test fun zipIncludesVersionsFailuresAndEnvironment() {
        DiagnosticsRegistry().use { r ->
            r.event("module", "check", Transition.FAILED, reason = "MODULE_MISSING")
            val report = r.bundle("0.11.0-alpha.8", "v0.11.0-alpha.8", 34, "arm64-v8a")
            ZipInputStream(r.zip(report).inputStream()).use { z ->
                assertEquals("diagnostics.json", z.nextEntry.name)
                val decoded = JSONObject(z.readBytes().toString(Charsets.UTF_8))
                assertTrue(decoded.getBoolean("redacted")); assertEquals(1, decoded.getJSONArray("failures").length())
                assertEquals(34, decoded.getJSONObject("environment").getInt("api"))
                assertEquals("v0.11.0-alpha.8", decoded.getString("module_version"))
                assertEquals("0.11.0-alpha.8", decoded.getString("app_version"))
                assertNull(z.nextEntry)
            }
        }
    }
    @Test fun crashPersistsRedactedLastEventsAndRecoveryIgnoresCorruption() {
        val dir = temp.newFolder()
        DiagnosticsRegistry(dir).use { r ->
            r.event("startup", "start", Transition.COMPLETED); r.flush()
            r.crash(IllegalStateException("private message secret"))
            assertFalse(dir.resolve("crash.jsonl").readText().contains("private message"))
        }
        dir.resolve("recent.jsonl").appendText("invalid json\n")
        DiagnosticsRegistry(dir).use { r -> assertTrue(r.events().any { it.transition == Transition.CRASH }) }
    }
    @Test fun unavailablePersistenceCannotBreakProducers() {
        val file = temp.newFile()
        DiagnosticsRegistry(file).use { r ->
            r.event("startup", "start", Transition.COMPLETED); assertTrue(r.flush())
            assertEquals(1, r.events().size); assertTrue(r.persistenceFailures.get() > 0)
        }
    }
    @Test fun saturatedQueueDropsInsteadOfBlocking() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        DiagnosticsRegistry(capacity = 1, sink = { entered.countDown(); release.await(2, java.util.concurrent.TimeUnit.SECONDS) }).use { r ->
            r.event("startup", "first", Transition.COMPLETED)
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))
            repeat(20) { r.event("startup", "later", Transition.COMPLETED) }
            assertTrue(r.dropped.get() > 0); release.countDown(); assertTrue(r.flush())
        }
    }
    @Test fun filtersMatchAllFourControls() {
        DiagnosticsRegistry().use { r ->
            r.event("module", "check", Transition.DEGRADED, reason = "MODULE_MISSING"); r.flush()
            val e = r.events().single()
            assertTrue(DiagnosticFilter("MODULE","warning","check","missing").accepts(e))
            assertFalse(DiagnosticFilter(severity = "ERROR").accepts(e))
        }
    }
    @Test fun traceReturnsOriginalResultAndRethrowsOriginalError() {
        assertEquals(42, LogBus.trace("test", "ok") { 42 })
        val error = IllegalStateException("private")
        try { LogBus.trace("test", "bad") { throw error }; fail() } catch (actual: IllegalStateException) { assertSame(error, actual) }
    }
}
