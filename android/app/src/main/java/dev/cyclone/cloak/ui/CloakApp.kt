package dev.cyclone.cloak.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val CloakDestination.icon: ImageVector get() = when (this) {
    CloakDestination.HOME -> Icons.Rounded.Home
    CloakDestination.IDENTITIES -> Icons.Rounded.Badge
    CloakDestination.PROFILES -> Icons.Rounded.Layers
    CloakDestination.HEALTH -> Icons.Rounded.HealthAndSafety
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloakApp(vm: CloakViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(CloakDestination.HOME.ordinal) }
    // A Home action that opens a screen leaves its request here until that screen has acted on it.
    var quick by rememberSaveable { mutableStateOf<QuickAction?>(null) }
    fun runQuick(action: QuickAction) {
        when (action) {
            QuickAction.NEW_PROFILE -> { quick = action; tab = CloakDestination.PROFILES.ordinal }
            QuickAction.NEW_IDENTITY -> { quick = action; tab = CloakDestination.IDENTITIES.ordinal }
            QuickAction.REPAIR -> vm.runRootDoctor()
            QuickAction.REFRESH -> vm.refresh()
        }
    }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    state.workshop?.let { workshop ->
        if (state.compatibleCreation) {
            CompatibleDeviceScreen(workshop, state.phones, state.registry, state.busy != null,
                vm::previewBatch, vm::saveApprovedBatch, vm::assignCreatedDevice, vm::closeBatchWorkshop)
            return
        }
        BatchIdentityWorkshop(workshop, state.phones, state.busy != null,
            vm::previewBatch, vm::regenerateBatch, vm::removeBatch, vm::saveApprovedBatch,
            vm::importBatchPlan, vm::exportBatchPlan, vm::closeBatchWorkshop)
        return
    }

    val editor = state.editor
    if (editor != null) {
        BackHandler { vm.closeEditor() }
        Column {
            ConnectionStatusChip(state.connection, vm::refresh)
            Box(Modifier.weight(1f)) {
                PhoneEditorScreen(editor, busy = state.busy != null, onChange = vm::updateDraft, onSave = vm::savePhone, onClose = vm::closeEditor)
            }
        }
        return
    }

    state.bindingFlow?.let { flow ->
        Column {
            ConnectionStatusChip(state.connection, vm::refresh)
            Box(Modifier.weight(1f)) {
                BindingFlowScreen(flow, BindingInputs(state.registry + BindingFlow.spaceProfiles(state.spaces), state.identities, state.bindings, state.canBind, state.ownProfileId),
            state.phones, state.busy != null || state.syncing,
            vm::chooseBindingProfile, vm::chooseBindingApps, vm::chooseBindingIdentity, vm::generateBindingProfile,
            vm::nextBindingStep, vm::backBindingStep, vm::acknowledgeBinding, vm::confirmBindingFlow,
            vm::retryBindingFlow, vm::refresh, vm::closeBindingFlow)
            }
        }
        return
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                    Text("C", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text("Cloak", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("by Cyclone", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    },
                    actions = {
                        Box(Modifier.widthIn(max = 170.dp)) { ConnectionStatusChip(state.connection, vm::refresh) }
                    },
                )
                if (state.busy != null || state.syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xED0B1038), tonalElevation = 0.dp) {
                CloakDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item.ordinal,
                        onClick = { tab = item.ordinal },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            if (CloakDestination.entries[tab] != CloakDestination.HOME) BackHandler { tab = CloakDestination.HOME.ordinal }
            when (CloakDestination.entries[tab]) {
                CloakDestination.HOME -> HomeScreen(state, onNavigate = { tab = it.ordinal }, onQuick = ::runQuick)
                CloakDestination.IDENTITIES -> IdentitiesScreen(state, vm, quick = quick, onQuickHandled = { quick = null })
                CloakDestination.PROFILES -> ProfilesScreen(state, vm, quick = quick, onQuickHandled = { quick = null })
                CloakDestination.HEALTH -> HealthScreen(state, vm)
            }
        }
    }

    state.backupReview?.let { review ->
        var accepted by remember(review) { androidx.compose.runtime.mutableStateOf(false) }
        AlertDialog(onDismissRequest = vm::dismissBackupReview,
            title = { Text("Review library import") }, text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${review.profiles.size} profiles will be added atomically. Existing profiles and bindings will remain.")
                    review.profiles.forEach { Text(it.optString("name")) }
                    FindingsList(review.findings)
                    state.backupMessage?.let { Text(it) }
                    if (review.findings.any { it.severity == dev.cyclone.cloak.forge.ProfileValidator.WARNING }) {
                        Row { androidx.compose.material3.Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                            Text("I reviewed the duplicate values and warnings.") }
                    }
                }
            }, confirmButton = {
                TextButton(onClick = { vm.confirmBackupImport(accepted) }, enabled = state.busy == null && review.canSave &&
                    (accepted || review.findings.none { it.severity == dev.cyclone.cloak.forge.ProfileValidator.WARNING })) { Text("Confirm import") }
            }, dismissButton = { TextButton(onClick = vm::dismissBackupReview, enabled = state.busy == null) { Text("Cancel") } })
    }

    state.review?.let { review ->
        AlertDialog(
            onDismissRequest = vm::dismissReview,
            title = { Text(if (review.canSave) "Check this profile" else "This profile doesn't hold together") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(review.profile.optString("name").ifBlank { "Unnamed profile" }, fontWeight = FontWeight.SemiBold)
                    FindingsList(review.findings)
                    review.findings.filter { it.code == "DUPLICATE_IDENTIFIER" }.forEach { finding ->
                        TextButton(onClick = { finding.field?.let(vm::regenerateReviewed) }, enabled = state.busy == null) {
                            Text("Regenerate ${finding.field?.removePrefix("identifiers.")}")
                        }
                    }
                    if (review.remaining.isNotEmpty()) Text("${review.remaining.size} profiles remain in this batch. Cancel leaves them unsaved.")
                }
            },
            confirmButton = {
                if (review.canSave) TextButton(onClick = vm::saveReviewed, enabled = state.busy == null) { Text(if (review.findings.isEmpty()) "Save" else "Save anyway") }
                else TextButton(onClick = vm::fixReviewedInBuilder) { Text("Fix in builder") }
            },
            dismissButton = { TextButton(onClick = vm::dismissReview) { Text("Cancel") } },
        )
    }
}
