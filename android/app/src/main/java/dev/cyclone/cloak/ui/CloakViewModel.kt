package dev.cyclone.cloak.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import dev.cyclone.cloak.data.PhoneStore
import dev.cyclone.cloak.forge.CloakFleet
import dev.cyclone.cloak.forge.CloakForge
import dev.cyclone.cloak.forge.Imports
import dev.cyclone.cloak.forge.PhoneCatalog
import dev.cyclone.cloak.forge.PhoneDraft
import dev.cyclone.cloak.forge.PhoneTemplate
import dev.cyclone.cloak.forge.ProfileValidator
import dev.cyclone.cloak.root.CloakResolver
import dev.cyclone.cloak.root.RootDoctorCode
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

/** An identity on this phone: its id and its full cloak profile. */
typealias Identity = Pair<String, JSONObject>

/** The phone builder, open on [draft]. [isNew]: saving adds a phone; otherwise it replaces the one with [draft]'s id. */
data class PhoneEditor(val draft: PhoneDraft, val isNew: Boolean, val note: String? = null) {
    val findings: List<ProfileValidator.Finding> get() = draft.findings()
    val canSave: Boolean get() = findings.none { it.isError }
}

/** An imported profile with findings, waiting for the owner's decision. */
data class ImportReview(val profile: JSONObject, val findings: List<ProfileValidator.Finding>) {
    val canSave: Boolean get() = findings.none { it.isError }
}

data class CloakState(
    val phones: List<PhoneTemplate> = emptyList(),
    val identities: List<Identity> = emptyList(),
    val bindings: List<CloakBinding> = emptyList(),
    val report: SyncReport? = null,
    val syncing: Boolean = false,
    val providerState: String = "not registered",
    val rootDoctor: RootDoctorResult = RootDoctorResult(RootDoctorCode.NOT_CHECKED),
    /** What is running right now ("Creating identity…"), or null. */
    val busy: String? = null,
    val opening: Boolean = false,
    val editor: PhoneEditor? = null,
    val review: ImportReview? = null,
) {
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
                    block()
                } catch (error: Exception) {
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
        _state.update {
            it.copy(phones = builtIn + own, identities = CloakStore.all(app), bindings = CloakBindingStore.all(app))
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
            _state.update { it.copy(report = report, syncing = false, providerState = provider) }
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

    // ---- Import and export ------------------------------------------------------------------------------------------

    fun import(uri: Uri) = launchWork("Reading file…") {
        val text = app.contentResolver.openInputStream(uri)?.bufferedReader()?.use(::readBounded)
            ?: throw IllegalArgumentException("Couldn't open that file.")
        require(text.isNotBlank()) { "That file is empty." }
        when (val parsed = Imports.parse(text, takenPhoneIds())) {
            is Imports.Parsed.Profile -> {
                if (parsed.findings.isEmpty()) saveIdentity(parsed.profile, "Imported")
                else _state.update { it.copy(review = ImportReview(parsed.profile, parsed.findings)) }
            }
            is Imports.Parsed.Fleet -> {
                parsed.profiles.forEach { CloakStore.save(app, it) }
                say("Imported ${parsed.profiles.size} identities.")
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

    fun saveReviewed() {
        val review = _state.value.review ?: return
        if (!review.canSave) return say("This profile has errors, so it can't be used as it is.")
        launchWork("Saving identity…") {
            saveIdentity(review.profile, "Imported")
            _state.update { it.copy(review = null) }
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

    fun dismissReview() = _state.update { it.copy(review = null) }

    fun exportPhone(phone: PhoneTemplate, uri: Uri) = launchWork("Exporting phone…") {
        write(uri, Imports.exportPhone(phone).toString(2))
        say("Exported ${phone.label}.")
    }

    fun exportFleet(uri: Uri) = launchWork("Exporting identities…") {
        val identities = CloakStore.all(app)
        require(identities.isNotEmpty()) { "No identities to export." }
        write(uri, CloakFleet.exportFleet(identities).toString(2))
        say("Exported ${identities.size} identities.")
    }

    private fun write(uri: Uri, text: String) {
        app.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw IllegalStateException("Couldn't write that file.")
    }

    // ---- Identities -------------------------------------------------------------------------------------------------

    private fun saveIdentity(profile: JSONObject, verb: String) {
        val name = profile.optString("name").trim()
        require(_state.value.identities.none { (id, p) -> id != profile.optString("id") && p.optString("name").equals(name, ignoreCase = true) }) {
            "An identity named \"$name\" already exists. Choose another name."
        }
        CloakStore.save(app, profile)
        say("$verb $name.")
    }

    fun createIdentity(name: String, phoneId: String) {
        val phone = _state.value.phone(phoneId) ?: return say("Choose a phone first.")
        launchWork("Creating identity…") {
            val profile = CloakForge.forgeNewProfile(name, phone)
            ProfileValidator.errors(profile).firstOrNull()?.let { throw IllegalArgumentException(it) }
            saveIdentity(profile, "Created")
        }
    }

    fun forgeFleet(phoneId: String, count: Int) {
        val phone = _state.value.phone(phoneId) ?: return say("Choose a phone first.")
        launchWork("Creating $count identities…") {
            val profiles = CloakFleet.forgeFleet(phone, count)
            profiles.forEach { CloakStore.save(app, it) }
            say("Created ${profiles.size} identities from ${phone.label}.")
        }
    }

    fun renameIdentity(id: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > 64) return say("Names are 1 to 64 characters.")
        launchWork("Renaming…") {
            val profile = CloakStore.find(app, id) ?: throw IllegalStateException("That identity is gone.")
            saveIdentity(profile.put("name", clean), "Renamed to")
            // Bound apps carry the name in the published state and in Cyclone: bring both up to date.
            if (_state.value.bindingsOf(id).isNotEmpty()) publishAndSync()
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

    fun bind(identityId: String, profile: CycloneProfile) {
        val state = _state.value
        when {
            !state.canBind -> return say("Approve Cyclone Cloak's profile settings in Cyclone → Settings → Connectors first.")
            !state.bindableHere(profile) -> return say(
                if (profile.bindable) "Bind ${profile.label} from Cyclone Cloak in Main, or from Cloak inside ${profile.label}."
                else "${profile.label} isn't ready in Cyclone yet.",
            )
        }
        launchWork("Binding…") {
            val count = bindLocally(profile, identityId)
            if (count == 0) return@launchWork say("Cyclone shared no apps for ${profile.label}.")
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
        launchWork("Binding ${plan.assignments.size} profiles…") {
            val count = plan.assignments.sumOf { (profile, id) -> bindLocally(profile, id) }
            val result = publishAndSync()
            val note = if (result.published) "" else " ${result.title}: ${result.message}"
            say("Bound $count apps across ${plan.assignments.size} profiles.$note")
        }
    }

    fun toggleBinding(binding: CloakBinding) {
        if (binding.origin == CloakBinding.ORIGIN_MAIN) return say("This binding is managed by Cyclone Cloak in Main.")
        launchWork(if (binding.enabled) "Turning off…" else "Turning on…") {
            CloakBindingStore.upsert(app, binding.copy(enabled = !binding.enabled, updatedAt = System.currentTimeMillis()))
            publishAndSync()
        }
    }

    fun removeBinding(binding: CloakBinding) {
        if (binding.origin == CloakBinding.ORIGIN_MAIN) return say("This binding is managed by Cyclone Cloak in Main.")
        launchWork("Removing binding…") {
            CloakBindingStore.remove(app, binding.profileId, binding.androidUserId, binding.packageName)
            publishAndSync()
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
        CloakBindingStore.update(app) { current ->
            packages.fold(current) { list, pkg ->
                CloakBindingStore.upsertIn(list, CloakBinding(profile.id, userId, pkg, cloakId, 0, true, now, "pending"))
            }
        }
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
