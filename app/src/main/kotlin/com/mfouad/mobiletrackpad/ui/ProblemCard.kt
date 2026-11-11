package com.mfouad.mobiletrackpad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mfouad.mobiletrackpad.R
import com.mfouad.mobiletrackpad.session.Problem

/** A problem's title, explanation, technical detail and the action that usually fixes it. */
@Composable
fun ProblemCard(
    problem: Problem,
    detail: String?,
    actions: ProblemActions,
) {
    InfoCard(stringResource(problem.titleRes()), isError = true) {
        Text(stringResource(problem.bodyRes()))
        detail?.let { Text(stringResource(R.string.problem_detail, it), style = MaterialTheme.typography.bodySmall) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (problem) {
                Problem.BLUETOOTH_DISABLED, Problem.BLUETOOTH_TURNED_OFF_WHILE_ACTIVE -> {
                    TextButton(
                        onClick = actions.turnOnBluetooth,
                    ) { Text(stringResource(R.string.action_turn_on_bluetooth)) }
                }

                Problem.MISSING_PERMISSIONS -> {
                    TextButton(
                        onClick = actions.grantPermission,
                    ) { Text(stringResource(R.string.action_grant_permission)) }
                    TextButton(
                        onClick = actions.openAppSettings,
                    ) { Text(stringResource(R.string.action_open_app_settings)) }
                }

                Problem.HOST_UNPLUGGED, Problem.CONNECTION_FAILED -> {
                    TextButton(onClick = actions.openBluetoothSettings) {
                        Text(stringResource(R.string.action_open_bluetooth_settings))
                    }
                }

                Problem.BLUETOOTH_UNAVAILABLE -> {
                    Unit
                }

                else -> {
                    TextButton(onClick = actions.retry) { Text(stringResource(R.string.action_retry)) }
                }
            }
        }
    }
}
