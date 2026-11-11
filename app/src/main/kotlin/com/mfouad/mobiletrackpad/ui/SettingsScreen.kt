package com.mfouad.mobiletrackpad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mfouad.mobiletrackpad.AppGraph
import com.mfouad.mobiletrackpad.R
import com.mfouad.mobiletrackpad.settings.Settings
import java.util.Locale

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenMappings: () -> Unit,
    onOpenSetupGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val store = AppGraph.settingsStore
    val s by store.settings.collectAsStateWithLifecycle(Settings())
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )

            SectionHeading(stringResource(R.string.settings_section_pointer))
            SpeedSlider(
                R.string.settings_pointer_speed,
                s.pointerSpeed,
            ) { v -> store.update { it.copy(pointerSpeed = v) } }
            LabeledSlider(
                label = stringResource(R.string.settings_acceleration),
                hint = stringResource(R.string.settings_acceleration_hint),
                value = s.acceleration,
                range = 0f..1f,
                valueText = String.format(currentLocale(), "%.0f%%", s.acceleration * PERCENT),
            ) { v -> store.update { it.copy(acceleration = v) } }

            HorizontalDivider()
            SectionHeading(stringResource(R.string.settings_section_scrolling))
            SpeedSlider(
                R.string.settings_scroll_speed,
                s.scrollSpeed,
            ) { v -> store.update { it.copy(scrollSpeed = v) } }
            SwitchRow(R.string.settings_reverse_scroll, R.string.settings_reverse_scroll_hint, s.reverseScroll) { v ->
                store.update { it.copy(reverseScroll = v) }
            }
            SwitchRow(R.string.settings_inertia, R.string.settings_inertia_hint, s.scrollInertia) { v ->
                store.update { it.copy(scrollInertia = v) }
            }

            HorizontalDivider()
            SectionHeading(stringResource(R.string.settings_section_clicking))
            SwitchRow(
                R.string.settings_tap_to_click,
                null,
                s.tapToClick,
            ) { v -> store.update { it.copy(tapToClick = v) } }
            SwitchRow(R.string.settings_drag_lock, R.string.settings_drag_lock_hint, s.dragLock) { v ->
                store.update {
                    it.copy(dragLock = v)
                }
            }
            SwitchRow(R.string.settings_haptics, null, s.haptics) { v -> store.update { it.copy(haptics = v) } }

            HorizontalDivider()
            SectionHeading(stringResource(R.string.settings_section_trackpad))
            SwitchRow(R.string.settings_on_screen_buttons, null, s.onScreenButtons) { v ->
                store.update { it.copy(onScreenButtons = v) }
            }
            SwitchRow(R.string.settings_left_handed, R.string.settings_left_handed_hint, s.leftHanded) { v ->
                store.update { it.copy(leftHanded = v) }
            }

            HorizontalDivider()
            SectionHeading(stringResource(R.string.settings_section_power))
            SwitchRow(R.string.settings_battery_saver, R.string.settings_battery_saver_hint, s.batterySaver) { v ->
                store.update { it.copy(batterySaver = v) }
            }

            HorizontalDivider()
            SectionHeading(stringResource(R.string.settings_section_more))
            OutlinedButton(onClick = onOpenMappings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_mappings))
            }
            OutlinedButton(onClick = onOpenSetupGuide, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_setup_guide))
            }
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_reset))
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.settings_reset_confirm_title)) },
            text = { Text(stringResource(R.string.settings_reset_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    store.resetSettings()
                    confirmReset = false
                }) { Text(stringResource(R.string.action_reset)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmReset = false },
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private const val PERCENT = 100f
private const val SPEED_STEPS = 10

@Composable
private fun SpeedSlider(
    label: Int,
    value: Float,
    onChange: (Float) -> Unit,
) {
    LabeledSlider(
        label = stringResource(label),
        hint = null,
        value = value,
        range = Settings.MIN_SPEED..Settings.MAX_SPEED,
        valueText =
            stringResource(
                R.string.settings_value_multiplier,
                String.format(currentLocale(), "%.2f", value),
            ),
        steps = SPEED_STEPS,
        onChange = onChange,
    )
}

/** Slider whose value is saved when the user lets go, not on every frame of a drag. */
@Composable
private fun LabeledSlider(
    label: String,
    hint: String?,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: value
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodyMedium)
        }
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let(onChange)
                dragging = null
            },
            valueRange = range,
            steps = steps,
            modifier =
                Modifier.semantics {
                    contentDescription = label
                    stateDescription = valueText
                },
        )
    }
}

@Composable
private fun SwitchRow(
    label: Int,
    hint: Int?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            hint?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
        }
        // The row handles toggling so the whole row is one accessible target.
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]
