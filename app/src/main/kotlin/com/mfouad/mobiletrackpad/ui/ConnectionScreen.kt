package com.mfouad.mobiletrackpad.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mfouad.mobiletrackpad.AppGraph
import com.mfouad.mobiletrackpad.DebugFeatures
import com.mfouad.mobiletrackpad.R
import com.mfouad.mobiletrackpad.bluetooth.BluetoothEnv
import com.mfouad.mobiletrackpad.bluetooth.BondedHost
import com.mfouad.mobiletrackpad.data.HostRecords
import com.mfouad.mobiletrackpad.service.HidSessionService
import com.mfouad.mobiletrackpad.session.Phase
import com.mfouad.mobiletrackpad.session.Problem
import com.mfouad.mobiletrackpad.session.SessionState

private const val DISCOVERABLE_SECONDS = 120

@Composable
fun ConnectionScreen(
    onOpenTrackpad: () -> Unit,
    onOpenTest: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val controller = AppGraph.controller
    val env = remember { BluetoothEnv(context) }
    val state by controller.state.collectAsStateWithLifecycle()
    val hosts by controller.bondedHosts.collectAsStateWithLifecycle()
    val records by AppGraph.hostStore.records.collectAsStateWithLifecycle(HostRecords())

    LaunchedEffect(state.phase) { controller.refreshHosts() }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (env.hasConnectPermission()) {
                HidSessionService.start(context)
            } else {
                controller.reportBlocked(Problem.MISSING_PERMISSIONS, null)
            }
            controller.refreshHosts()
        }
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val discoverable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    fun enable() {
        if (env.hasConnectPermission()) {
            HidSessionService.start(context)
        } else {
            permissionLauncher.launch(BluetoothEnv.runtimePermissions())
        }
    }

    val actions =
        ProblemActions(
            retry = ::enable,
            grantPermission = { permissionLauncher.launch(BluetoothEnv.runtimePermissions()) },
            turnOnBluetooth = {
                if (env.hasConnectPermission()) enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            },
            openBluetoothSettings = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
            openAppSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            },
        )

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.connection_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.connection_intro), style = MaterialTheme.typography.bodyLarge)

            StatusSection(state, hosts)
            state.problem?.let { ProblemCard(it, state.detail, actions) }

            if (!state.isActive) {
                Button(
                    onClick = ::enable,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_enable)) }
                if (DebugFeatures.SIMULATED_HOST_AVAILABLE) {
                    OutlinedButton(onClick = controller::startSimulated, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_simulate))
                    }
                }
                if (!env.hasConnectPermission()) {
                    InfoCard(
                        stringResource(R.string.permission_heading),
                    ) { Text(stringResource(R.string.permission_rationale)) }
                }
            } else {
                Button(onClick = { stopSession(context, state) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_stop))
                }
            }

            Button(onClick = onOpenTrackpad, enabled = state.isConnected, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_open_trackpad))
            }
            OutlinedButton(onClick = onOpenTest, enabled = state.isConnected, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_open_test))
            }
            OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_open_diagnostics))
            }

            if (state.phase == Phase.REGISTERED && !state.simulated) {
                InfoCard(stringResource(R.string.pairing_heading)) {
                    Text(stringResource(R.string.pairing_steps))
                    Button(
                        onClick = {
                            discoverable.launch(
                                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_SECONDS),
                            )
                        },
                        enabled = env.hasAdvertisePermission(),
                    ) { Text(stringResource(R.string.action_make_visible)) }
                }
            }

            HostList(
                state = state,
                hosts = hosts,
                records = records,
                hasPermission = env.hasConnectPermission(),
                onConnect = controller::connect,
                onDisconnect = controller::disconnect,
                onRefresh = controller::refreshHosts,
            )
        }
    }
}

fun stopSession(
    context: android.content.Context,
    state: SessionState,
) {
    if (state.simulated) {
        AppGraph.controller.stop()
    } else {
        context.startService(
            Intent(context, HidSessionService::class.java).setAction("com.mfouad.mobiletrackpad.action.STOP"),
        )
    }
}

@Composable
private fun StatusSection(
    state: SessionState,
    hosts: List<BondedHost>,
) {
    val hostName = hosts.firstOrNull { it.address == state.hostAddress }?.name ?: state.hostAddress.orEmpty()
    InfoCard(stringResource(R.string.status_heading)) {
        Text(
            phaseText(state.phase, hostName),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (state.isConnected) {
            Text(
                stringResource(state.protocolMode.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.simulated) Text(stringResource(R.string.status_simulated), style = MaterialTheme.typography.bodySmall)
    }
}

private class ProblemActions(
    val retry: () -> Unit,
    val grantPermission: () -> Unit,
    val turnOnBluetooth: () -> Unit,
    val openBluetoothSettings: () -> Unit,
    val openAppSettings: () -> Unit,
)

@Composable
private fun ProblemCard(
    problem: Problem,
    detail: String?,
    actions: ProblemActions,
) {
    InfoCard(stringResource(problem.titleRes()), isError = true) {
        Text(stringResource(problem.bodyRes()))
        detail?.let { Text(stringResource(R.string.problem_detail, it), style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    TextButton(
                        onClick = actions.openBluetoothSettings,
                    ) { Text(stringResource(R.string.action_open_bluetooth_settings)) }
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

@Composable
private fun HostList(
    state: SessionState,
    hosts: List<BondedHost>,
    records: HostRecords,
    hasPermission: Boolean,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading(stringResource(R.string.hosts_heading))
        when {
            !hasPermission -> Text(stringResource(R.string.hosts_need_permission))
            hosts.isEmpty() -> Text(stringResource(R.string.hosts_empty))
            else -> hosts.forEach { host -> HostRow(host, state, records, onConnect, onDisconnect) }
        }
        if (hasPermission) TextButton(onClick = onRefresh) { Text(stringResource(R.string.action_refresh)) }
    }
}

@Composable
private fun HostRow(
    host: BondedHost,
    state: SessionState,
    records: HostRecords,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
) {
    val flag =
        when {
            host.address in records.needsRepair -> R.string.host_flag_needs_repair
            host.address in records.hidPaired -> R.string.host_flag_ready
            host.isComputer -> R.string.host_flag_maybe_repair
            else -> R.string.host_flag_not_computer
        }
    val isThisHost = state.hostAddress == host.address
    HorizontalDivider()
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(host.name, style = MaterialTheme.typography.bodyLarge)
        Text(host.address, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(flag), style = MaterialTheme.typography.bodySmall)
        if (records.lastHost ==
            host.address
        ) {
            Text(stringResource(R.string.host_last), style = MaterialTheme.typography.labelSmall)
        }
        when {
            isThisHost && (state.phase == Phase.CONNECTED || state.phase == Phase.CONNECTING) -> {
                OutlinedButton(onClick = onDisconnect) { Text(stringResource(R.string.action_disconnect)) }
            }

            state.phase == Phase.REGISTERED && !state.simulated -> {
                Button(onClick = { onConnect(host.address) }) { Text(stringResource(R.string.action_connect)) }
            }
        }
    }
}
