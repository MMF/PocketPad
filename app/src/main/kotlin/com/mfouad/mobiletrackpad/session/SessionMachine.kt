package com.mfouad.mobiletrackpad.session

import com.mfouad.mobiletrackpad.hid.ProtocolMode

/** Where the Bluetooth HID session is. See docs/ARCHITECTURE.md section 4.1. */
enum class Phase {
    IDLE,
    ACQUIRING_PROXY,
    REGISTERING,
    REGISTERED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    STOPPING,
}

/** Why the session is not where the user wanted it. Each has its own message and action. */
enum class Problem {
    BLUETOOTH_UNAVAILABLE,
    BLUETOOTH_DISABLED,

    /**
     * Bluetooth was turned off while the HID app was registered. On the Samsung build tested
     * (docs/ARCHITECTURE.md section 15) this leaves the stack unable to register again until
     * the phone restarts.
     */
    BLUETOOTH_TURNED_OFF_WHILE_ACTIVE,
    MISSING_PERMISSIONS,
    SERVICE_START_BLOCKED,
    PROFILE_UNAVAILABLE,
    REGISTRATION_REJECTED,

    /** registerApp was accepted but the stack never reported the result. */
    REGISTRATION_STALLED,
    REGISTRATION_LOST,
    CONNECTION_FAILED,
    CONNECTION_TIMEOUT,
    CONNECTION_LOST,
    HOST_UNPLUGGED,
}

enum class LinkState { CONNECTING, CONNECTED, DISCONNECTING, DISCONNECTED }

data class SessionState(
    val phase: Phase = Phase.IDLE,
    /** Address of the host being connected, connected, or disconnected. */
    val hostAddress: String? = null,
    val protocolMode: ProtocolMode = ProtocolMode.REPORT,
    val problem: Problem? = null,
    /** Extra technical detail for diagnostics, not shown as the main message. */
    val detail: String? = null,
    val simulated: Boolean = false,
) {
    val isActive: Boolean get() = phase != Phase.IDLE
    val isConnected: Boolean get() = phase == Phase.CONNECTED
}

/** Inputs to the state machine. Everything except user requests comes from callbacks. */
sealed interface SessionEvent {
    /** Starting, registering and stopping the HID app. */
    sealed interface Lifecycle : SessionEvent

    /** The link to one host. */
    sealed interface Link : SessionEvent

    data class StartRequested(
        val simulated: Boolean = false,
    ) : Lifecycle

    /** A precondition failed before or while starting. */
    data class Blocked(
        val problem: Problem,
        val detail: String? = null,
    ) : Lifecycle

    data object ProxyConnected : Lifecycle

    data class ProxyFailed(
        val detail: String,
    ) : Lifecycle

    data object ProxyDisconnected : Lifecycle

    data class RegistrationRejected(
        val detail: String,
    ) : Lifecycle

    data class AppStatus(
        val registered: Boolean,
    ) : Lifecycle

    /** registerApp returned true but no onAppStatusChanged arrived in time. */
    data object RegistrationTimedOut : Lifecycle

    data object StopRequested : Lifecycle

    data object Stopped : Lifecycle

    data object AdapterOff : Lifecycle

    data class ConnectRequested(
        val address: String,
    ) : Link

    data class ConnectRejected(
        val detail: String,
    ) : Link

    data class LinkChanged(
        val address: String,
        val state: LinkState,
    ) : Link

    data object ConnectTimeout : Link

    data class ProtocolChanged(
        val mode: ProtocolMode,
    ) : Link

    data class VirtualCableUnplugged(
        val address: String,
    ) : Link

    data object DisconnectRequested : Link
}

/**
 * Pure transition function. Callback results are the only source of truth for registration and
 * connection; method return values only produce the "rejected" events.
 */
object SessionMachine {
    fun reduce(
        s: SessionState,
        e: SessionEvent,
    ): SessionState =
        when (e) {
            is SessionEvent.Lifecycle -> lifecycle(s, e)
            is SessionEvent.Link -> link(s, e)
        }

    private val LINKED = setOf(Phase.CONNECTING, Phase.CONNECTED, Phase.DISCONNECTING)
    private val REGISTERED_OR_LATER = setOf(Phase.REGISTERED) + LINKED

    private fun lifecycle(
        s: SessionState,
        e: SessionEvent.Lifecycle,
    ): SessionState =
        when (e) {
            is SessionEvent.StartRequested -> {
                if (s.isActive) {
                    s
                } else {
                    SessionState(
                        phase = Phase.ACQUIRING_PROXY,
                        simulated = e.simulated,
                    )
                }
            }

            is SessionEvent.Blocked -> {
                SessionState(problem = e.problem, detail = e.detail)
            }

            SessionEvent.ProxyConnected -> {
                s.ifPhase(Phase.ACQUIRING_PROXY) { it.copy(phase = Phase.REGISTERING) }
            }

            is SessionEvent.ProxyFailed -> {
                s.endedUnlessStopping(Problem.PROFILE_UNAVAILABLE, e.detail)
            }

            SessionEvent.ProxyDisconnected -> {
                s.endedUnlessStopping(
                    Problem.PROFILE_UNAVAILABLE,
                    "profile proxy disconnected",
                )
            }

            is SessionEvent.RegistrationRejected, SessionEvent.RegistrationTimedOut -> {
                s.ifPhase(Phase.REGISTERING) { registrationFailed(e) }
            }

            is SessionEvent.AppStatus -> {
                appStatus(s, e.registered)
            }

            SessionEvent.StopRequested -> {
                if (s.isActive) s.copy(phase = Phase.STOPPING) else s
            }

            SessionEvent.Stopped -> {
                SessionState(
                    problem = s.problem.takeIf { s.phase != Phase.STOPPING },
                    detail = s.detail,
                )
            }

            SessionEvent.AdapterOff -> {
                if (s.isActive) SessionState(problem = Problem.BLUETOOTH_TURNED_OFF_WHILE_ACTIVE) else s
            }
        }

    private fun link(
        s: SessionState,
        e: SessionEvent.Link,
    ): SessionState =
        when (e) {
            is SessionEvent.ConnectRequested -> {
                s.ifPhase(Phase.REGISTERED) { connecting(it, e.address) }
            }

            is SessionEvent.ConnectRejected -> {
                s.ifPhase(Phase.CONNECTING) { registered(it, Problem.CONNECTION_FAILED, e.detail) }
            }

            is SessionEvent.LinkChanged -> {
                linkChanged(s, e)
            }

            SessionEvent.ConnectTimeout -> {
                s.ifPhase(Phase.CONNECTING) { registered(it, Problem.CONNECTION_TIMEOUT, null) }
            }

            is SessionEvent.ProtocolChanged -> {
                s.ifPhase(Phase.CONNECTED) { it.copy(protocolMode = e.mode) }
            }

            is SessionEvent.VirtualCableUnplugged -> {
                unplugged(s, e.address)
            }

            SessionEvent.DisconnectRequested -> {
                if (s.phase in
                    CONNECTING_OR_CONNECTED
                ) {
                    s.copy(phase = Phase.DISCONNECTING)
                } else {
                    s
                }
            }
        }

    private val CONNECTING_OR_CONNECTED = setOf(Phase.CONNECTING, Phase.CONNECTED)

    private fun registrationFailed(e: SessionEvent.Lifecycle): SessionState =
        if (e is SessionEvent.RegistrationRejected) {
            rejected(e.detail)
        } else {
            SessionState(problem = Problem.REGISTRATION_STALLED)
        }

    private fun rejected(detail: String) = SessionState(problem = Problem.REGISTRATION_REJECTED, detail = detail)

    private fun connecting(
        s: SessionState,
        address: String,
    ) = s.copy(phase = Phase.CONNECTING, hostAddress = address, problem = null, detail = null)

    private fun appStatus(
        s: SessionState,
        registered: Boolean,
    ): SessionState =
        when {
            registered && s.phase == Phase.REGISTERING -> s.copy(phase = Phase.REGISTERED)
            registered -> s
            s.phase == Phase.STOPPING -> s
            s.phase == Phase.REGISTERING -> rejected("registered=false")
            s.phase in REGISTERED_OR_LATER -> SessionState(problem = Problem.REGISTRATION_LOST)
            else -> s
        }

    private fun unplugged(
        s: SessionState,
        address: String,
    ): SessionState {
        val forThisHost = s.hostAddress == null || s.hostAddress == address
        return if (s.phase in LINKED && forThisHost) registered(s, Problem.HOST_UNPLUGGED, address) else s
    }

    private fun linkChanged(
        s: SessionState,
        e: SessionEvent.LinkChanged,
    ): SessionState =
        when (e.state) {
            LinkState.CONNECTING -> {
                if (s.phase == Phase.REGISTERED) connecting(s, e.address) else s
            }

            LinkState.CONNECTED -> {
                if (s.phase == Phase.REGISTERED ||
                    s.phase == Phase.CONNECTING
                ) {
                    connected(s, e.address)
                } else {
                    s
                }
            }

            LinkState.DISCONNECTING -> {
                if (s.phase == Phase.CONNECTED &&
                    s.isHost(e.address)
                ) {
                    s.copy(phase = Phase.DISCONNECTING)
                } else {
                    s
                }
            }

            LinkState.DISCONNECTED -> {
                if (s.isHost(e.address)) disconnected(s) else s
            }
        }

    private fun connected(
        s: SessionState,
        address: String,
    ) = s.copy(
        phase = Phase.CONNECTED,
        hostAddress = address,
        protocolMode = ProtocolMode.REPORT,
        problem = null,
        detail = null,
    )

    private fun disconnected(s: SessionState): SessionState =
        when (s.phase) {
            Phase.CONNECTING -> registered(s, Problem.CONNECTION_FAILED, null)
            Phase.CONNECTED -> registered(s, Problem.CONNECTION_LOST, null)
            Phase.DISCONNECTING -> registered(s, null, null)
            Phase.STOPPING -> s.copy(hostAddress = null)
            else -> s
        }

    private fun SessionState.isHost(address: String) = hostAddress == null || hostAddress == address

    private fun registered(
        s: SessionState,
        problem: Problem?,
        detail: String?,
    ) = s.copy(
        phase = Phase.REGISTERED,
        hostAddress = null,
        protocolMode = ProtocolMode.REPORT,
        problem = problem,
        detail = detail,
    )

    private fun SessionState.endedUnlessStopping(
        problem: Problem,
        detail: String,
    ): SessionState =
        when (phase) {
            Phase.IDLE, Phase.STOPPING -> this
            else -> SessionState(problem = problem, detail = detail)
        }

    private inline fun SessionState.ifPhase(
        phase: Phase,
        block: (SessionState) -> SessionState,
    ): SessionState = if (this.phase == phase) block(this) else this
}
