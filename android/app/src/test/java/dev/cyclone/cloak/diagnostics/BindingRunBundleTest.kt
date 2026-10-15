package dev.cyclone.cloak.diagnostics

import dev.cyclone.cloak.data.CloakBinding
import dev.cyclone.cloak.root.*
import dev.cyclone.cloak.ui.BindingRunBundle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.zip.ZipInputStream

class BindingRunBundleTest {
    private val target = RunTarget("Cyclone_0123456789abcdef", 16, listOf("org.example.app"), "0123456789abcdef")
    private fun event(time: Long, subsystem: String = "publication") = DiagnosticEvent(time, Severity.INFO, "app", subsystem,
        "merge", "1", "2", null, Transition.COMPLETED, null, null, 3)
    private val run = BindingRun(timestamp = 100, operation = RunOperation.BIND, targets = listOf(target), durationMs = 50,
        events = listOf(event(110).json()))
    private fun bundle(runs: List<BindingRun> = listOf(run), recent: List<DiagnosticEvent> = emptyList(), bindings: List<CloakBinding> = emptyList()) =
        BindingRunBundle.build(runs, recent, bindings, RootDoctorResult(RootDoctorCode.READY, "0.11.0-alpha.11",
            diagnostic = "SECRET ROOT OUTPUT", publicationReason = PublicationReason.COMPLETED), null, "0.11.0-alpha.11", 100, 150, 300)

    @Test fun zipContainsAllSevenStructuredFilesAndVersions() {
        val files = bundle()
        val names = mutableListOf<String>()
        ZipInputStream(BindingRunBundle.zip(files).inputStream()).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; names += entry.name
                JSONObject(zip.readBytes().toString(Charsets.UTF_8)) }
        }
        assertEquals(setOf("manifest.json", "runs.json", "diagnostics.json", "bindings-index.json", "module-state.json", "connector-handshake.json", "reconciliation.json"), names.toSet())
        assertEquals("0.11.0-alpha.11", files.getValue("manifest.json").getString("app_version"))
        assertTrue(files.getValue("manifest.json").getBoolean("redacted"))
    }
    @Test fun windowIncludesBoundariesAndExcludesOutsideEvents() {
        val events = bundle(recent = listOf(99, 100, 150, 151).map { event(it.toLong()) }).getValue("diagnostics.json").getJSONArray("events")
        assertEquals(listOf(100L, 110L, 150L), (0 until events.length()).map { events.getJSONObject(it).getLong("timestamp") })
    }
    @Test fun oldRunEvidenceIsRetainedAfterGenericRingHasMovedOn() {
        val events = bundle(recent = listOf(event(1000))).getValue("diagnostics.json").getJSONArray("events")
        assertEquals(1, events.length()); assertEquals(110L, events.getJSONObject(0).getLong("timestamp"))
    }
    @Test fun last24HoursUsesOverlapAndExcludesFutureAndExpiredRuns() {
        val selected = BindingRunBundle.window(listOf(run.copy(timestamp = 0, durationMs = 1), run,
            run.copy(timestamp = 149), run.copy(timestamp = 151)), 100, 150)
        assertEquals(listOf(100L, 149L), selected.map { it.timestamp })
    }
    @Test fun redactionOmitsBodiesSummariesSecretsAndFreeText() {
        val poisoned = event(120).json().put("profile", JSONObject().put("seed", "SECRET SEED"))
            .put("message", "SECRET CLIPBOARD").put("token", "SECRET TOKEN").put("headers", "SECRET AUTH")
        val binding = CloakBinding(target.profileId, 16, "org.example.app", target.deviceProfileId, 0, true, 1,
            state = "SECRET STATE", mirroredSummary = "{\"androidId\":\"SECRET ANDROID ID\",\"password\":\"SECRET PASSWORD\"}")
        val files = bundle(runs = listOf(run.copy(events = listOf(poisoned), appVersion = "SECRET APP",
            moduleVersion = "SECRET MODULE", moduleCode = "SECRET CODE")), bindings = listOf(binding))
        val text = files.values.joinToString()
        assertFalse(text.contains("SECRET")); assertFalse(text.contains("mirroredSummary")); assertFalse(text.contains("androidId"))
        assertTrue(text.contains("org.example.app")); assertTrue(text.contains(target.profileId))
    }
    @Test fun arbitraryImportedIdentityReferencesAreHashedStably() {
        val text = "private user content"
        val redacted = run.copy(targets = listOf(target.copy(deviceProfileId = text))).json()
        assertFalse(redacted.toString().contains(text))
        assertEquals(redacted.toString(), BindingRun.parse(redacted).json().toString())
    }
    @Test fun moduleStateHashHasExplicitObservedScopeAndNoFabricatedBinaryHash() {
        val state = bundle().getValue("module-state.json")
        assertEquals(RunPrivacy.sha256(state.getJSONObject("state").toString()), state.getString("sha256"))
        assertTrue(state.isNull("installed_binary_hash"))
        assertEquals("last_observed_root_doctor_state", state.getString("scope"))
        assertFalse(bundle().getValue("connector-handshake.json").getBoolean("observed"))
    }
    @Test fun phaseMarkersAndCaptureGapsAreIncluded() {
        val j = run.copy(eventsDropped = 7).json()
        assertEquals("merge", j.getJSONArray("publication_phases").getJSONObject(0).getString("phase"))
        assertEquals(7, bundle(runs = listOf(run.copy(eventsDropped = 7))).getValue("diagnostics.json").getInt("retained_run_events_dropped"))
    }
    @Test fun reconciliationSummaryAndFirstFailureAreHistoricalAndRedacted() {
        val failed = run.copy(reasons = listOf("CYCLONE_NOT_ANSWERING", "CONNECTOR_NOT_READY"),
            reconciliation = RunReconciliation(1, 2, 0, 1, "CYCLONE_NOT_ANSWERING"))
        val row = bundle(runs = listOf(failed)).getValue("reconciliation.json").getJSONArray("run_observations").getJSONObject(0)
        assertEquals(failed.id, row.getString("run_id"))
        assertEquals("CYCLONE_NOT_ANSWERING", row.getString("first_failure_reason"))
        assertEquals(1, row.getJSONObject("summary").getInt("written"))
        assertEquals("CYCLONE_NOT_ANSWERING", row.getJSONObject("summary").getString("stopped_by"))
    }
    @Test fun activeRunRemainsExportableBeyondItsLastCheckpoint() {
        val active = run.copy(timestamp = 1, durationMs = 5, active = true)
        val files = bundle(runs = listOf(active))
        assertEquals(1, files.getValue("manifest.json").getInt("run_count"))
        val row = files.getValue("runs.json").getJSONArray("runs").getJSONObject(0)
        assertTrue(row.getBoolean("active")); assertTrue(row.isNull("end_timestamp"))
    }
}
