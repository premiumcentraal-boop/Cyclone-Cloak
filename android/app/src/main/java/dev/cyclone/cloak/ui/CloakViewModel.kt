package dev.cyclone.cloak.ui

import dev.cyclone.cloak.diagnostics.LogBus
import dev.cyclone.cloak.diagnostics.Transition
import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.cyclone.cloak.cyclone.CompanionPresence
import dev.cyclone.cloak.cyclone.CompanionPresenceLookup
import dev.cyclone.cloak.cyclone.CompanionPresenceProbe
import dev.cyclone.cloak.cyclone.CompanionFacts
import dev.cyclone.cloak.data.InstallPlacement
import dev.cyclone.cloak.cyclone.BulkBind
import dev.cyclone.cloak.cyclone.CloakPill
import dev.cyclone.cloak.cyclone.CloakPills
import dev.cyclone.cloak.cyclone.CloakStartupProvider
import dev.cyclone.cloak.cyclone.CycloneBridge
import dev.cyclone.cloak.cyclone.CycloneProfile
import dev.cyclone.cloak.cyclone.OpenRequests
import dev.cyclone.cloak.cyclone.Placement
import dev.cyclone.cloak.cyclone.SyncReport
import dev.cyclone.cloak.data.CloakBinding
import dev.cyclone.cloak.data.CloakBindingStore
import dev.cyclone.cloak.data.CloakStore
import dev.cyclone.cloak.data.EngineMetadataStore
import dev.cyclone.cloak.data.PostureRecord
import dev.cyclone.cloak.data.PostureStore
import dev.cyclone.cloak.data.CompatibilityPostureReport
import dev.cyclone.cloak.data.PostureArtifacts
import dev.cyclone.cloak.data.PostureProvenance
import dev.cyclone.cloak.data.PostureProvenanceStore
import dev.cyclone.cloak.forge.DefaultDeviceProfileEngine
import dev.cyclone.cloak.forge.IdentityEngine
import dev.cyclone.cloak.forge.EngineBundleCodec
import dev.cyclone.cloak.forge.RuntimeEvidence
import dev.cyclone.cloak.data.PhoneStore
import dev.cyclone.cloak.forge.CloakFleet
import dev.cyclone.cloak.forge.CloakForge
import dev.cyclone.cloak.forge.Imports
import dev.cyclone.cloak.forge.PhoneCatalog
import dev.cyclone.cloak.forge.PhoneDraft
import dev.cyclone.cloak.forge.PhoneTemplate
import dev.cyclone.cloak.forge.ProfileUniqueness
import dev.cyclone.cloak.data.JsonDirectory
import dev.cyclone.cloak.forge.ProfileBackup
import dev.cyclone.cloak.forge.ProfileValidator
import dev.cyclone.cloak.root.CloakResolver
import dev.cyclone.cloak.root.RootDoctorCode
import dev.cyclone.cloak.root.PrivilegedSession
import dev.cyclone.cloak.root.RootSessions
import dev.cyclone.cloak.root.RootDoctorResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.Instant

/** An identity on this phone: its id and its full cloak profile. */
typealias Identity = Pair<String, JSONObject>

/** The phone builder, open on [draft]. [isNew]: saving adds a phone; otherwise it replaces the one with [draft]'s id. */
data class PhoneEditor(val draft: PhoneDraft, val isNew: Boolean, val note: String? = null) {
    val findings: List<ProfileValidator.Finding> get() = draft.findings()
    val canSave: Boolean get() = findings.none { it.isError }
}

/** An imported profile with findings, waiting for the owner's decision. */
data class ImportReview(val profile: JSONObject, val findings: List<ProfileValidator.Finding>,
    val verb: String = "Imported", val remaining: List<JSONObject> = emptyList(), val engineMetadata: JSONObject? = null) {
    val canSave: Boolean get() = findings.none { it.isError }
}

data class CloakState(
    val phones: List<PhoneTemplate> = emptyList(),
    val identities: List<Identity> = emptyList(),
    val identityInventoryKnown: Boolean = false,
    val unreadableIdentityIds: Set<String> = emptySet(),
    val engineMetadata: Map<String, JSONObject> = emptyMap(),
    val postures: Map<String, PostureRecord> = emptyMap(),
    val postureErrors: Map<String, String> = emptyMap(),
    val currentArtifactDigest: String? = null,
    val diagnosticsReview: DiagnosticsReview? = null,
    val engineCoverage: Map<String, String> = emptyMap(),
    val bindings: List<CloakBinding> = emptyList(),
    val report: SyncReport? = null,
    val syncing: Boolean = false,
    val providerState: String = "not registered",
    val companionPresence: List<CompanionPresence> = emptyList(),
    val checkingCompanions: Boolean = false,
    val rootDoctor: RootDoctorResult = RootDoctorResult(RootDoctorCode.NOT_CHECKED),
    /** What is running right now ("Creating identity…"), or null. */
    val busy: String? = null,
    val opening: Boolean = false,
    val editor: PhoneEditor? = null,
    val review: ImportReview? = null,
    val generation: ProfileGeneration.Result? = null,
    val bindingFlow: BindingDraft? = null,
    val backupReview: ProfileBackup.Review? = null,
    val backupMessage: String? = null,
    val backups: List<Pair<String, JSONObject>> = emptyList(),
) {
    val uniqueness: ProfileUniqueness get() = ProfileUniqueness(identities)
    val registry: List<CycloneProfile> get() = report?.snapshot?.registry.orEmpty()
    val placement: Placement? get() = report?.placement
    /** Null in Main; this Cyclone profile's id when Cloak runs inside one. */
    val ownProfileId: String? get() = (placement as? Placement.InProfile)?.profile?.id
    val canBind: Boolean get() = report?.gate?.bindings == true
    val pills: Map<String, CloakPill>
        get() = registry.mapNotNull { p -> CloakPills.forProfile(p, bindings, report?.rootFacts?.get(p.id))?.let { p.id to it } }.toMap()
    val openTargets: List<CycloneProfile>
        get() {
            val snapshot = report?.snapshot ?: return emptyList()
            val placement = placement ?: return emptyList()
            return if (report?.gate?.openRequests == true) OpenRequests.targets(snapshot, placement) else emptyList()
        }

    fun phone(id: String?): PhoneTemplate? = phones.firstOrNull { it.id == id }
    fun bindingsOf(identityId: String): List<CloakBinding> = bindings.filter { it.cloakProfileId == identityId }

    /** Main binds every ready profile; a profile's Cloak only its own. Never Main itself. */
    fun bindableHere(profile: CycloneProfile): Boolean = profile.bindable && (ownProfileId == null || ownProfileId == profile.id)
}

class CloakViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = DefaultDeviceProfileEngine()
    private val engineMetadataStore by lazy { EngineMetadataStore(app, EngineBundleCodec::validateMetadata) }
    private val postureStore by lazy { PostureStore(app) }
    private val bundledArtifactDigest by lazy { PostureArtifacts.bundledModuleDigest(app) }
    private val postureProvenance by lazy { PostureProvenanceStore(app) }
    private val app: Context get() = getApplication()
    private val _state = MutableStateFlow(CloakState())
    val state: StateFlow<CloakState> = _state.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    /** One change at a time: binding, publishing and saving never interleave. */
    private val work = Mutex()

    init {
        viewModelScope.launch(Dispatchers.IO) { loadLocal() }
        refresh()
    }

    private fun say(message: String) {
        _messages.tryEmit(message)
    }

    /** Runs [block] off the main thread, one at a time, with [label] shown while it runs; errors become a message. */
    private fun launchWork(label: String, block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            work.withLock {
                _state.update { it.copy(busy = label) }
                try {
                    LogBus.trace("app", "local-work") { block() }
                } catch (error: Exception) {
                    LogBus.event("exceptions", "ui-work", Transition.FAILED, reason = "ACTION_FAILED", error = error)
                    say(error.message ?: "Something went wrong.")
                } finally {
                    _state.update { it.copy(busy = null) }
                    loadLocal()
                }
            }
        }
    }

    private fun loadLocal() {
        val builtIn = runCatching { PhoneCatalog.parse(PhoneStore.catalogText(app)) }.getOrDefault(emptyList())
        val own = PhoneStore.userPhones(app).mapNotNull { runCatching { PhoneTemplate.fromJson(it) }.getOrNull() }
            .filter { phone -> builtIn.none { it.id == phone.id } }
            .sortedBy { it.label.lowercase() }
        val identities = CloakStore.all(app)
        val readableIds = identities.map { it.first }.toSet()
        val directory = java.io.File(app.filesDir, "profiles")
        val files = directory.listFiles()
        val inventoryKnown = files != null || !directory.exists()
        val unreadable = files.orEmpty()
            .filter { it.isFile && it.name.endsWith(".json") && !it.name.startsWith(".") }
            .map { it.name.removeSuffix(".json") }.filter { it !in readableIds }.toSet()
        if (unreadable.isNotEmpty()) LogBus.event("profiles", "inventory", Transition.DEGRADED, reason = "UNREADABLE_PROFILES")
        _state.update {
            it.copy(phones = builtIn + own, identities = identities, identityInventoryKnown = inventoryKnown,
                engineMetadata = identities.mapNotNull { (id, profile) -> engineMetadataStore.read(profile)?.let { id to it } }.toMap(),
                postures = identities.mapNotNull { (id, _) -> postureStore.read(id)?.let { id to it } }.toMap(),
                currentArtifactDigest = bundledArtifactDigest,
                unreadableIdentityIds = unreadable, bindings = CloakBindingStore.all(app),
                backups = backupDirectory().all().mapNotNull { (id, value) -> value.optJSONObject("manifest")?.let { manifest -> id to manifest } })
        }
    }

    // ---- Cyclone ----------------------------------------------------------------------------------------------------

    fun refresh() {
        if (_state.value.syncing) return
        _state.update { it.copy(syncing = true) }
        viewModelScope.launch(Dispatchers.IO) {
            var provider = "not registered"
            val report = work.withLock {
                CycloneBridge.sync(app, foreground = true) { cyclone, report ->
                    // The startup provider lives as long as this process; register it again on every connect.
                    val gate = report.gate
                    provider = when {
                        gate?.approved != true -> "not registered"
                        gate.minor < 1 -> "Cyclone too old"
                        !gate.startup -> "not approved"
                        else -> runCatching { cyclone.registerProfileProvider(CloakStartupProvider(app)) }
                            .fold({ "ready" }, { it.message ?: "failed" })
                    }
                }
            }
            loadLocal()
            val presence = CompanionPresenceLookup.inspect(report.snapshot?.registry.orEmpty()) { user ->
                if (user == InstallPlacement.myUser()) CompanionPresenceProbe.local(app)
                else CompanionFacts()
            }
            _state.update { it.copy(report = report, syncing = false, providerState = provider, companionPresence = presence) }
        }
    }

    fun checkCompanionPresence() {
        if (_state.value.checkingCompanions) return
        _state.update { it.copy(checkingCompanions = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profiles = _state.value.registry
                val rows = CompanionPresenceLookup.inspect(profiles) { user ->
                    LogBus.trace("companions", "presence", user.toString()) {
                        CompanionPresenceProbe.inspect(app, user)
                    }
                }
                _state.update { current ->
                    // A concurrent refresh may replace user assignments; never show results for an old assignment.
                    current.copy(companionPresence = current.registry.map { profile ->
                        rows.firstOrNull { it.profileId == profile.id && it.user == profile.androidUserId }
                            ?: CompanionPresence(profile.id, profile.label, profile.androidUserId?.takeIf { it >= 0 },
                                CompanionFacts())
                    })
                }
                rows.forEach { row ->
                    LogBus.event("companions", "presence-result", if (row.state == dev.cyclone.cloak.cyclone.CompanionState.READY)
                        Transition.COMPLETED else Transition.DEGRADED, target = row.user?.toString(), reason = row.state.name)
                }
            } finally { _state.update { it.copy(checkingCompanions = false) } }
        }
    }

    fun openInCyclone(profile: CycloneProfile) {
        if (_state.value.opening) return
        _state.update { it.copy(opening = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = CycloneBridge.requestOpen(app, profile)
            _state.update { it.copy(opening = false) }
            say(outcome.message)
            if (outcome.refreshProfiles) refresh()
        }
    }

    // ---- Phones -----------------------------------------------------------------------------------------------------

    private fun takenPhoneIds() = _state.value.phones.map { it.id }.toSet()

    fun newPhone() = _state.update { it.copy(editor = PhoneEditor(PhoneDraft.blank(PhoneTemplate.idFor("new phone", takenPhoneIds())), isNew = true)) }

    fun editPhone(phone: PhoneTemplate) {
        if (phone.builtIn) return clonePhone(phone)
        _state.update { it.copy(editor = PhoneEditor(PhoneDraft.from(phone), isNew = false)) }
    }

    fun clonePhone(phone: PhoneTemplate) {
        val label = "${phone.label} copy"
        val draft = PhoneDraft.from(phone).copy(id = PhoneTemplate.idFor(label, takenPhoneIds()), label = label)
        _state.update { it.copy(editor = PhoneEditor(draft, isNew = true, note = "A copy of ${phone.label}. Change what you like, then save.")) }
    }

    fun updateDraft(draft: PhoneDraft) = _state.update { s -> s.copy(editor = s.editor?.copy(draft = draft)) }

    fun closeEditor() = _state.update { it.copy(editor = null) }

    fun savePhone() {
        val editor = _state.value.editor ?: return
        val firstError = editor.findings.firstOrNull { it.isError }
        if (firstError != null) return say("Fix this first: ${firstError.message}")
        launchWork("Saving phone…") {
            val phone = editor.draft.toTemplate()
            PhoneStore.save(app, phone.toJson())
            _state.update { it.copy(editor = null) }
            say("Saved ${phone.label}. You can make identities from it now.")
        }
    }

    fun deletePhone(phone: PhoneTemplate) {
        if (phone.builtIn) return
        launchWork("Deleting phone…") {
            PhoneStore.delete(app, phone.id)
            say("Deleted ${phone.label}. Identities made from it keep their own copy.")
        }
    }

    fun generateProfile(phoneId: String) {
        val current = _state.value
        if (current.busy != null || current.generation == ProfileGeneration.Result.Running) return
        val phone = current.phone(phoneId)
        if (phone == null) {
            _state.update { it.copy(generation = ProfileGeneration.Result.Failed("TEMPLATE_MISSING", "Choose an available phone template first.")) }
            return
        }
        // Set synchronously so repeated taps cannot enqueue more than one generation.
        _state.update { it.copy(generation = ProfileGeneration.Result.Running) }
        launchWork("Generating profile…") {
            val result = try {
                ProfileGeneration.generate(phone, CloakStore.all(app), save = { CloakStore.save(app, it) },
                    saveEngine = { profile, metadata -> engineMetadataStore.save(profile, metadata) { CloakStore.save(app, it) } })
            } catch (_: Exception) {
                ProfileGeneration.Result.Failed("GENERATION_FAILED", "Could not generate the profile. Try again.")
            }
            _state.update { it.copy(generation = result, review = if (result is ProfileGeneration.Result.NeedsReview)
                ImportReview(result.profile, result.findings, "Created") else it.review) }
            if (result is ProfileGeneration.Result.Saved) say("Saved ${result.name}. Find it in Identities.")
        }
    }

    fun dismissGenerationResult() {
        _state.update { if (it.generation == ProfileGeneration.Result.Running) it else it.copy(generation = null) }
    }

    // ---- Import and export ------------------------------------------------------------------------------------------

    fun import(uri: Uri) = launchWork("Reading file…") {
        val text = app.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { readBounded(it, 20_000_000) }
            ?: throw IllegalArgumentException("Couldn't open that file.")
        require(text.isNotBlank()) { "That file is empty." }
        val objectFile = runCatching { JSONObject(text) }.getOrNull()
        if (objectFile != null && (objectFile.optString("kind") in setOf(ProfileBackup.KIND, Imports.FLEET_KIND) ||
                objectFile.has("device"))) {
            reviewBackup(text)
            return@launchWork
        }
        when (val parsed = Imports.parse(text, takenPhoneIds())) {
            is Imports.Parsed.Engine -> saveIdentity(parsed.bundle.profile, "Imported", engineMetadata = parsed.bundle.metadata)
            is Imports.Parsed.Profile -> {
                saveIdentity(parsed.profile, "Imported")
            }
            is Imports.Parsed.Fleet -> {
                saveBatch(parsed.profiles, "Imported")
            }
            is Imports.Parsed.Phone -> _state.update {
                it.copy(editor = PhoneEditor(PhoneDraft.from(parsed.phone), isNew = true, note = when {
                    parsed.fromDump && parsed.findings.any { f -> f.isError } ->
                        "Read from a dump. Fill in what it couldn't tell (marked below), then save."
                    parsed.fromDump -> "Read from a dump. Check it, then save."
                    else -> "An imported phone. Check it, then save."
                }))
            }
        }
    }

    fun importEngineEvidence(uri: Uri) = launchWork("Reading probe evidence…") {
        val text = app.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { readBounded(it, 2_000_000) }
            ?: error("Couldn't open that file.")
        val coverage = RuntimeEvidence.toScoreInput(JSONObject(text))
        _state.update { it.copy(engineCoverage = coverage) }
        say("Loaded probe fixture coverage. It describes the measured binary, not this saved identity or the installed module.")
    }

    private fun postureWork(identityId: String, label: String, failure: String, block: suspend () -> Unit) = launchWork(label) {
        _state.update { it.copy(postureErrors = it.postureErrors - identityId) }
        try { block() } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            val safeReasons = setOf("This compatibility report is for another profile. Select its identity before loading.",
                "The saved report changed. Review it again before continuing.",
                "This exact local posture report was already imported.", "This local posture report is not newer than the saved record.",
                "A completed explicit local reset or reseed is required.", "This identity is not marked as needing rotation.",
                "That identity is gone. No local evidence was changed.",
                "This compatibility report does not match the selected profile. No local evidence was saved.")
            val message = error.message?.takeIf { it in safeReasons } ?: failure
            _state.update { it.copy(postureErrors = it.postureErrors + (identityId to message)) }
            say(message)
        }
    }

    fun importPostureReport(uri: Uri, identityId: String) = postureWork(identityId, "Reading local compatibility report…",
        "Couldn't load the compatibility report. Check the file format and try again. No record was saved.") {
        _state.update { it.copy(diagnosticsReview = null) }
        val text = app.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { readBounded(it, 2_000_000) }
            ?: error("Couldn't open that file.")
        dev.cyclone.cloak.forge.StrictJson(text).check()
        val report = CompatibilityPostureReport.parse(JSONObject(text))
        val profile = CloakStore.find(app, identityId) ?: error("That identity is gone. No local evidence was changed.")
        require(report.profileId == identityId) { "This compatibility report is for another profile. Select its identity before loading." }
        val diagnostics = AdaptedDiagnostics(report, report.profileId, report.profileDigest)
        _state.update { it.copy(diagnosticsReview = DiagnosticsReview(diagnostics,
            diagnostics.preview(profile, bundledArtifactDigest, Instant.now()))) }
    }

    fun loadDiagnosticsForPosture(uri: Uri, identityId: String) = postureWork(identityId, "Reading local diagnostics…",
        "Couldn't load diagnostics. Check the file format and try again. No record was saved.") {
        _state.update { it.copy(diagnosticsReview = null) }
        val text = app.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { readBounded(it, 2_000_000) }
            ?: error("Couldn't open that file.")
        val profile = CloakStore.find(app, identityId) ?: error("That identity is gone.")
        val diagnostics = DiagnosticsPostureAdapter.convert(text, profile)
        _state.update { it.copy(diagnosticsReview = DiagnosticsReview(diagnostics,
            diagnostics.preview(profile, bundledArtifactDigest, Instant.now()))) }
    }

    fun saveDiagnosticsPosture() {
        val identityId = _state.value.diagnosticsReview?.diagnostics?.report?.profileId ?: return
        postureWork(identityId, "Saving local compatibility evidence…", "Couldn't save local evidence. Review the report and try again.") {
            val review = _state.value.diagnosticsReview ?: return@postureWork
            val profile = CloakStore.find(app, review.diagnostics.report.profileId) ?: error("That identity is gone. No local evidence was changed.")
            when (val outcome = review.save(profile, bundledArtifactDigest, Instant.now(), ::acceptPostureRecord)) {
                is DiagnosticsReview.SaveResult.ReviewChanged -> {
                    _state.update { it.copy(diagnosticsReview = outcome.review) }
                    return@postureWork say("The profile or report context changed. Review the updated preview before saving.")
                }
                DiagnosticsReview.SaveResult.Blocked -> error("This compatibility report does not match the selected profile. No local evidence was saved.")
                DiagnosticsReview.SaveResult.Saved -> Unit
            }
            _state.update { it.copy(diagnosticsReview = null) }
            say("Saved local compatibility evidence. No platform verdict was inferred.")
        }
    }

    fun markPostureNeedsRotation(identityId: String, reviewed: PostureRecord?) = postureWork(identityId,
        "Marking local rotation state…", "Couldn't mark rotation state. No identity reset was performed. Try again.") {
        require(CloakStore.find(app, identityId) != null) { "That identity is gone. No local evidence was changed." }
        postureStore.update(CompatibilityReportActions.mark(identityId, postureStore.read(identityId), reviewed))
        say("Marked Needs rotation. No identity reset or reseed was performed.")
    }

    fun clearPostureAfterReset(identityId: String, reviewed: PostureRecord, resetCompleted: Boolean) = postureWork(identityId,
        "Clearing local rotation state…", "Couldn't clear rotation state. Review the report and try again.") {
        require(CloakStore.find(app, identityId) != null) { "That identity is gone. No local evidence was changed." }
        require(reviewed.profileId == identityId) { "The saved report changed. Review it again before continuing." }
        postureStore.update(CompatibilityReportActions.clear(postureStore.read(identityId), reviewed, resetCompleted))
        say("Local evidence state is Unknown after your confirmed reset. Compatibility has not been checked again.")
    }

    fun dismissDiagnosticsPosture() = _state.update { if (it.busy == null) it.copy(diagnosticsReview = null) else it }

    private fun acceptPostureRecord(record: PostureRecord) {
        val previous = postureStore.read(record.profileId)
        require(PostureProvenance.canReplace(previous, record)) {
            if (previous?.reportDigest == record.reportDigest) "This exact local posture report was already imported."
            else "This local posture report is not newer than the saved record."
        }
        val digest = record.reportDigest
        if (digest != null) {
            require(!postureProvenance.consumed(record.profileId, digest)) { "This exact local posture report was already imported." }
        }
        postureStore.update(record)
        digest?.let { postureProvenance.remember(record.profileId, it) }
    }
    fun saveReviewed() {
        val review = _state.value.review ?: return
        if (!review.canSave) return say("This profile has errors, so it can't be used as it is.")
        launchWork("Saving identity…") {
            val currentFindings = ProfileUniqueness(CloakStore.all(app)).findings(review.profile)
            if (currentFindings != review.findings.filter { it.code == "DUPLICATE_IDENTIFIER" }) {
                _state.update { it.copy(review = review.copy(findings = ProfileValidator.validateProfile(review.profile) + currentFindings)) }
                return@launchWork say("The library changed. Review the current conflicts before saving.")
            }
            if (saveIdentity(review.profile, review.verb, allowDuplicates = true, engineMetadata = review.engineMetadata)) {
                _state.update { it.copy(review = null, generation = if (it.generation is ProfileGeneration.Result.NeedsReview &&
                    it.generation.profile.optString("id") == review.profile.optString("id"))
                    ProfileGeneration.Result.Saved(review.profile.optString("id"), review.profile.optString("name"), review.findings)
                    else it.generation) }
                saveBatch(review.remaining, review.verb)
            }
        }
    }

    /** An imported profile that doesn't pass: open its phone in the builder to fix and save. */
    fun fixReviewedInBuilder() {
        val review = _state.value.review ?: return
        val label = review.profile.optString("name").ifBlank { "Imported phone" }
        val phone = PhoneTemplate.fromProfile(PhoneTemplate.idFor(label, takenPhoneIds()), label, review.profile)
        _state.update {
            it.copy(review = null, editor = PhoneEditor(PhoneDraft.from(phone), isNew = true,
                note = "The phone from that profile. Fix what's marked, save it, then make an identity from it."))
        }
    }

    fun dismissReview() = _state.update {
        if (it.busy != null) it else it.copy(review = null,
            generation = if (it.generation is ProfileGeneration.Result.NeedsReview) null else it.generation)
    }

    fun exportPhone(phone: PhoneTemplate, uri: Uri) = launchWork("Exporting phone…") {
        write(uri, Imports.exportPhone(phone).toString(2))
        say("Exported ${phone.label}.")
    }

    private fun backupDirectory() = JsonDirectory(java.io.File(app.filesDir, "backups"))
    fun exportProfiles(identityId: String? = null, includeIdentity: Boolean = false, label: String = "") = launchWork("Saving local backup…") {
        try {
            val identities = CloakStore.all(app)
            val profiles = if (identityId == null) {
                val files = java.io.File(app.filesDir, "profiles").listFiles().orEmpty().count { it.isFile && it.name.endsWith(".json") && !it.name.startsWith(".") }
                require(files == identities.size) { "Some profiles are unreadable. Resolve them before exporting the full library." }
                identities.map { it.second }
            } else listOf(identities.firstOrNull { it.first == identityId }?.second ?: error("That profile is unreadable or missing."))
            val version = app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()
            val backup = ProfileBackup.export(profiles, version, includeIdentity, label)
            val id = java.util.UUID.randomUUID().toString()
            backupDirectory().write(id, backup)
            val download = runCatching { dev.cyclone.cloak.data.LocalDocumentExport.write(app, "cyclone-cloak-$id.json", backup.toString(2).toByteArray(Charsets.UTF_8)) }
            _state.update { it.copy(backupMessage = "Saved ${profiles.size} profile(s) atomically in the on-device backup library. " +
                (if (download.isSuccess) "A complete copy is in Downloads/CycloneCloak. " else "Downloads copy failed; the complete in-app backup is retained. ") +
                if (includeIdentity) "Identity values included by your selection." else "Seeds and identifiers excluded; import will generate fresh values.") }
        } catch (error: Exception) { _state.update { it.copy(backupMessage = "Backup not saved: ${error.message}") } }
    }
    fun importLocalBackup(id: String) = launchWork("Reading local backup…") {
        try { reviewBackup(backupDirectory().read(id)?.toString() ?: error("Backup is corrupt or missing.")) }
        catch (error: Exception) { _state.update { it.copy(backupMessage = "Import not saved: ${error.message}", backupReview = null) } }
    }
    private fun reviewBackup(text: String) {
        try {
            val review = ProfileBackup.parse(text, CloakStore.all(app))
            _state.update { it.copy(backupReview = review, backupMessage = null) }
        } catch (error: Exception) { _state.update { it.copy(backupMessage = "Import not saved: ${error.message}", backupReview = null) } }
    }
    fun dismissBackupReview() = _state.update { if (it.busy != null) it else it.copy(backupReview = null) }
    fun confirmBackupImport(acceptWarnings: Boolean) {
        val review = _state.value.backupReview ?: return
        launchWork("Importing profile library…") {
            try {
                ProfileBackup.commit(review, CloakStore.all(app), true, acceptWarnings) { CloakStore.importBatch(app, it) }
                _state.update { it.copy(backupReview = null, backupMessage = "Imported ${review.profiles.size} profile(s). Existing profiles and bindings were retained.") }
            } catch (error: Exception) { _state.update { it.copy(backupMessage = "Import not saved: ${error.message}") } }
        }
    }

    private fun write(uri: Uri, text: String) {
        app.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw IllegalStateException("Couldn't write that file.")
    }

    // ---- Identities -------------------------------------------------------------------------------------------------

    private suspend fun saveIdentity(profile: JSONObject, verb: String, allowDuplicates: Boolean = false, engineMetadata: JSONObject? = null): Boolean {
        val existing = CloakStore.all(app)
        if (engineMetadata != null) require(CloakStore.find(app, profile.getString("id")) == null &&
            !java.io.File(app.filesDir, "profiles/${profile.getString("id")}.json").exists()) { "That profile already exists. Engine import never replaces saved identities." }
        val name = profile.optString("name").trim()
        require(existing.none { (id, p) -> id != profile.optString("id") && p.optString("name").equals(name, ignoreCase = true) }) {
            "An identity named \"$name\" already exists. Choose another name."
        }
        val findings = ProfileValidator.validateProfile(profile) + ProfileUniqueness(existing).findings(profile)
        if (findings.any { it.isError } || (!allowDuplicates && findings.isNotEmpty())) {
            _state.update { it.copy(review = ImportReview(JSONObject(profile.toString()), findings, verb, engineMetadata = engineMetadata)) }
            return false
        }
        if (engineMetadata == null) CloakStore.save(app, profile)
        else engineMetadataStore.save(profile, engineMetadata) { CloakStore.save(app, it) }
        say("$verb $name.")
        if (CloakBindingStore.all(app).any { it.cloakProfileId == profile.optString("id") }) publishAndSync()
        return true
    }

    private suspend fun saveBatch(profiles: List<JSONObject>, verb: String) {
        profiles.forEachIndexed { index, profile ->
            if (!saveIdentity(profile, verb)) {
                _state.update { it.copy(review = it.review?.copy(remaining = profiles.drop(index + 1))) }
                return
            }
        }
    }

    fun regenerateReviewed(field: String) = launchWork("Regenerating value…") {
        val review = _state.value.review ?: return@launchWork
        val index = ProfileUniqueness(CloakStore.all(app))
        val draft = index.regenerate(review.profile, field)
        _state.update { it.copy(review = review.copy(profile = draft,
            engineMetadata = null,
            findings = ProfileValidator.validateProfile(draft) + index.findings(draft))) }
    }

    fun reviewIdentity(id: String) = launchWork("Checking identity…") {
        val profile = CloakStore.find(app, id) ?: error("That identity is gone.")
        _state.update { it.copy(review = ImportReview(profile,
            ProfileValidator.validateProfile(profile) + ProfileUniqueness(CloakStore.all(app)).findings(profile), "Updated")) }
    }

    fun duplicateIdentity(id: String) = launchWork("Duplicating identity…") {
        val source = CloakStore.find(app, id) ?: error("That identity is gone.")
        val draft = JSONObject(source.toString()).put("id", java.util.UUID.randomUUID().toString())
        val names = CloakStore.all(app).map { it.second.optString("name").lowercase() }.toSet()
        val base = source.optString("name").take(50) + " copy"
        var name = base
        var suffix = 2
        while (name.lowercase() in names) name = "$base ${suffix++}"
        saveIdentity(draft.put("name", name), "Duplicated")
    }

    fun createIdentity(name: String, phoneId: String) {
        val phone = _state.value.phone(phoneId) ?: return say("Choose a phone first.")
        launchWork("Creating identity…") {
            val clean = name.trim()
            require(clean.length in 1..64) { "Names are 1 to 64 characters." }
            val bundle = engine.generate_unique(IdentityEngine.GenerationRequest(clean, CloakForge.newSeed(), phone), CloakStore.all(app).map { it.second })
            val profile = bundle.getJSONObject("profile")
            ProfileValidator.errors(profile).firstOrNull()?.let { throw IllegalArgumentException(it) }
            saveIdentity(profile, "Created", engineMetadata = bundle.getJSONObject("metadata"))
        }
    }

    fun forgeFleet(phoneId: String, count: Int) {
        val phone = _state.value.phone(phoneId) ?: return say("Choose a phone first.")
        launchWork("Creating $count identities…") {
            val profiles = CloakFleet.forgeFleet(phone, count)
            saveBatch(profiles, "Created")
        }
    }

    fun renameIdentity(id: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > 64) return say("Names are 1 to 64 characters.")
        launchWork("Renaming…") {
            val profile = CloakStore.find(app, id) ?: throw IllegalStateException("That identity is gone.")
            saveIdentity(profile.put("name", clean), "Renamed to")

        }
    }

    fun deleteIdentity(id: String) {
        val bound = _state.value.bindingsOf(id)
        if (bound.isNotEmpty()) return say("It's bound to ${bound.size} apps. Remove those bindings first.")
        launchWork("Deleting identity…") {
            // Check the store itself: the screen's list may be a moment old.
            val stillBound = CloakBindingStore.all(app).count { it.cloakProfileId == id }
            require(stillBound == 0) { "It's bound to $stillBound apps. Remove those bindings first." }
            CloakStore.delete(app, id)
            say("Deleted.")
        }
    }

    // ---- Bindings ---------------------------------------------------------------------------------------------------

    fun startBinding(identityId: String? = null) {
        if (_state.value.busy != null || _state.value.bindingFlow?.running == true) return
        _state.update { it.copy(bindingFlow = BindingDraft(identityId = identityId)) }
    }
    fun closeBindingFlow() {
        _state.update { if (it.bindingFlow?.running == true) it else it.copy(bindingFlow = null) }
    }
    private fun bindingInputs(freshLocal: Boolean = false): BindingInputs {
        val state = _state.value
        return BindingInputs(state.registry, if (freshLocal) CloakStore.all(app) else state.identities,
            if (freshLocal) CloakBindingStore.all(app) else state.bindings, state.canBind, state.ownProfileId)
    }
    private fun changeBinding(transform: (BindingDraft) -> BindingDraft) {
        _state.update { state -> state.copy(bindingFlow = state.bindingFlow?.let { if (it.running) it else transform(it) }) }
    }
    fun chooseBindingProfile(id: String) = changeBinding { BindingFlow.chooseProfile(it, id) }
    fun chooseBindingApps(packages: Set<String>) = changeBinding { BindingFlow.selectApps(it, packages) }
    fun chooseBindingIdentity(id: String) = changeBinding { it.copy(identityId = id, reviewStamp = null, acknowledged = false, note = null) }
    fun acknowledgeBinding(value: Boolean) = changeBinding { it.copy(acknowledged = value) }
    fun nextBindingStep() = changeBinding {
        if (it.step == BindingStep.REVIEW) it.copy(reviewStamp = BindingFlow.stamp(it, bindingInputs()), acknowledged = false, note = null)
        else BindingFlow.next(it, bindingInputs())
    }
    fun backBindingStep() {
        val flow = _state.value.bindingFlow ?: return
        if (flow.running) return
        if (flow.step == BindingStep.PROFILE) return closeBindingFlow()
        changeBinding { it.copy(step = if (it.step == BindingStep.RESULT) BindingStep.REVIEW else BindingStep.entries[it.step.ordinal - 1],
            reviewStamp = null, acknowledged = false, outcome = null, note = null) }
        if (_state.value.bindingFlow?.step == BindingStep.REVIEW) changeBinding { it.copy(reviewStamp = BindingFlow.stamp(it, bindingInputs())) }
    }
    fun generateBindingProfile(phoneId: String) {
        val flow = _state.value.bindingFlow ?: return
        val phone = _state.value.phone(phoneId) ?: return
        if (flow.step != BindingStep.IDENTITY || flow.running || _state.value.busy != null) return
        changeBinding { it.copy(running = true, note = null) }
        launchWork("Generating compatibility profile…") {
            val result = try {
                ProfileGeneration.generate(phone, CloakStore.all(app), save = { CloakStore.save(app, it) },
                    saveEngine = { profile, metadata -> engineMetadataStore.save(profile, metadata) { CloakStore.save(app, it) } })
            } catch (_: Exception) { ProfileGeneration.Result.Failed("GENERATION_FAILED", "Generation failed. Check local storage and try again.") }
            loadLocal()
            _state.update { state -> state.copy(bindingFlow = state.bindingFlow?.copy(running = false,
                identityId = if (result is ProfileGeneration.Result.Saved) result.id else state.bindingFlow.identityId,
                note = when (result) {
                    is ProfileGeneration.Result.Saved -> "Saved ${result.name}. Review it before binding."
                    is ProfileGeneration.Result.Rejected -> result.findings.joinToString("\n") { it.message }
                    is ProfileGeneration.Result.NeedsReview -> "Generated values conflict with the library. Generate again or review an existing profile in Identities. Nothing was saved."
                    is ProfileGeneration.Result.Failed -> result.message
                    else -> "Generation did not complete. Try again."
                })) }
        }
    }
    fun confirmBindingFlow() {
        val draft = _state.value.bindingFlow ?: return
        if (draft.running || _state.value.busy != null || !BindingFlow.canConfirm(draft, bindingInputs())) return
        changeBinding { it.copy(running = true) }
        launchBindingWork("Saving binding…") {
            try {
                val input = bindingInputs(freshLocal = true)
                if (!BindingFlow.canConfirm(draft, input)) {
                    _state.update { it.copy(bindingFlow = draft.copy(reviewStamp = BindingFlow.stamp(draft, input),
                        acknowledged = false, note = "The library or target changed. Review the current findings and confirm again.")) }
                    return@launchBindingWork
                }
                val records = BindingFlow.records(draft, input, System.currentTimeMillis())
                // Existing store preserves the logical profile-ID/package key and atomic update behavior.
                saveBindingRecords(records, input.bindings)
                finishBindingFlow(draft)
            } catch (_: Exception) {
                bindingFlowFailed(draft)
            }
        }
    }
    fun retryBindingFlow() {
        val draft = _state.value.bindingFlow ?: return
        if (draft.running || _state.value.busy != null || draft.outcome?.saved != true || draft.outcome.ready) return
        changeBinding { it.copy(running = true) }
        launchBindingWork("Retrying reconciliation…") {
            try {
                val input = bindingInputs(freshLocal = true)
                val errors = BindingFlow.findings(draft, input).filter { it.isError }
                require(BindingFlow.saved(draft, input.bindings))
                if (errors.isNotEmpty()) {
                    _state.update { it.copy(bindingFlow = draft.copy(running = false, outcome = BindingOutcome(false, true,
                        "Binding retained. " + errors.joinToString("\n") { it.message }))) }
                    return@launchBindingWork
                }
                // Retry publication/sync only: never recreate or overwrite a changed binding.
                finishBindingFlow(draft)
            } catch (_: Exception) { bindingFlowFailed(draft) }
        }
    }
    private fun finishBindingFlow(draft: BindingDraft) {
        val root = publishAndSync()
        val result = BindingFlow.completion(draft, root, _state.value.report, CloakBindingStore.all(app))
        _state.update { it.copy(bindingFlow = draft.copy(step = BindingStep.RESULT, running = false, outcome = result, note = null)) }
    }
    private fun bindingFlowFailed(draft: BindingDraft) {
        val saved = BindingFlow.saved(draft, CloakBindingStore.all(app))
        _state.update { it.copy(bindingFlow = draft.copy(step = BindingStep.RESULT, running = false,
            outcome = BindingOutcome(false, saved, if (saved) "Binding saved locally. Check Health Doctor and retry reconciliation."
                else "Binding was not saved or the target changed. Return to review before trying again."))) }
    }

    fun bind(identityId: String, profile: CycloneProfile) {
        val state = _state.value
        when {
            !state.canBind -> return say("Approve Cyclone Cloak's profile settings in Cyclone → Settings → Connectors first.")
            !state.bindableHere(profile) -> return say(
                if (profile.bindable) "Bind ${profile.label} from Cyclone Cloak in Main, or from Cloak inside ${profile.label}."
                else "${profile.label} isn't ready in Cyclone yet.",
            )
        }
        launchBindingWork("Binding…") {
            val count = bindLocally(profile, identityId)
            if (count == 0) return@launchBindingWork say("Cyclone shared no apps for ${profile.label}.")
            val result = publishAndSync()
            say(if (result.published) "Bound $count apps in ${profile.label}." else "Bound $count apps. ${result.title}: ${result.message}")
        }
    }

    fun bulkBind() {
        val state = _state.value
        if (!state.canBind) return say("Approve Cyclone Cloak's profile settings in Cyclone → Settings → Connectors first.")
        val ready = state.registry.filter(state::bindableHere)
        val plan = BulkBind.plan(state.identities.map { it.first }, ready, state.bindings)
        if (plan.assignments.isEmpty()) {
            return say(when {
                ready.isEmpty() -> "No ready Cyclone profiles to bind."
                plan.unusedCloakProfiles == 0 -> "No unused identities left. Create more first."
                else -> "Every ready profile is bound already."
            })
        }
        launchBindingWork("Binding ${plan.assignments.size} profiles…") {
            val count = plan.assignments.sumOf { (profile, id) -> bindLocally(profile, id) }
            val result = publishAndSync()
            val note = if (result.published) "" else " ${result.title}: ${result.message}"
            say("Bound $count apps across ${plan.assignments.size} profiles.$note")
        }
    }

    fun toggleBinding(binding: CloakBinding) {
        if (binding.origin == CloakBinding.ORIGIN_MAIN) return say("This binding is managed by Cyclone Cloak in Main.")
        launchBindingWork(if (binding.enabled) "Turning off…" else "Turning on…") {
            CloakBindingStore.upsert(app, binding.copy(enabled = !binding.enabled, updatedAt = System.currentTimeMillis()))
            publishAndSync()
        }
    }

    fun removeBinding(binding: CloakBinding) {
        if (binding.origin == CloakBinding.ORIGIN_MAIN) return say("This binding is managed by Cyclone Cloak in Main.")
        launchBindingWork("Removing binding…") {
            CloakBindingStore.remove(app, binding.profileId, binding.androidUserId, binding.packageName)
            publishAndSync()
        }
    }

    /** Approval precedes all binding writes; all synchronous root calls reuse this session. */
    private fun launchBindingWork(label: String, block: () -> Unit) {
        launchWork(label) {
            val session = try {
                PrivilegedSession(waiting = {
                    LogBus.event("root", "batch-session", Transition.DEGRADED, reason = "WAITING_FOR_APPROVAL")
                    _state.update { state -> state.copy(busy = "Waiting for approval — allow Cyclone Cloak in Magisk…",
                        bindingFlow = state.bindingFlow?.copy(note = "Waiting for approval. Allow Cyclone Cloak in Magisk for this Android profile.")) }
                }, resumed = {
                    LogBus.event("root", "batch-session", Transition.COMPLETED, reason = "SESSION_RESUMED")
                    _state.update { it.copy(busy = label, bindingFlow = it.bindingFlow?.copy(note = null)) }
                })
            } catch (_: java.io.IOException) { null }
            LogBus.event("root", "batch-preflight", Transition.STARTED)
            val result = if (session == null) RootDoctorResult(RootDoctorCode.ROOT_UNAVAILABLE)
                else RootSessions.batch(session) {
                    LogBus.event("root", "batch-preflight", Transition.COMPLETED, reason = "PRIVILEGE_GRANTED")
                    _state.update { it.copy(busy = label, bindingFlow = it.bindingFlow?.copy(note = null)) }
                    block()
                }
            if (!result.published) {
                LogBus.event("root", "batch-preflight", Transition.FAILED, reason = result.code.name)
                val current = _state.value.bindingFlow
                _state.update { it.copy(rootDoctor = result, bindingFlow = current?.copy(step = BindingStep.RESULT,
                    running = false, note = null, outcome = BindingOutcome(false, BindingFlow.saved(current, CloakBindingStore.all(app)),
                        "No binding work started. ${result.title}: ${result.message}"))) }
                say("No binding work started. ${result.title}: ${result.message}")
            }
            RootSessions.profileHint(InstallPlacement.myUser())?.let { hint ->
                _state.update { state -> state.copy(bindingFlow = state.bindingFlow?.let { draft ->
                    draft.copy(outcome = draft.outcome?.let { it.copy(message = "${it.message}\n$hint") })
                }) }
                say(hint)
            }
        }
    }

    private fun saveBindingRecords(records: List<CloakBinding>, expected: List<CloakBinding>? = null) {
        CloakBindingStore.update(app) { current ->
            require(expected == null || current == expected) { "Bindings changed. Return to review before saving." }
            BindingFlow.applyRecords(current, records)
        }
    }

    /** Loads identity [cloakId] onto every app of [profile]; apps Main manages stay Main's. Returns how many. */
    private fun bindLocally(profile: CycloneProfile, cloakId: String): Int {
        val userId = profile.androidUserId ?: return 0
        val managedByMain = CloakBindingStore.all(app)
            .filter { it.profileId == profile.id && it.origin == CloakBinding.ORIGIN_MAIN }
            .map { it.packageName }.toSet()
        val packages = profile.packages.orEmpty().filter { pkg ->
            pkg !in managedByMain && runCatching { CloakBindingStore.validate(profile.id, userId, pkg) }.isSuccess
        }
        val now = System.currentTimeMillis()
        saveBindingRecords(packages.map { pkg -> CloakBinding(profile.id, userId, pkg, cloakId, 0, true, now, "pending") })
        return packages.size
    }

    /** Publishes for the module, then tells Cyclone (values and health). Runs inside [work]. */
    private fun publishAndSync(): RootDoctorResult {
        val result = CloakResolver.rebuildIndexDetailed(app)
        _state.update { it.copy(rootDoctor = result) }
        val report = CycloneBridge.sync(app, foreground = true)
        _state.update { it.copy(report = report) }
        return result
    }

    // ---- Root -------------------------------------------------------------------------------------------------------

    fun runRootDoctor() {
        _state.update { it.copy(rootDoctor = RootDoctorResult(RootDoctorCode.CHECKING)) }
        launchWork("Checking root…") {
            val result = CloakResolver.rebuildIndexDetailed(app, repairModule = true)
            _state.update { it.copy(rootDoctor = result) }
            _state.update { it.copy(report = CycloneBridge.sync(app, foreground = true)) }
        }
    }

    companion object {
        const val MAX_IMPORT_CHARS = 2_000_000

        /** Reads all of [reader], refusing anything longer than [limit] characters. */
        fun readBounded(reader: java.io.Reader, limit: Int = MAX_IMPORT_CHARS): String {
            val out = StringBuilder()
            val buffer = CharArray(64 * 1024)
            while (true) {
                val read = reader.read(buffer)
                if (read < 0) break
                out.append(buffer, 0, read)
                require(out.length <= limit) { "That file is too big to be a profile, phone or dump." }
            }
            return out.toString()
        }
    }
}
