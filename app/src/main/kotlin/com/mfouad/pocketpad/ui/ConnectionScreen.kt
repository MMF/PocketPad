package com.mfouad.pocketpad.ui

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mfouad.pocketpad.AppGraph
import com.mfouad.pocketpad.DebugFeatures
import com.mfouad.pocketpad.R
import com.mfouad.pocketpad.bluetooth.BluetoothEnv
import com.mfouad.pocketpad.bluetooth.BondedHost
import com.mfouad.pocketpad.data.HostRecords
import com.mfouad.pocketpad.hid.HostProfiles
import com.mfouad.pocketpad.hid.ProtocolMode
import com.mfouad.pocketpad.service.HidSessionService
import com.mfouad.pocketpad.session.Phase
import com.mfouad.pocketpad.session.Problem
import com.mfouad.pocketpad.session.SessionState

private const val DISCOVERABLE_SECONDS = 120
private const val STEP_ENABLE = 1
private const val STEP_PAIR = 2
private const val STEP_CONNECT = 3

/** Everything the status card can ask the session to do. */
private class MainActions(
    val enable: () -> Unit,
    val stop: () -> Unit,
    val makeVisible: () -> Unit,
    val connect: (String) -> Unit,
    val disconnect: () -> Unit,
    val openTrackpad: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    onOpenTrackpad: () -> Unit,
    onOpenTest: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val controller = AppGraph.controller
    val env = remember { BluetoothEnv(context) }
    val state by controller.state.collectAsStateWithLifecycle()
    val hosts by controller.bondedHosts.collectAsStateWithLifecycle()
    val records by AppGraph.hostStore.records.collectAsStateWithLifecycle(HostRecords())
    val reconnect by controller.reconnect.collectAsStateWithLifecycle()
    val stoppedByUser by AppGraph.stoppedByUserLastTime.collectAsStateWithLifecycle()
    val profiles by AppGraph.settingsStore.profiles.collectAsStateWithLifecycle(HostProfiles.defaults)

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

    val main =
        MainActions(
            enable = ::enable,
            stop = { stopSession(context, state) },
            makeVisible = {
                discoverable.launch(
                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                        .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_SECONDS),
                )
            },
            connect = controller::connect,
            disconnect = controller::disconnect,
            openTrackpad = onOpenTrackpad,
        )
    val problemActions =
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.connection_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                    IconButton(onClick = onOpenDiagnostics) {
                        Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.action_open_diagnostics))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (stoppedByUser) {
                InfoCard(stringResource(R.string.exit_notice_title), isError = true) {
                    Text(stringResource(R.string.exit_notice_body))
                    TextButton(
                        onClick = AppGraph::dismissStoppedByUser,
                    ) { Text(stringResource(R.string.action_dismiss)) }
                }
            }

            StatusCard(state, hosts, records, env.hasAdvertisePermission(), main)

            reconnect?.let { r ->
                val name = hosts.firstOrNull { it.address == r.address }?.name ?: r.address
                InfoCard(stringResource(R.string.status_heading)) {
                    Text(
                        stringResource(R.string.reconnect_status, name, r.attempt, r.maxAttempts),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(
                        onClick = controller::cancelReconnect,
                    ) { Text(stringResource(R.string.reconnect_cancel)) }
                }
            }
            state.problem?.takeIf { reconnect == null }?.let { ProblemCard(it, state.detail, problemActions) }

            if (!state.isActive && !env.hasConnectPermission()) {
                InfoCard(
                    stringResource(R.string.permission_heading),
                ) { Text(stringResource(R.string.permission_rationale)) }
            }
            if (!state.isConnected && !state.simulated) StepsCard(state, records, hosts)

            HostCards(
                state = state,
                hosts = hosts,
                records = records,
                profiles = profiles,
                hasPermission = env.hasConnectPermission(),
                actions = HostActions(controller::connect, controller::disconnect, controller::refreshHosts),
            )

            ToolsSection(state, onOpenTest, onSimulate = controller::startSimulated)
        }
    }
}

/** Status dot and text, a short hint, and the one action that matters most right now. */
@Composable
private fun StatusCard(
    state: SessionState,
    hosts: List<BondedHost>,
    records: HostRecords,
    canAdvertise: Boolean,
    main: MainActions,
) {
    val hostName = hostDisplayName(state, hosts)
    val lastHost = hosts.firstOrNull { it.address == records.lastHost }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(14.dp).background(phaseColor(state), CircleShape))
                Text(
                    phaseText(state.phase, hostName),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            StatusHint(state)
            if (state.phase in BUSY_PHASES) LinearProgressIndicator(Modifier.fillMaxWidth())
            PrimaryActions(state, lastHost, canAdvertise, main)
        }
    }
}

private val BUSY_PHASES =
    setOf(Phase.ACQUIRING_PROXY, Phase.REGISTERING, Phase.CONNECTING, Phase.DISCONNECTING, Phase.STOPPING)

@Composable
private fun StatusHint(state: SessionState) {
    val text =
        when {
            state.simulated -> R.string.status_simulated
            state.isConnected -> R.string.status_hint_connected
            state.phase == Phase.REGISTERED -> R.string.status_hint_registered
            !state.isActive -> R.string.status_hint_idle
            else -> null
        }
    text?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
    // Report mode is normal; only the limited boot mode is worth pointing out here.
    if (state.isConnected && state.protocolMode == ProtocolMode.BOOT) {
        Text(
            stringResource(R.string.protocol_mode_boot),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun PrimaryActions(
    state: SessionState,
    lastHost: BondedHost?,
    canAdvertise: Boolean,
    main: MainActions,
) {
    val full = Modifier.fillMaxWidth()
    when (state.phase) {
        Phase.IDLE -> {
            Button(onClick = main.enable, modifier = full) { Text(stringResource(R.string.action_enable)) }
        }

        Phase.CONNECTED -> {
            Button(onClick = main.openTrackpad, modifier = full) { Text(stringResource(R.string.action_open_trackpad)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = main.disconnect, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_disconnect))
                }
                OutlinedButton(
                    onClick = main.stop,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.action_stop)) }
            }
        }

        Phase.REGISTERED -> {
            if (lastHost != null && !state.simulated) {
                Button(onClick = { main.connect(lastHost.address) }, modifier = full) {
                    Text(stringResource(R.string.action_connect_to, lastHost.name))
                }
            }
            if (!state.simulated) {
                // With no computer to reconnect to, pairing is the next step, so it is the main button.
                val label = R.string.action_make_visible
                if (lastHost == null) {
                    Button(
                        onClick = main.makeVisible,
                        enabled = canAdvertise,
                        modifier = full,
                    ) { Text(stringResource(label)) }
                } else {
                    OutlinedButton(
                        onClick = main.makeVisible,
                        enabled = canAdvertise,
                        modifier = full,
                    ) { Text(stringResource(label)) }
                }
            }
            OutlinedButton(onClick = main.stop, modifier = full) { Text(stringResource(R.string.action_stop)) }
        }

        else -> {
            OutlinedButton(onClick = main.stop, modifier = full) { Text(stringResource(R.string.action_stop)) }
        }
    }
}

/** Enable, pair, connect: what is done, and how to do the rest. */
@Composable
private fun StepsCard(
    state: SessionState,
    records: HostRecords,
    hosts: List<BondedHost>,
) {
    var showPairing by rememberSaveable { mutableStateOf(false) }
    val enabled = state.isActive
    val paired = hosts.any { it.address in records.hidPaired }
    // Pairing steps show by themselves when they are the next thing to do.
    val pairingIsNext = enabled && !paired
    InfoCard(stringResource(R.string.steps_heading)) {
        Step(STEP_ENABLE, R.string.step_enable_title, R.string.step_enable_body, done = enabled)
        Step(STEP_PAIR, R.string.step_pair_title, R.string.step_pair_body, done = paired)
        if (pairingIsNext || showPairing) {
            Text(stringResource(R.string.pairing_steps), style = MaterialTheme.typography.bodySmall)
        }
        if (!pairingIsNext) {
            TextButton(onClick = { showPairing = !showPairing }) {
                Text(stringResource(if (showPairing) R.string.how_to_pair_hide else R.string.how_to_pair_show))
            }
        }
        Step(STEP_CONNECT, R.string.step_connect_title, R.string.step_connect_body, done = false)
    }
}

@Composable
private fun Step(
    number: Int,
    title: Int,
    body: Int,
    done: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (done) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.step_done),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Box(
                    Modifier.size(24.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(number.toString(), style = MaterialTheme.typography.labelMedium) }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(body), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ToolsSection(
    state: SessionState,
    onOpenTest: () -> Unit,
    onSimulate: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeading(stringResource(R.string.tools_heading))
        TextButton(
            onClick = onOpenTest,
            enabled = state.isConnected,
        ) { Text(stringResource(R.string.action_open_test)) }
        if (DebugFeatures.SIMULATED_HOST_AVAILABLE && !state.isActive) {
            TextButton(onClick = onSimulate) { Text(stringResource(R.string.action_simulate)) }
        }
    }
}

fun stopSession(
    context: Context,
    state: SessionState,
) {
    if (state.simulated) {
        AppGraph.controller.stop()
    } else {
        context.startService(
            Intent(context, HidSessionService::class.java).setAction("com.mfouad.pocketpad.action.STOP"),
        )
    }
}
