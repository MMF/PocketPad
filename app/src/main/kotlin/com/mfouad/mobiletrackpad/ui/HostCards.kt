package com.mfouad.mobiletrackpad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mfouad.mobiletrackpad.AppGraph
import com.mfouad.mobiletrackpad.R
import com.mfouad.mobiletrackpad.bluetooth.BondedHost
import com.mfouad.mobiletrackpad.data.HostRecords
import com.mfouad.mobiletrackpad.hid.HostProfile
import com.mfouad.mobiletrackpad.hid.HostProfiles
import com.mfouad.mobiletrackpad.session.Phase
import com.mfouad.mobiletrackpad.session.SessionState

private enum class HostStatus(
    val label: Int,
) {
    CONNECTED(R.string.chip_connected),
    CONNECTING(R.string.chip_connecting),
    READY(R.string.chip_ready),
    CHECK_PAIRING(R.string.chip_check_pairing),
    REPAIR(R.string.chip_repair),
    OTHER(R.string.chip_other_device),
}

private fun statusOf(
    host: BondedHost,
    state: SessionState,
    records: HostRecords,
): HostStatus =
    when {
        state.hostAddress == host.address && state.phase == Phase.CONNECTED -> HostStatus.CONNECTED
        state.hostAddress == host.address && state.phase == Phase.CONNECTING -> HostStatus.CONNECTING
        host.address in records.needsRepair -> HostStatus.REPAIR
        host.address in records.hidPaired -> HostStatus.READY
        host.isComputer -> HostStatus.CHECK_PAIRING
        else -> HostStatus.OTHER
    }

/** Paired computers first, then other devices; each as a card with status and actions. */
@Composable
fun HostCards(
    state: SessionState,
    hosts: List<BondedHost>,
    records: HostRecords,
    profiles: List<HostProfile>,
    hasPermission: Boolean,
    actions: HostActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeading(stringResource(R.string.computers_heading), Modifier.weight(1f))
            if (hasPermission) TextButton(onClick = actions.refresh) { Text(stringResource(R.string.action_refresh)) }
        }
        when {
            !hasPermission -> Text(stringResource(R.string.hosts_need_permission))
            hosts.isEmpty() -> Text(stringResource(R.string.hosts_empty))
            else -> hosts.forEach { HostCard(it, state, records, profiles, actions) }
        }
    }
}

@Composable
private fun HostCard(
    host: BondedHost,
    state: SessionState,
    records: HostRecords,
    profiles: List<HostProfile>,
    actions: HostActions,
) {
    val status = statusOf(host, state, records)
    val hint =
        when (status) {
            HostStatus.REPAIR -> R.string.host_flag_needs_repair
            HostStatus.CHECK_PAIRING -> R.string.host_flag_maybe_repair
            HostStatus.OTHER -> R.string.host_flag_not_computer
            else -> null
        }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(host.name, style = MaterialTheme.typography.titleMedium)
                    if (records.lastHost == host.address) {
                        Text(stringResource(R.string.host_last), style = MaterialTheme.typography.labelSmall)
                    }
                }
                StatusChip(status)
            }
            hint?.let {
                Text(
                    stringResource(it),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (status ==
                            HostStatus.REPAIR
                        ) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            ProfileChooser(
                current = records.profiles[host.address] ?: HostProfiles.CUSTOM,
                profiles = profiles,
                onChoose = { id -> AppGraph.hostStore.setProfile(host.address, id) },
            )
            when {
                status == HostStatus.CONNECTED || status == HostStatus.CONNECTING -> {
                    OutlinedButton(onClick = actions.disconnect) { Text(stringResource(R.string.action_disconnect)) }
                }

                state.phase == Phase.REGISTERED && !state.simulated -> {
                    Button(
                        onClick = { actions.connect(host.address) },
                    ) { Text(stringResource(R.string.action_connect)) }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: HostStatus) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) =
        when (status) {
            HostStatus.CONNECTED, HostStatus.READY -> scheme.primaryContainer to scheme.onPrimaryContainer
            HostStatus.REPAIR -> scheme.errorContainer to scheme.onErrorContainer
            HostStatus.CONNECTING, HostStatus.CHECK_PAIRING -> scheme.tertiaryContainer to scheme.onTertiaryContainer
            HostStatus.OTHER -> scheme.surfaceVariant to scheme.onSurfaceVariant
        }
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Text(
            stringResource(status.label),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ProfileChooser(
    current: String,
    profiles: List<HostProfile>,
    onChoose: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val name = profiles.firstOrNull { it.id == current }?.name ?: current
    Box {
        TextButton(onClick = { open = true }) { Text(stringResource(R.string.profile_used_by, name)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            profiles.forEach { p ->
                DropdownMenuItem(text = { Text(p.name) }, onClick = {
                    onChoose(p.id)
                    open = false
                })
            }
        }
    }
}

/** Colour of the status dot for a session phase. */
fun phaseColor(state: SessionState): Color =
    when (state.phase) {
        Phase.CONNECTED -> StatusGreen
        Phase.IDLE -> if (state.problem != null) StatusRed else StatusGrey
        Phase.REGISTERED -> if (state.problem != null) StatusAmber else StatusBlue
        else -> StatusAmber
    }

private val StatusGreen = Color(0xFF2E7D32)
private val StatusRed = Color(0xFFC62828)
private val StatusAmber = Color(0xFFF9A825)
private val StatusBlue = Color(0xFF1565C0)
private val StatusGrey = Color(0xFF9E9E9E)
