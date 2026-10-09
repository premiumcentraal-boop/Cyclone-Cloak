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
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PhoneAndroid
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class Tab(val label: String, val icon: ImageVector) {
    PHONES("Phones", Icons.Rounded.PhoneAndroid),
    IDENTITIES("Identities", Icons.Rounded.Badge),
    PROFILES("Profiles", Icons.Rounded.Layers),
    HEALTH("Health", Icons.Rounded.HealthAndSafety),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloakApp(vm: CloakViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(Tab.PHONES.ordinal) }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val editor = state.editor
    if (editor != null) {
        BackHandler { vm.closeEditor() }
        PhoneEditorScreen(editor, busy = state.busy != null, onChange = vm::updateDraft, onSave = vm::savePhone, onClose = vm::closeEditor)
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    title = {
                        Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                    Text("C", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text("Cyclone Cloak", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(Tab.entries[tab].label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    actions = {
                        val headline = if (state.syncing) "Checking Cyclone" else state.report?.headline ?: "Checking Cyclone"
                        CloakStatusPill(headline, statusTone(headline))
                    },
                )
                if (state.busy != null || state.syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item.ordinal,
                        onClick = { tab = item.ordinal },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (Tab.entries[tab]) {
                Tab.PHONES -> PhonesScreen(state, vm)
                Tab.IDENTITIES -> IdentitiesScreen(state, vm)
                Tab.PROFILES -> ProfilesScreen(state, vm)
                Tab.HEALTH -> HealthScreen(state, vm)
            }
        }
    }

    state.review?.let { review ->
        AlertDialog(
            onDismissRequest = vm::dismissReview,
            title = { Text(if (review.canSave) "Check this profile" else "This profile doesn't hold together") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(review.profile.optString("name").ifBlank { "Unnamed profile" }, fontWeight = FontWeight.SemiBold)
                    FindingsList(review.findings)
                }
            },
            confirmButton = {
                if (review.canSave) TextButton(onClick = vm::saveReviewed) { Text("Save anyway") }
                else TextButton(onClick = vm::fixReviewedInBuilder) { Text("Fix in builder") }
            },
            dismissButton = { TextButton(onClick = vm::dismissReview) { Text("Cancel") } },
        )
    }
}
