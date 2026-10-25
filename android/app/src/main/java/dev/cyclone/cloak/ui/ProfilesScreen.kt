package dev.cyclone.cloak.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import dev.cyclone.cloak.diagnostics.BindingRuns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cyclone.cloak.cyclone.bindingKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfilesScreen(state: CloakState, vm: CloakViewModel) {
    val openTargets = state.openTargets
    val mainTarget = openTargets.firstOrNull { it.isOwner }
    var profileFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var packageQuery by rememberSaveable { mutableStateOf("") }
    var selectedProfile by rememberSaveable { mutableStateOf<String?>(null) }
    var historyProfile by rememberSaveable { mutableStateOf<String?>(null) }
    var playTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var integrityTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val integrityImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = integrityTarget
        integrityTarget = null
        if (uri != null && target != null) vm.importIntegrityVerdict(target, uri)
    }
    val playImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = playTarget
        playTarget = null
        if (uri != null && target != null) vm.importPlayEvidence(target, uri)
    }
    val context = LocalContext.current
    val journal = remember(context) { BindingRuns.journal(context) }
    val runs by journal.runs.collectAsState()
    @Suppress("DEPRECATION")
    val appVersion = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown" }
    val profiles = (state.registry + BindingFlow.spaceProfiles(state.spaces)).associateBy { it.id }
    val knownProfileIds = state.report?.snapshot?.registry?.map { it.id }?.toSet()
    state.playImportReview?.let { review -> PlayEvidenceReviewDialog(review, state.busy == null,
        state.playErrors[review.target.profileId], { profiles[it]?.label ?: it }, vm::savePlayEvidence, vm::dismissPlayEvidence) }
    val visibleBindings = filterBindings(state.bindings, profileFilter, packageQuery)
    val profileChoices = (profiles.keys + state.bindings.map { it.profileId }).sorted()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CloakHeroCard(
                title = if (state.ownProfileId == null) "Cyclone profiles" else "This Cyclone profile",
                body = state.ownProfileId?.let { id -> profiles[id]?.label?.let { "You're in $it" } }
                    ?: "Choose a space. Review its apps and device profile.",
            )
        }
        item {
            CloakActionButton("Bind a device profile", Icons.Rounded.Link, { vm.startBinding() }, Modifier.fillMaxWidth(),
                enabled = state.busy == null && !state.syncing)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CloakActionButton("Refresh", Icons.Rounded.Refresh, vm::refresh, Modifier.weight(1f), outlined = true)
                CloakActionButton("Assign fleet", Icons.Rounded.Layers, vm::bulkBind, Modifier.weight(1f), outlined = true)
            }
        }
        item {
            Text(
                "Assign fleet gives every ready profile without bindings the next unused identity.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { SpacesSection(vm, state) }
        mainTarget?.let { main ->
            item {
                CloakGroup {
                    CloakRow("Open ${main.label} in Cyclone", icon = Icons.AutoMirrored.Rounded.OpenInNew, onClick = { if (!state.opening) vm.openInCyclone(main) })
                }
            }
        }

        item { CloakSectionHeader("Profiles", state.registry.size) }
        if (state.registry.isEmpty()) {
            item {
                CloakEmptyState(
                    title = "No Cyclone profiles found",
                    body = "Check that Cyclone is installed and Cyclone Cloak is approved in Cyclone → Settings → Connectors, then refresh.",
                    icon = Icons.Rounded.CheckCircle,
                )
            }
        }
        if (state.registry.isNotEmpty()) item {
            CloakProfileGallery(
                state.registry.filter { it.state != "in_trash" }
                    .sortedWith(compareByDescending<dev.cyclone.cloak.cyclone.CycloneProfile> { it.id == state.ownProfileId }
                        .thenByDescending { it.isOwner }.thenBy { it.label.lowercase(java.util.Locale.ROOT) })
                    .map { profile -> CloakProfileFace(profile.id, profile.label,
                        when {
                            profile.id == state.ownProfileId -> "In use"
                            profile.state != "ready" -> "Setting up"
                            profile.androidUserId == null -> "Needs review"
                            else -> "${profile.packages?.size ?: 0} apps"
                        }, profile.id == state.ownProfileId, profile.state == "ready") },
                selectedProfile, { selectedProfile = it })
        }
        val trashed = state.registry.filter { it.state == "in_trash" }
        if (trashed.isNotEmpty()) item {
            CloakGroup { CloakDisclosure("Recently deleted", supporting = "${trashed.size} profiles · managed in Cyclone") {
                trashed.forEach { profile ->
                    CloakRow(profile.label, supporting = "Bindings retained for review", onClick = { selectedProfile = profile.id })
                }
            } }
        }

        historyProfile?.let { id -> item {
            CloakGroup { Column(Modifier.fillMaxWidth()) {
                TextButton(onClick = { historyProfile = null }) { Text("Close run history") }
                BindingRunsSection(state, vm::retryRun, id)
            } }
        } }

        item { CloakSectionHeader("Bound apps", state.bindings.size) }
        if (state.bindings.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BindingFilters(
                        profileFilter = profileFilter,
                        profileChoices = profileChoices,
                        profileLabel = { profiles[it]?.label ?: it },
                        onProfileChange = { profileFilter = it },
                        packageQuery = packageQuery,
                        onPackageChange = { packageQuery = it },
                        onClear = { profileFilter = null; packageQuery = "" },
                    )
                    Text("Showing ${visibleBindings.size} of ${state.bindings.size} bindings", style = MaterialTheme.typography.bodySmall)
                    if (knownProfileIds == null) {
                        Text("Profile availability not checked. Refresh to load Cyclone profiles.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (visibleBindings.isEmpty()) {
                        Text("No bindings match these filters.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (state.bindings.isEmpty()) {
            item {
                Text(
                    "Nothing is bound yet. Choose Bind a device profile, then select apps or All apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Duplicate records must remain visible for review instead of colliding on a LazyColumn key.
        itemsIndexed(visibleBindings, key = { index, binding -> "${binding.profileId}/${binding.packageName}/$index" }) { _, binding ->
            CloakBindingCard(
                binding = binding,
                profileLabel = profiles[binding.profileId]?.label ?: binding.profileId,
                issue = state.report?.issues?.get(bindingKey(binding.profileId, binding.packageName)),
                profileMissing = bindingProfileMissing(binding.profileId, knownProfileIds),
                identityLabel = state.identities.firstOrNull { it.first == binding.cloakProfileId }?.second?.optString("name")
                    ?.takeIf { it.isNotBlank() } ?: binding.cloakProfileId,
                onToggle = vm::toggleBinding,
                onRemove = vm::removeBinding,
            )
        }
    }
    profiles[selectedProfile]?.let { profile ->
        val managedElsewhere = state.ownProfileId != null && state.ownProfileId != profile.id
        ModalBottomSheet(onDismissRequest = { selectedProfile = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CloakProfileAvatar(profile.id, profile.label, profile.id == state.ownProfileId,
                        profile.state != "ready", 104)
                    Text(profile.label, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("${profile.packages?.size ?: 0} apps · ${profile.androidUserId?.let { "Android user $it" } ?: "No Android user yet"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.pills[profile.id]?.let { pill ->
                        CloakStatusPill(pill.label, when (pill.tone) {
                            dev.cyclone.cloak.cyclone.CloakPillTone.READY -> CloakStatusTone.READY
                            dev.cyclone.cloak.cyclone.CloakPillTone.ATTENTION -> CloakStatusTone.ERROR
                            else -> CloakStatusTone.PENDING
                        })
                        pill.reason?.takeIf(String::isNotBlank)?.let { CloakNotice(it, CloakStatusTone.PENDING) }
                    }
                }
                if (managedElsewhere) CloakNotice("Bindings managed from Cloak in Main.")
                if (!profile.isOwner) ProfileReadinessCard(profileReadiness(state, profile, appVersion, runs),
                    state.busy == null && !state.syncing && !managedElsewhere,
                    { vm.profileOperation(profile.id, ProfileAction.BIND_ALL) },
                    { vm.profileOperation(profile.id, ProfileAction.REPLACE) },
                    { historyProfile = profile.id; selectedProfile = null })
                CloakGroup {
                    CloakDisclosure("Play & Google evidence", supporting = "Local readiness and account-slot review") {
                        PlayReadinessCard(state.playReadiness[profile.id] ?: dev.cyclone.cloak.play.PlayReadinessCardState(),
                            state.identities.map { (id, body) -> id to body.optString("name", id) },
                            state.busy == null && !managedElsewhere && profile.state == "ready",
                            state.playErrors[profile.id], { profiles[it]?.label ?: it },
                            { vm.selectPlayIdentity(profile.id, it) },
                            { playTarget = profile.id; selectedProfile = null; playImporter.launch(arrayOf("application/json")) },
                            { integrityTarget = profile.id; selectedProfile = null; integrityImporter.launch(arrayOf("application/json")) })
                    }
                    if (openTargets.any { it.id == profile.id }) {
                        CloakGroupDivider()
                        CloakRow("Open in Cyclone", icon = Icons.AutoMirrored.Rounded.OpenInNew,
                            onClick = { if (!state.opening) vm.openInCyclone(profile) })
                    }
                }
                TextButton(onClick = { selectedProfile = null }, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }
}

@Composable
internal fun BindingFilters(
    profileFilter: String?,
    profileChoices: List<String>,
    profileLabel: (String) -> String,
    onProfileChange: (String?) -> Unit,
    packageQuery: String,
    onPackageChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CloakSelectField(
            label = "Filter bindings",
            value = profileFilter?.let { "Cyclone profile: ${profileLabel(it)}" } ?: "All Cyclone profiles",
            options = listOf<String?>(null) + profileChoices,
            optionLabel = { it?.let(profileLabel) ?: "All Cyclone profiles" },
            optionSupporting = { it },
            onSelect = onProfileChange,
        )
        OutlinedTextField(
            value = packageQuery,
            onValueChange = onPackageChange,
            label = { Text("App package filter") },
            placeholder = { Text("For example, com.example") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (profileFilter != null || packageQuery.isNotEmpty()) {
            TextButton(onClick = onClear) { Text("Clear filters") }
        }
    }
}
