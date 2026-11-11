package com.mfouad.pocketpad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mfouad.pocketpad.R
import com.mfouad.pocketpad.hid.Action
import com.mfouad.pocketpad.hid.ConsumerUsage
import com.mfouad.pocketpad.hid.MouseButton
import com.mfouad.pocketpad.hid.Modifier as HidModifier

private enum class ActionKind(
    val label: Int,
) {
    NONE(R.string.action_type_none),
    CLICK(R.string.action_type_click),
    KEYS(R.string.action_type_keys),
    MEDIA(R.string.action_type_media),
    MOD_WHEEL(R.string.action_type_modwheel),
    SEQUENCE(R.string.action_type_sequence),
}

private const val MAX_SEQUENCE_STEPS = 4

/** Edits one action. Sequences contain single actions only (no nested sequences). */
@Composable
fun ActionEditorDialog(
    title: String,
    initial: Action?,
    allowSequence: Boolean,
    onSave: (Action?) -> Unit,
    onDismiss: () -> Unit,
    profileId: String? = null,
) {
    val draft = remember { ActionDraft(initial) }
    var editingStep by remember { mutableStateOf<Int?>(null) }
    val result = draft.build()
    val valid = draft.kind == ActionKind.NONE || result != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.action_type_label), style = MaterialTheme.typography.titleSmall)
                ActionKind.entries.filter { allowSequence || it != ActionKind.SEQUENCE }.forEach { k ->
                    RadioRow(stringResource(k.label), draft.kind == k) { draft.kind = k }
                }
                HorizontalDivider()
                DraftFields(draft, profileId, onEditStep = { editingStep = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(result) }, enabled = valid) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    editingStep?.let { index ->
        ActionEditorDialog(
            title = stringResource(R.string.action_step, index + 1),
            initial = draft.steps.getOrNull(index),
            allowSequence = false,
            profileId = profileId,
            onSave = { a ->
                draft.setStep(index, a)
                editingStep = null
            },
            onDismiss = { editingStep = null },
        )
    }
}

@Composable
private fun SequenceSteps(
    steps: MutableList<Action>,
    profileId: String?,
    onEdit: (Int) -> Unit,
) {
    val c = LocalContext.current
    steps.forEachIndexed { i, step ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.action_step, i + 1) + ": " + ActionText.describe(c, step, profileId),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onEdit(i) }) { Text(stringResource(R.string.action_edit)) }
            TextButton(onClick = { steps.removeAt(i) }) { Text(stringResource(R.string.action_remove_step)) }
        }
    }
    if (steps.size < MAX_SEQUENCE_STEPS) {
        OutlinedButton(onClick = { onEdit(steps.size) }) { Text(stringResource(R.string.action_add_step)) }
    }
}

@Composable
private fun ModifierChecks(
    mods: MutableList<HidModifier>,
    profileId: String?,
) {
    Text(stringResource(R.string.action_modifiers), style = MaterialTheme.typography.titleSmall)
    ActionText.modifierChoices.forEach { m ->
        val checked = m in mods
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Checkbox) { if (it) mods.add(m) else mods.remove(m) }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Text(stringResource(ActionText.modifierName(m, profileId)), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun KeyPicker(
    key: Int?,
    onPick: (Int?) -> Unit,
) {
    val c = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Text(stringResource(R.string.action_key), style = MaterialTheme.typography.titleSmall)
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(key?.let { ActionText.keyName(c, it) } ?: stringResource(R.string.action_key_none))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.action_key_none)) }, onClick = {
                onPick(null)
                open = false
            })
            ActionText.keyChoices.forEach { k ->
                DropdownMenuItem(text = { Text(ActionText.keyName(c, k)) }, onClick = {
                    onPick(k)
                    open = false
                })
            }
        }
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun kindOf(a: Action?): ActionKind =
    when (a) {
        null -> ActionKind.NONE
        is Action.MouseClick -> ActionKind.CLICK
        is Action.KeyChord -> ActionKind.KEYS
        is Action.Consumer -> ActionKind.MEDIA
        is Action.ModifierWheel -> ActionKind.MOD_WHEEL
        is Action.Sequence -> ActionKind.SEQUENCE
    }

private fun modifiersOf(a: Action?): Set<HidModifier> =
    when (a) {
        is Action.KeyChord -> a.modifiers
        is Action.ModifierWheel -> a.modifiers
        else -> emptySet()
    }

/** Editing state for one action; [build] returns null while the input is incomplete. */
private class ActionDraft(
    initial: Action?,
) {
    var kind by mutableStateOf(kindOf(initial))
    var button by mutableStateOf((initial as? Action.MouseClick)?.button ?: MouseButton.BUTTON4)
    val mods = mutableStateListOf<HidModifier>().apply { addAll(modifiersOf(initial)) }
    var key by mutableStateOf((initial as? Action.KeyChord)?.keys?.firstOrNull())
    var media by mutableIntStateOf((initial as? Action.Consumer)?.usage ?: ConsumerUsage.PLAY_PAUSE)
    var wheelUp by mutableStateOf(((initial as? Action.ModifierWheel)?.wheel ?: 1) >= 0)
    val steps = mutableStateListOf<Action>().apply { addAll((initial as? Action.Sequence)?.steps.orEmpty()) }

    fun build(): Action? =
        when (kind) {
            ActionKind.NONE -> {
                null
            }

            ActionKind.CLICK -> {
                Action.MouseClick(button)
            }

            ActionKind.KEYS -> {
                keyChord()
            }

            ActionKind.MEDIA -> {
                Action.Consumer(media)
            }

            ActionKind.MOD_WHEEL -> {
                mods
                    .takeIf {
                        it.isNotEmpty()
                    }?.let { Action.ModifierWheel(it.toSet(), wheel = if (wheelUp) 1 else -1) }
            }

            ActionKind.SEQUENCE -> {
                sequence()
            }
        }

    fun setStep(
        index: Int,
        action: Action?,
    ) {
        when {
            index >= steps.size -> action?.let { steps.add(it) }
            action == null -> steps.removeAt(index)
            else -> steps[index] = action
        }
    }

    private fun keyChord(): Action? =
        if (mods.isEmpty() &&
            key == null
        ) {
            null
        } else {
            Action.KeyChord(mods.toSet(), listOfNotNull(key))
        }

    private fun sequence(): Action? =
        when (steps.size) {
            0 -> null
            1 -> steps.single()
            else -> Action.Sequence(steps.toList())
        }
}

@Composable
private fun DraftFields(
    draft: ActionDraft,
    profileId: String?,
    onEditStep: (Int) -> Unit,
) {
    when (draft.kind) {
        ActionKind.NONE -> {
            Unit
        }

        ActionKind.CLICK -> {
            MouseButton.entries.forEach { b ->
                RadioRow(
                    stringResource(ActionText.buttonName(b)),
                    draft.button == b,
                ) { draft.button = b }
            }
        }

        ActionKind.KEYS -> {
            ModifierChecks(draft.mods, profileId)
            KeyPicker(draft.key) { draft.key = it }
        }

        ActionKind.MEDIA -> {
            val c = LocalContext.current
            ActionText.mediaChoices.forEach { u ->
                RadioRow(ActionText.mediaName(c, u), draft.media == u) {
                    draft.media =
                        u
                }
            }
        }

        ActionKind.MOD_WHEEL -> {
            ModifierChecks(draft.mods, profileId)
            RadioRow(stringResource(R.string.action_wheel_up), draft.wheelUp) { draft.wheelUp = true }
            RadioRow(stringResource(R.string.action_wheel_down), !draft.wheelUp) { draft.wheelUp = false }
        }

        ActionKind.SEQUENCE -> {
            SequenceSteps(draft.steps, profileId, onEdit = onEditStep)
        }
    }
}
