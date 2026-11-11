package com.mfouad.pocketpad.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mfouad.pocketpad.R
import com.mfouad.pocketpad.hid.ProtocolMode
import com.mfouad.pocketpad.session.Phase
import com.mfouad.pocketpad.session.Problem

@Composable
fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
fun InfoCard(
    title: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors =
        if (isError) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            CardDefaults.cardColors()
        }
    Card(modifier = modifier.fillMaxWidth(), colors = colors) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeading(title)
            content()
        }
    }
}

@StringRes
fun Problem.titleRes(): Int =
    when (this) {
        Problem.BLUETOOTH_UNAVAILABLE -> R.string.problem_bluetooth_unavailable_title
        Problem.BLUETOOTH_DISABLED -> R.string.problem_bluetooth_disabled_title
        Problem.BLUETOOTH_TURNED_OFF_WHILE_ACTIVE -> R.string.problem_bluetooth_turned_off_title
        Problem.MISSING_PERMISSIONS -> R.string.problem_missing_permissions_title
        Problem.SERVICE_START_BLOCKED -> R.string.problem_service_blocked_title
        Problem.PROFILE_UNAVAILABLE -> R.string.problem_profile_unavailable_title
        Problem.REGISTRATION_REJECTED -> R.string.problem_registration_rejected_title
        Problem.REGISTRATION_STALLED -> R.string.problem_registration_stalled_title
        Problem.REGISTRATION_LOST -> R.string.problem_registration_lost_title
        Problem.CONNECTION_FAILED -> R.string.problem_connection_failed_title
        Problem.CONNECTION_TIMEOUT -> R.string.problem_connection_timeout_title
        Problem.CONNECTION_LOST -> R.string.problem_connection_lost_title
        Problem.HOST_UNPLUGGED -> R.string.problem_host_unplugged_title
    }

@StringRes
fun Problem.bodyRes(): Int =
    when (this) {
        Problem.BLUETOOTH_UNAVAILABLE -> R.string.problem_bluetooth_unavailable_body
        Problem.BLUETOOTH_DISABLED -> R.string.problem_bluetooth_disabled_body
        Problem.BLUETOOTH_TURNED_OFF_WHILE_ACTIVE -> R.string.problem_bluetooth_turned_off_body
        Problem.MISSING_PERMISSIONS -> R.string.problem_missing_permissions_body
        Problem.SERVICE_START_BLOCKED -> R.string.problem_service_blocked_body
        Problem.PROFILE_UNAVAILABLE -> R.string.problem_profile_unavailable_body
        Problem.REGISTRATION_REJECTED -> R.string.problem_registration_rejected_body
        Problem.REGISTRATION_STALLED -> R.string.problem_registration_stalled_body
        Problem.REGISTRATION_LOST -> R.string.problem_registration_lost_body
        Problem.CONNECTION_FAILED -> R.string.problem_connection_failed_body
        Problem.CONNECTION_TIMEOUT -> R.string.problem_connection_timeout_body
        Problem.CONNECTION_LOST -> R.string.problem_connection_lost_body
        Problem.HOST_UNPLUGGED -> R.string.problem_host_unplugged_body
    }

@Composable
fun phaseText(
    phase: Phase,
    hostName: String,
): String =
    when (phase) {
        Phase.IDLE -> stringResource(R.string.status_idle)
        Phase.ACQUIRING_PROXY -> stringResource(R.string.status_acquiring)
        Phase.REGISTERING -> stringResource(R.string.status_registering)
        Phase.REGISTERED -> stringResource(R.string.status_registered)
        Phase.CONNECTING -> stringResource(R.string.status_connecting, hostName)
        Phase.CONNECTED -> stringResource(R.string.status_connected, hostName)
        Phase.DISCONNECTING -> stringResource(R.string.status_disconnecting)
        Phase.STOPPING -> stringResource(R.string.status_stopping)
    }

@StringRes
fun ProtocolMode.labelRes(): Int =
    when (this) {
        ProtocolMode.REPORT -> R.string.protocol_mode_report
        ProtocolMode.BOOT -> R.string.protocol_mode_boot
    }

/** Name of the session's host for status text: the bonded name, or a label for the simulator. */
@Composable
fun hostDisplayName(
    state: com.mfouad.pocketpad.session.SessionState,
    hosts: List<com.mfouad.pocketpad.bluetooth.BondedHost>,
): String =
    when {
        state.simulated -> stringResource(R.string.simulated_computer_name)
        else -> hosts.firstOrNull { it.address == state.hostAddress }?.name ?: state.hostAddress.orEmpty()
    }
