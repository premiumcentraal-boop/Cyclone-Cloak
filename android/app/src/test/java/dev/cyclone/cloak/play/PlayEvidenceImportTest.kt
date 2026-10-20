package dev.cyclone.cloak.play

import android.content.Context
import dev.cyclone.cloak.data.CloakStore
import dev.cyclone.cloak.data.EngineMetadataStore
import dev.cyclone.cloak.data.CloakBindingStore
import dev.cyclone.cloak.data.CloakBinding
import dev.cyclone.cloak.playevidence.EvidenceReport
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayEvidenceImportTest {
    private lateinit var app: Context
    private var now = Instant.parse("2026-10-19T12:00:00Z")
    private var artifact = "b".repeat(64)
    private lateinit var repo: PlayEvidenceRepository
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        for (name in listOf("google-surface-isolation", "play-readiness-evaluation")) app.getSharedPreferences(name, 0).edit().clear().commit()
        File(app.filesDir, "profiles").deleteRecursively()
        now = Instant.parse("2026-10-19T12:00:00Z"); artifact = "b".repeat(64)
        repo = PlayEvidenceRepository(app, { now }, { artifact })
        CloakStore.save(app, JSONObject().put("id", "device-a").put("schema_version", "0.3").put("name", "Local device"))
    }
    private fun target() = repo.target("synthetic-profile", 17, "device-a")
    private fun envelope(t: PlayImportTarget = target()): String {
        val j = JSONObject(javaClass.classLoader!!.getResource("play_evidence_report.json")!!.readText())
        val hash = t.context(now).profileHash
        j.put("profile_hash", hash).put("profile_id", t.profileId).put("artifact_hash", t.artifactHash)
        j.getJSONObject("readiness_report").put("profile_hash", hash).put("profile_id", t.profileId).put("artifact_hash", t.artifactHash)
        val rows = j.getJSONArray("rows")
        for (i in 0 until rows.length()) if (rows.getJSONObject(i).getString("id") == "profile.binding")
            rows.getJSONObject(i).getJSONObject("metadata").put("actual_profile_hash", hash)
        return j.toString()
    }
    private fun rejected(block: () -> Unit) = assertThrows(PlayEvidenceImport.Rejected::class.java, block)
    @Test fun validEnvelopeSavesExistingReadinessFormatWithoutPromotion() {
        val review = repo.preview(envelope(), target())
        assertTrue(review.result.accepted)
        assertEquals(PlayReadinessState.UNKNOWN, repo.save(review, target(), false).state)
        val store = PlayReadinessStore(app)
        assertNotNull(store.evidence("synthetic-profile"))
        assertEquals(PlayReadinessState.UNKNOWN, store.read(target().context(now)).state)
    }
    @Test fun savedProfileAndBindingsRemainByteIdentical() {
        val profile = File(app.filesDir, "profiles/device-a.json")
        val before = profile.readBytes()
        val binding = CloakBinding("Cyclone_aaaaaaaaaaaaaaaa", 17, "com.example.reader", "device-a", 1, true, 1)
        CloakBindingStore.upsert(app, binding)
        val bindings = File(app.noBackupFilesDir, "cloak-bindings-v1.json")
        val bindingBytes = bindings.readBytes()
        val review = repo.preview(envelope(), target()); repo.save(review, target(), false)
        assertArrayEquals(before, profile.readBytes())
        assertArrayEquals(bindingBytes, bindings.readBytes())
        assertEquals(listOf(binding), CloakBindingStore.all(app))
        assertEquals(setOf("device-a"), CloakStore.all(app).map { it.first }.toSet())
    }
    @Test fun exactFileHashUsesWhitespaceAndDoesNotCanonicalizeReportBinding() {
        val t = target()
        assertEquals(EvidenceReport.digest(File(app.filesDir, "profiles/device-a.json").readBytes()), t.context(now).profileHash)
        assertNotEquals(EngineMetadataStore.digest(t.profile), t.context(now).profileHash)
    }
    @Test fun foreignProfileUserAndArtifactRejected() {
        val t = target(); val report = envelope(t)
        rejected { repo.preview(report, t.copy(profileId = "another")) }
        rejected { repo.preview(report, t.copy(androidUserId = 0)) }
        rejected { repo.preview(report, t.copy(artifactHash = "c".repeat(64))) }
        rejected { repo.preview(report, t.copy(profileText = t.profile.put("name", "changed").toString())) }
    }
    @Test fun staleFutureAndMalformedRejectedWithoutReplacingSavedEvidence() {
        repo.save(repo.preview(envelope(), target()), target(), false)
        val store = PlayReadinessStore(app); val before = store.evidence("synthetic-profile")
        now = now.plusSeconds(86401)
        rejected { repo.preview(envelope(), target()) }
        now = Instant.parse("2026-10-19T11:00:00Z")
        rejected { repo.preview(envelope(), target()) }
        rejected { repo.preview("{\"token\":\"private\"}", target()) }
        assertEquals(before, store.evidence("synthetic-profile"))
    }
    @Test fun projectionCannotBePromotedOrRowsOmitted() {
        val j = JSONObject(envelope())
        j.getJSONObject("readiness_report").put("play_protect", "enabled")
        rejected { repo.preview(j.toString(), target()) }
        val incomplete = JSONObject(envelope()); incomplete.getJSONArray("rows").remove(0)
        rejected { repo.preview(incomplete.toString(), target()) }
        rejected { repo.preview(incomplete.getJSONObject("readiness_report").toString(), target()) }
    }
    @Test fun boundCaptureMustConfirmItsOwnUserAndProfile() {
        val j = JSONObject(envelope()); val rows = j.getJSONArray("rows")
        for (i in 0 until rows.length()) if (rows.getJSONObject(i).getString("id") == "process.android_user")
            rows.getJSONObject(i).put("status", "FAIL").put("reason", "USER_MISMATCH")
        j.getJSONObject("readiness_report").getJSONArray("failures").put("LOCAL_CHECK_FAILED")
        rejected { repo.preview(j.toString(), target()) }
    }
    @Test fun oldProfilesWithoutPlaySurfaceStayReadableAndUnmodified() {
        for (version in listOf("0.2", "0.3", "0.4")) {
            val body = target().profile.put("schema_version", version)
            val t = target().copy(profileText = body.toString())
            assertNull(PlayEvidenceImport.assignment(t))
            assertTrue(repo.preview(envelope(t), t).result.accepted)
            assertFalse(body.has("play_surface"))
        }
    }
    @Test fun missingIdentityAndAndroidUserDoNotDefaultToOwner() {
        rejected { repo.target("synthetic-profile", null, "device-a") }
        rejected { repo.target("synthetic-profile", 17, null) }
        assertFalse(repo.card("synthetic-profile", null, "device-a").contextAvailable)
    }
    @Test fun reportRevalidatedAfterProfileOrArtifactChanges() {
        val review = repo.preview(envelope(), target())
        CloakStore.save(app, target().profile.put("name", "Updated"))
        rejected { repo.save(review, target(), false) }
        assertNull(PlayReadinessStore(app).evidence("synthetic-profile"))
    }
    @Test fun staleSavedCardShowsNeedsReviewAndReasons() {
        repo.save(repo.preview(envelope(), target()), target(), false)
        now = now.plusSeconds(86401)
        val card = repo.card("synthetic-profile", 17, "device-a")
        assertEquals("NEEDS REVIEW", card.verdict)
        assertTrue(card.reasons.any { it.contains("PR_STALE") })
        assertNotNull(card.timestamp)
    }
    @Test fun accountSlotRequiresExplicitCurrentSharingApproval() {
        val store = GoogleSurfaceStore(app)
        store.save(SurfaceFixtures.record("peer", 0, "shared-slot"))
        val body = target().profile.put("play_surface", SurfaceFixtures.record(slot = "shared-slot").surface)
        CloakStore.save(app, body)
        val review = repo.preview(envelope(), target())
        assertEquals(listOf("peer", "synthetic-profile"), review.sharedProfiles)
        assertFalse(review.sharingConfirmed)
        assertThrows(GoogleSurfaceStore.ReviewRequired::class.java) { repo.save(review, target(), false) }
        assertNull(PlayReadinessStore(app).evidence("synthetic-profile"))
        repo.save(review, target(), true)
        val card = repo.card("synthetic-profile", 17, "device-a")
        assertTrue(card.sharingConfirmed)
        assertEquals("UNKNOWN", card.verdict)
    }
    @Test fun changedSharingInventoryRequiresNewReview() {
        val store = GoogleSurfaceStore(app)
        store.save(SurfaceFixtures.record("peer", 0, "shared-slot"))
        CloakStore.save(app, target().profile.put("play_surface", SurfaceFixtures.record(slot = "shared-slot").surface))
        val review = repo.preview(envelope(), target())
        store.save(SurfaceFixtures.record("other", 18, "different-slot"))
        assertThrows(PlayEvidenceRepository.ReviewChanged::class.java) { repo.save(review, target(), true) }
        assertNull(PlayReadinessStore(app).evidence("synthetic-profile"))
    }
    @Test fun existingAssignmentMismatchRemainsVisibleWhenSelectedProfileHasNoSurface() {
        GoogleSurfaceStore(app).save(SurfaceFixtures.record("synthetic-profile", 17, "slot"))
        val card = repo.card("synthetic-profile", 17, "device-a")
        assertEquals("NEEDS REVIEW", card.verdict)
        assertTrue(card.reasons.any { it.contains("Stored account assignment") })
    }
    @Test fun unreadableReadinessStoreCannotCauseAnAssignmentWrite() {
        CloakStore.save(app, target().profile.put("play_surface", SurfaceFixtures.record(slot = "slot").surface))
        val review = repo.preview(envelope(), target())
        app.getSharedPreferences("play-readiness-evaluation", 0).edit().putString("synthetic-profile", "broken").commit()
        assertThrows(PlayReadinessStore.UnreadableStore::class.java) { repo.save(review, target(), false) }
        assertNull(GoogleSurfaceStore(app).read("synthetic-profile"))
    }
    @Test fun malformedUtf8ProfileBytesCannotBeSilentlyReplaced() {
        File(app.filesDir, "profiles/device-a.json").writeBytes(byteArrayOf(0xc3.toByte(), 0x28))
        assertThrows(java.nio.charset.CharacterCodingException::class.java) { target() }
    }
    @Test fun unavailableCurrentContextDoesNotHidePreviouslyRecordedProvenance() {
        repo.save(repo.preview(envelope(), target()), target(), false)
        val card = repo.card("synthetic-profile", null, "device-a")
        assertEquals("NEEDS REVIEW", card.verdict)
        assertEquals("2026-10-19T12:00:00Z", card.timestamp)
        assertTrue(card.source.contains("current binding unavailable"))
        assertFalse(card.contextAvailable)
    }
    @Test fun presentationNeverHidesMissingEvidenceBehindOptionalIntegrity() {
        val review = repo.preview(envelope(), target())
        val display = PlayEvidenceImport.display(review.result, review.projection)
        assertEquals("UNKNOWN", display.verdict)
        assertEquals("unknown", display.details["Play Protect"])
        assertEquals("unknown", display.details["Google settings access"])
        assertTrue(display.reasons.isNotEmpty())
    }
    @Test fun artifactPairIsDeterministicAndSensitiveToBothArtifacts() {
        val hash = PlayEvidenceArtifacts.combine("a".repeat(64), "b".repeat(64))
        assertEquals(hash, PlayEvidenceArtifacts.combine("a".repeat(64), "b".repeat(64)))
        assertNotEquals(hash, PlayEvidenceArtifacts.combine("c".repeat(64), "b".repeat(64)))
        assertNotEquals(hash, PlayEvidenceArtifacts.combine("a".repeat(64), "c".repeat(64)))
    }
    @Test fun oversizedInputIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ByteArray(9).inputStream().readBounded(8) }
        assertArrayEquals(ByteArray(8), ByteArray(8).inputStream().readBounded(8))
    }
    @Test fun importedPackagesDoNotInventAccountOrBoundaryEvidence() {
        CloakStore.save(app, target().profile.put("play_surface", SurfaceFixtures.record().surface))
        val t = target().copy(appPackages = listOf("com.android.vending"))
        val review = repo.preview(envelope(t), t)
        repo.save(review, t, false)
        val saved = ServiceSurfaceStore(app).read(t.profileId, "com.android.vending").evidence!!
        assertEquals(t.artifactHash, saved.binding.artifactHash)
        assertNull(saved.observation.privateDataBoundary)
        assertNull(saved.observation.accountSlotId)
        assertNull(saved.observation.backupSync)
        assertTrue(saved.observation.packageStates.isNotEmpty())
        assertEquals(ServiceIsolationState.UNKNOWN, repo.card(t.profileId, t.androidUserId, t.identityId, t.appPackages)
            .serviceIsolation.getValue("com.android.vending").state)
        assertNotEquals("READY", repo.card(t.profileId, t.androidUserId, t.identityId, t.appPackages).verdict)
    }
    @Test fun changingAppScopeInvalidatesTheReviewStamp() {
        val t = target().copy(appPackages = listOf("com.android.vending"))
        val review = repo.preview(envelope(t), t)
        assertThrows(PlayEvidenceRepository.ReviewChanged::class.java) {
            repo.save(review, t.copy(appPackages = listOf("com.google.android.gms")), false)
        }
        assertNull(PlayReadinessStore(app).evidence(t.profileId))
    }
    @Test fun appBoundToAnotherIdentityCannotBorrowTheSelectedIdentityEvidence() {
        val card = repo.card("synthetic-profile", 17, "device-a", listOf("com.android.vending"), setOf("com.android.vending"))
        assertEquals("NEEDS REVIEW", card.verdict)
        assertEquals(listOf("SSI_BINDING_MISMATCH"), card.serviceIsolation.getValue("com.android.vending").reasons)
    }
}
