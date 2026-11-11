package com.mfouad.pocketpad.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.mfouad.pocketpad.DebugFeatures
import com.mfouad.pocketpad.data.HostStore
import com.mfouad.pocketpad.diagnostics.DiagnosticsLog
import com.mfouad.pocketpad.diagnostics.DiagnosticsLog.Category
import com.mfouad.pocketpad.diagnostics.LoggingOutputSink
import com.mfouad.pocketpad.engine.Engine
import com.mfouad.pocketpad.gesture.Cancellable
import com.mfouad.pocketpad.hid.HidDescriptor
import com.mfouad.pocketpad.hid.HidReply
import com.mfouad.pocketpad.hid.HidReport
import com.mfouad.pocketpad.hid.HidRequestHandler
import com.mfouad.pocketpad.hid.OutputSink
import com.mfouad.pocketpad.hid.ProtocolMode
import com.mfouad.pocketpad.hid.ReportSender
import com.mfouad.pocketpad.input.InputPipeline
import com.mfouad.pocketpad.session.LinkState
import com.mfouad.pocketpad.session.Phase
import com.mfouad.pocketpad.session.Problem
import com.mfouad.pocketpad.session.Reconnector
import com.mfouad.pocketpad.session.SessionEvent
import com.mfouad.pocketpad.session.SessionMachine
import com.mfouad.pocketpad.session.SessionState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** A computer bonded with this phone, as Android reports it. */
data class BondedHost(
    val address: String,
    val name: String,
    val isComputer: Boolean,
)

/**
 * Thin wrapper around [BluetoothHidDevice]: proxy acquisition, registration, connection,
 * callbacks and report delivery. Public methods may be called from any thread; all work runs on
 * the engine thread. See docs/ARCHITECTURE.md sections 4-6.
 *
 * Every Bluetooth call is reached only after [BluetoothEnv.hasConnectPermission] passed in
 * [startOnEngine], or on callbacks that only arrive after that point, hence the suppression.
 */
@SuppressLint("MissingPermission")
class HidSessionController(
    private val context: Context,
    val log: DiagnosticsLog,
    private val hostStore: HostStore,
) {
    private val env = BluetoothEnv(context)
    val engine = Engine()
    private val scheduler = engine.scheduler

    private val stateFlow = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = stateFlow.asStateFlow()

    private val hostsFlow = MutableStateFlow<List<BondedHost>>(emptyList())
    val bondedHosts: StateFlow<List<BondedHost>> = hostsFlow.asStateFlow()

    private val endedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits whenever the session returns to idle (stopped, failed, or blocked on start). */
    val sessionEnded: SharedFlow<Unit> = endedFlow.asSharedFlow()

    private var proxy: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var simulatedSink: OutputSink? = null
    private var timeout: Cancellable? = null
    private val broadcasts =
        BluetoothBroadcasts(
            context,
            onAdapterTurningOff = {
                engine.post {
                    log.log(Category.BLUETOOTH) { "Bluetooth turning off" }
                    dispatch(SessionEvent.AdapterOff)
                }
            },
            onBondChanged = { address, bonded -> engine.post { onBondChanged(address, bonded) } },
        )

    /** The ordered report path. Engine thread only. */
    val sender = ReportSender(LoggingOutputSink(OutputSink(::deliver), log), scheduler)
    private val requests = HidRequestHandler(sender::currentInputReport)

    /** Touch input, gestures, mapping and settings. */
    val input = InputPipeline(engine, sender, log, context.resources.displayMetrics.density)

    private val reconnectFlow = MutableStateFlow<Reconnector.Status?>(null)

    /** Non-null while automatically reconnecting to the last host. */
    val reconnect: StateFlow<Reconnector.Status?> = reconnectFlow.asStateFlow()

    private val reconnector =
        Reconnector(
            scheduler,
            connect = { address ->
                log.log(Category.SESSION) { "reconnect attempt to $address" }
                connectOnEngine(address)
            },
            onStatus = { reconnectFlow.value = it },
        )

    // --- public API (any thread) ---------------------------------------------------------

    fun start() = engine.post { startOnEngine() }

    /** Debug builds only: run the session against a recorder instead of Bluetooth. */
    fun startSimulated() = engine.post { startSimulatedOnEngine() }

    fun stop() = engine.post { stopOnEngine() }

    fun connect(address: String) =
        engine.post {
            reconnector.cancel()
            connectOnEngine(address)
        }

    fun disconnect() =
        engine.post {
            reconnector.cancel()
            disconnectOnEngine()
        }

    fun refreshHosts() = engine.post { refreshHostsOnEngine() }

    /** Records a problem found outside the controller, such as the service failing to start. */
    fun reportBlocked(
        problem: Problem,
        detail: String?,
    ) = engine.post { dispatch(SessionEvent.Blocked(problem, detail)) }

    /** Runs [block] on the engine thread with the sender. Used by test controls and the trackpad. */
    fun withSender(block: (ReportSender) -> Unit) = engine.post { block(sender) }

    /** Stops automatic reconnection. */
    fun cancelReconnect() = engine.post { reconnector.cancel() }

    val ledState: Int get() = requests.ledState

    /** The debug recorder, when the simulated host is active. */
    val simulatedRecorder: OutputSink? get() = simulatedSink

    // --- state machine ---------------------------------------------------------------------

    private fun dispatch(e: SessionEvent) {
        val old = stateFlow.value
        val new = SessionMachine.reduce(old, e)
        log.log(Category.SESSION) { "${e.describe()} -> ${new.phase}${new.problem?.let { " ($it)" } ?: ""}" }
        stateFlow.value = new
        onTransition(old, new, e)
    }

    private fun onTransition(
        old: SessionState,
        new: SessionState,
        e: SessionEvent,
    ) {
        if (new.phase != old.phase) cancelTimeout()
        applyOutputSafety(old, new, e)
        recordHost(old, new, e)
        reconnector.onTransition(old, new)
        if (new.phase == Phase.IDLE && old.phase != Phase.IDLE && e !is SessionEvent.Stopped) cleanup()
        if (new.phase == Phase.IDLE) endedFlow.tryEmit(Unit)
    }

    /** Stuck-input safety: the sender's connection state follows the session exactly. */
    private fun applyOutputSafety(
        old: SessionState,
        new: SessionState,
        e: SessionEvent,
    ) {
        val wasConnected = old.phase == Phase.CONNECTED
        val isConnected = new.phase == Phase.CONNECTED
        when {
            // Neutral reports first on every new connection.
            !wasConnected && isConnected -> {
                sender.onConnected()
                input.onLinkChanged(true, new.hostAddress)
            }

            // Unexpected or requested: local held state is cleared; nothing is replayed later.
            wasConnected && !isConnected -> {
                sender.onDisconnected()
                input.onLinkChanged(false, null)
            }

            isConnected && e is SessionEvent.ProtocolChanged -> {
                sender.setProtocolMode(new.protocolMode)
            }
        }
    }

    private fun recordHost(
        old: SessionState,
        new: SessionState,
        e: SessionEvent,
    ) {
        if (new.simulated) return
        val address = new.hostAddress
        if (old.phase != Phase.CONNECTED && new.phase == Phase.CONNECTED && address != null) {
            hostStore.markHidPaired(address)
            hostStore.setLastHost(address)
        }
        if (e is SessionEvent.VirtualCableUnplugged) hostStore.markNeedsRepair(e.address)
    }

    // --- start ----------------------------------------------------------------------------

    private fun startOnEngine() {
        if (state.value.isActive) return
        val adapter = env.adapter
        val blocked =
            when {
                adapter == null -> Problem.BLUETOOTH_UNAVAILABLE
                !env.hasConnectPermission() -> Problem.MISSING_PERMISSIONS
                !adapter.isEnabled -> Problem.BLUETOOTH_DISABLED
                else -> null
            }
        if (blocked != null || adapter == null) {
            dispatch(SessionEvent.Blocked(blocked ?: Problem.BLUETOOTH_UNAVAILABLE))
            return
        }
        dispatch(SessionEvent.StartRequested())
        broadcasts.register()
        val requested = adapter.getProfileProxy(context, proxyListener, BluetoothProfile.HID_DEVICE)
        log.log(Category.BLUETOOTH) { "getProfileProxy(HID_DEVICE) returned $requested" }
        if (!requested) {
            dispatch(SessionEvent.ProxyFailed("getProfileProxy returned false"))
        } else {
            setTimeout(PROXY_TIMEOUT_NANOS) {
                if (state.value.phase ==
                    Phase.ACQUIRING_PROXY
                ) {
                    dispatch(SessionEvent.ProxyFailed("no onServiceConnected within 5 s"))
                }
            }
        }
    }

    private fun startSimulatedOnEngine() {
        val sink = DebugFeatures.simulatedSink() ?: return
        if (state.value.isActive) return
        simulatedSink = sink
        dispatch(SessionEvent.StartRequested(simulated = true))
        dispatch(SessionEvent.ProxyConnected)
        dispatch(SessionEvent.AppStatus(registered = true))
        dispatch(SessionEvent.ConnectRequested(SIMULATED_ADDRESS))
        dispatch(SessionEvent.LinkChanged(SIMULATED_ADDRESS, LinkState.CONNECTED))
    }

    private val proxyListener =
        object : BluetoothProfile.ServiceListener {
            // Delivered on the main looper; hop to the engine.
            override fun onServiceConnected(
                profile: Int,
                p: BluetoothProfile,
            ) = engine.post { onProxyConnected(p as BluetoothHidDevice) }

            override fun onServiceDisconnected(profile: Int) =
                engine.post {
                    log.log(Category.BLUETOOTH) { "onServiceDisconnected(HID_DEVICE)" }
                    proxy = null
                    dispatch(SessionEvent.ProxyDisconnected)
                }
        }

    private fun onProxyConnected(p: BluetoothHidDevice) {
        log.log(Category.BLUETOOTH) { "onServiceConnected(HID_DEVICE)" }
        if (state.value.phase != Phase.ACQUIRING_PROXY) {
            // Stopped while waiting; release the late proxy.
            env.adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, p)
            return
        }
        proxy = p
        dispatch(SessionEvent.ProxyConnected)
        val sdp =
            BluetoothHidDeviceAppSdpSettings(
                SDP_NAME,
                SDP_DESCRIPTION,
                SDP_PROVIDER,
                BluetoothHidDevice.SUBCLASS1_COMBO,
                HidDescriptor.bytes(),
            )
        val requested = p.registerApp(sdp, null, null, engine.executor, hidCallback)
        log.log(Category.BLUETOOTH) { "registerApp(${HidDescriptor.bytes().size}-byte descriptor) returned $requested" }
        if (!requested) {
            dispatch(SessionEvent.RegistrationRejected("registerApp returned false"))
        } else {
            setTimeout(REGISTER_TIMEOUT_NANOS) {
                if (state.value.phase == Phase.REGISTERING) {
                    dispatch(SessionEvent.RegistrationTimedOut)
                }
            }
        }
    }

    // --- HID callbacks (on the engine executor) ---------------------------------------------

    private val hidCallback =
        object : BluetoothHidDevice.Callback() {
            override fun onAppStatusChanged(
                pluggedDevice: BluetoothDevice?,
                registered: Boolean,
            ) {
                log.log(
                    Category.BLUETOOTH,
                ) { "onAppStatusChanged(registered=$registered, plugged=${pluggedDevice?.address})" }
                if (registered && pluggedDevice != null) hostStore.markHidPaired(pluggedDevice.address)
                dispatch(SessionEvent.AppStatus(registered))
                if (registered) refreshHostsOnEngine()
            }

            override fun onConnectionStateChanged(
                device: BluetoothDevice,
                state: Int,
            ) {
                val link = state.toLinkState()
                log.log(Category.BLUETOOTH) { "onConnectionStateChanged(${device.address}, $link)" }
                if (link == LinkState.CONNECTED || link == LinkState.CONNECTING) host = device
                dispatch(SessionEvent.LinkChanged(device.address, link))
                if (link == LinkState.DISCONNECTED && host?.address == device.address) {
                    host = null
                    if (stateFlow.value.phase == Phase.STOPPING) finishStop()
                }
            }

            override fun onGetReport(
                device: BluetoothDevice,
                type: Byte,
                id: Byte,
                bufferSize: Int,
            ) {
                val reply = requests.onGetReport(type.toInt(), id.toInt())
                log.log(
                    Category.BLUETOOTH,
                ) { "onGetReport(type=$type, id=$id, bufferSize=$bufferSize) -> ${reply.describe()}" }
                reply(device, reply)
            }

            override fun onSetReport(
                device: BluetoothDevice,
                type: Byte,
                id: Byte,
                data: ByteArray,
            ) {
                val reply = requests.onSetReport(type.toInt(), id.toInt(), data)
                log.log(
                    Category.BLUETOOTH,
                ) { "onSetReport(type=$type, id=$id, ${HidReport(0, data).toHex()}) -> ${reply.describe()}" }
                reply(device, reply)
            }

            override fun onSetProtocol(
                device: BluetoothDevice,
                protocol: Byte,
            ) {
                val mode =
                    if (protocol ==
                        BluetoothHidDevice.PROTOCOL_BOOT_MODE
                    ) {
                        ProtocolMode.BOOT
                    } else {
                        ProtocolMode.REPORT
                    }
                log.log(Category.BLUETOOTH) { "onSetProtocol($mode)" }
                dispatch(SessionEvent.ProtocolChanged(mode))
            }

            override fun onInterruptData(
                device: BluetoothDevice,
                reportId: Byte,
                data: ByteArray,
            ) {
                val used = requests.onInterruptData(reportId.toInt(), data)
                log.log(
                    Category.BLUETOOTH,
                ) { "onInterruptData(id=$reportId, ${HidReport(0, data).toHex()}) used=$used" }
            }

            override fun onVirtualCableUnplug(device: BluetoothDevice) {
                log.log(Category.BLUETOOTH) { "onVirtualCableUnplug(${device.address})" }
                dispatch(SessionEvent.VirtualCableUnplugged(device.address))
            }
        }

    private fun reply(
        device: BluetoothDevice,
        reply: HidReply,
    ) {
        val p = proxy ?: return
        val ok =
            when (reply) {
                is HidReply.Report -> {
                    p.replyReport(
                        device,
                        reply.type.toByte(),
                        reply.report.id.toByte(),
                        reply.report.payload,
                    )
                }

                is HidReply.Handshake -> {
                    p.reportError(device, reply.code.toByte())
                }
            }
        if (!ok) log.log(Category.BLUETOOTH) { "reply to host was not submitted" }
    }

    // --- connect / disconnect / stop ------------------------------------------------------

    private fun connectOnEngine(address: String) {
        val s = state.value
        val p = proxy
        val device = env.adapter?.getRemoteDevice(address)
        when {
            s.phase != Phase.REGISTERED -> {
                return
            }

            s.simulated -> {
                dispatch(SessionEvent.ConnectRequested(address))
                dispatch(SessionEvent.LinkChanged(address, LinkState.CONNECTED))
                return
            }

            p == null || device == null -> {
                return
            }
        }
        dispatch(SessionEvent.ConnectRequested(address))
        host = device
        val requested = p.connect(device)
        log.log(Category.BLUETOOTH) { "connect($address) returned $requested" }
        if (!requested) {
            dispatch(SessionEvent.ConnectRejected("connect returned false"))
        } else {
            setTimeout(CONNECT_TIMEOUT_NANOS) {
                if (state.value.phase == Phase.CONNECTING) {
                    p.disconnect(device)
                    dispatch(SessionEvent.ConnectTimeout)
                }
            }
        }
    }

    private fun disconnectOnEngine() {
        val s = state.value
        val address = s.hostAddress ?: return
        if (s.phase != Phase.CONNECTED && s.phase != Phase.CONNECTING) return
        // Intentional disconnect: neutral reports while still connected.
        if (s.phase == Phase.CONNECTED) sender.shutdown()
        dispatch(SessionEvent.DisconnectRequested)
        if (s.simulated) {
            dispatch(SessionEvent.LinkChanged(address, LinkState.DISCONNECTED))
            return
        }
        val device = host
        scheduler.schedule(NEUTRAL_FLUSH_NANOS) { device?.let { proxy?.disconnect(it) } }
        setTimeout(DISCONNECT_TIMEOUT_NANOS) {
            if (state.value.phase ==
                Phase.DISCONNECTING
            ) {
                dispatch(SessionEvent.LinkChanged(address, LinkState.DISCONNECTED))
            }
        }
    }

    private fun stopOnEngine() {
        val s = state.value
        if (!s.isActive || s.phase == Phase.STOPPING) return
        if (s.phase == Phase.CONNECTED) sender.shutdown()
        dispatch(SessionEvent.StopRequested)
        val device = host
        val p = proxy
        if (device != null && p != null && !s.simulated) {
            scheduler.schedule(NEUTRAL_FLUSH_NANOS) { p.disconnect(device) }
            setTimeout(STOP_TIMEOUT_NANOS) { finishStop() }
        } else {
            finishStop()
        }
    }

    private fun finishStop() {
        if (state.value.phase != Phase.STOPPING) return
        cleanup()
        dispatch(SessionEvent.Stopped)
    }

    /** Unregisters and releases everything. Safe to call more than once. */
    private fun cleanup() {
        cancelTimeout()
        input.onLinkChanged(false, null)
        reconnector.cancel()
        sender.onDisconnected()
        proxy?.let { p ->
            val unregistered = p.unregisterApp()
            log.log(Category.BLUETOOTH) { "unregisterApp() returned $unregistered" }
            env.adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, p)
        }
        proxy = null
        host = null
        simulatedSink = null
        broadcasts.unregister()
    }

    // --- report delivery --------------------------------------------------------------------

    private fun deliver(report: HidReport): Boolean {
        simulatedSink?.let { return it.send(report) }
        val p = proxy
        val d = host
        return p != null && d != null && p.sendReport(d, report.id, report.payload)
    }

    // --- bonded hosts and system broadcasts -------------------------------------------------

    private fun refreshHostsOnEngine() {
        val adapter = env.adapter
        hostsFlow.value =
            if (adapter == null || !env.hasConnectPermission()) {
                emptyList()
            } else {
                adapter.bondedDevices
                    .orEmpty()
                    .map { d ->
                        BondedHost(
                            address = d.address,
                            name = d.name ?: d.address,
                            isComputer = d.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.COMPUTER,
                        )
                    }.sortedWith(compareByDescending<BondedHost> { it.isComputer }.thenBy { it.name.lowercase() })
            }
    }

    private fun onBondChanged(
        address: String,
        bonded: Boolean,
    ) {
        log.log(Category.BLUETOOTH) { "bond ${if (bonded) "created" else "removed"}: $address" }
        val registered = state.value.phase in setOf(Phase.REGISTERED, Phase.CONNECTING, Phase.CONNECTED)
        if (bonded && registered) hostStore.markHidPaired(address)
        if (!bonded) hostStore.forget(address)
        refreshHostsOnEngine()
    }

    // --- helpers ----------------------------------------------------------------------------

    private fun setTimeout(
        nanos: Long,
        block: () -> Unit,
    ) {
        cancelTimeout()
        timeout = scheduler.schedule(nanos, block)
    }

    private fun cancelTimeout() {
        timeout?.cancel()
        timeout = null
    }

    companion object {
        const val SIMULATED_ADDRESS = "00:00:00:00:00:00"
        private const val SDP_NAME = "PocketPad"
        private const val SDP_DESCRIPTION = "Bluetooth mouse"
        private const val SDP_PROVIDER = "PocketPad"
        private const val PROXY_TIMEOUT_NANOS = 5_000_000_000L
        private const val REGISTER_TIMEOUT_NANOS = 5_000_000_000L
        private const val CONNECT_TIMEOUT_NANOS = 15_000_000_000L
        private const val DISCONNECT_TIMEOUT_NANOS = 3_000_000_000L
        private const val STOP_TIMEOUT_NANOS = 1_500_000_000L

        /** Gap between the last neutral reports and the disconnect request. */
        private const val NEUTRAL_FLUSH_NANOS = 60_000_000L
    }
}

private fun Int.toLinkState(): LinkState =
    when (this) {
        BluetoothProfile.STATE_CONNECTING -> LinkState.CONNECTING
        BluetoothProfile.STATE_CONNECTED -> LinkState.CONNECTED
        BluetoothProfile.STATE_DISCONNECTING -> LinkState.DISCONNECTING
        else -> LinkState.DISCONNECTED
    }

private fun HidReply.describe(): String =
    when (this) {
        is HidReply.Report -> "replyReport(R${report.id} ${report.toHex()})"
        is HidReply.Handshake -> "handshake($code)"
    }

/** Data classes and objects already print as `Name(field=value)` / `Name`. */
private fun SessionEvent.describe(): String = toString()
