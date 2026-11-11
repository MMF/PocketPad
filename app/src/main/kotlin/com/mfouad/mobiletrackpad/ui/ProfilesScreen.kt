package com.mfouad.mobiletrackpad.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mfouad.mobiletrackpad.AppGraph
import com.mfouad.mobiletrackpad.R
import com.mfouad.mobiletrackpad.hid.GestureSlot
import com.mfouad.mobiletrackpad.hid.HostProfile
import com.mfouad.mobiletrackpad.hid.HostProfiles

/** Profile list; selecting one opens its gesture mappings. */
@Composable
fun ProfilesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val store = AppGraph.settingsStore
    val profiles by store.profiles.collectAsStateWithLifecycle(HostProfiles.defaults)
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val profile = profiles.firstOrNull { it.id == selected }
    // System back leaves the editor for the list before leaving the screen.
    BackHandler(enabled = profile != null) { selected = null }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = { if (profile != null) selected = null else onBack() }) {
                Text(stringResource(R.string.action_back))
            }
            if (profile == null) {
                ProfileList(profiles) { selected = it }
            } else {
                ProfileEditor(
                    profile = profile,
                    onSave = store::saveProfile,
                    onReset = { store.resetProfile(profile.id) },
                )
            }
        }
    }
}

@Composable
private fun ProfileList(
    profiles: List<HostProfile>,
    onSelect: (String) -> Unit,
) {
    Text(
        stringResource(R.string.profiles_title),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() },
    )
    Text(stringResource(R.string.profiles_intro), style = MaterialTheme.typography.bodyMedium)
    profiles.forEach { p ->
        HorizontalDivider()
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onSelect(p.id) }
                .padding(vertical = 12.dp),
        ) {
            Text(p.name, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ProfileEditor(
    profile: HostProfile,
    onSave: (HostProfile) -> Unit,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    var editing by rememberSaveable { mutableStateOf<GestureSlot?>(null) }
    Text(
        profile.name,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() },
    )
    GestureSlot.entries.forEach { slot ->
        HorizontalDivider()
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { editing = slot }
                .padding(vertical = 10.dp),
        ) {
            Text(stringResource(ActionText.slotName(slot)), style = MaterialTheme.typography.bodyLarge)
            Text(
                ActionText.describe(context, profile.actionFor(slot), profile.id),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    OutlinedButton(
        onClick = onReset,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.profile_reset)) }

    editing?.let { slot ->
        ActionEditorDialog(
            title = stringResource(ActionText.slotName(slot)),
            initial = profile.actionFor(slot),
            allowSequence = true,
            profileId = profile.id,
            onSave = { action ->
                onSave(profile.with(slot, action))
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}
