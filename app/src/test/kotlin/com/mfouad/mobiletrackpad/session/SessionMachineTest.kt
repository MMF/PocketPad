package com.mfouad.mobiletrackpad.session

import com.mfouad.mobiletrackpad.hid.ProtocolMode
import com.mfouad.mobiletrackpad.session.SessionEvent.AdapterOff
import com.mfouad.mobiletrackpad.session.SessionEvent.AppStatus
import com.mfouad.mobiletrackpad.session.SessionEvent.Blocked
import com.mfouad.mobiletrackpad.session.SessionEvent.ConnectRejected
import com.mfouad.mobiletrackpad.session.SessionEvent.ConnectRequested
import com.mfouad.mobiletrackpad.session.SessionEvent.ConnectTimeout
import com.mfouad.mobiletrackpad.session.SessionEvent.DisconnectRequested
import com.mfouad.mobiletrackpad.session.SessionEvent.LinkChanged
import com.mfouad.mobiletrackpad.session.SessionEvent.ProtocolChanged
import com.mfouad.mobiletrackpad.session.SessionEvent.ProxyConnected
import com.mfouad.mobiletrackpad.session.SessionEvent.ProxyDisconnected
import com.mfouad.mobiletrackpad.session.SessionEvent.ProxyFailed
import com.mfouad.mobiletrackpad.session.SessionEvent.RegistrationRejected
import com.mfouad.mobiletrackpad.session.SessionEvent.StartRequested
import com.mfouad.mobiletrackpad.session.SessionEvent.StopRequested
import com.mfouad.mobiletrackpad.session.SessionEvent.Stopped
import com.mfouad.mobiletrackpad.session.SessionEvent.VirtualCableUnplugged
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionMachineTest {
    private val host = "AA:BB:CC:DD:EE:FF"

    private fun run(vararg events: SessionEvent): SessionState =
        events.fold(SessionState()) { s, e -> SessionMachine.reduce(s, e) }

    private val registered: Array<SessionEvent> = arrayOf(StartRequested(), ProxyConnected, AppStatus(true))
    private val connected: Array<SessionEvent> =
        registered + arrayOf(ConnectRequested(host), LinkChanged(host, LinkState.CONNECTED))

    @Test
    fun happyPathToConnected() {
        assertEquals(Phase.ACQUIRING_PROXY, run(StartRequested()).phase)
        assertEquals(Phase.REGISTERING, run(StartRequested(), ProxyConnected).phase)
        assertEquals(Phase.REGISTERED, run(*registered).phase)
        assertEquals(Phase.CONNECTING, run(*registered, ConnectRequested(host)).phase)
        val s = run(*connected)
        assertEquals(Phase.CONNECTED, s.phase)
        assertEquals(host, s.hostAddress)
        assertNull(s.problem)
    }

    @Test
    fun registrationIsDecidedByCallbackNotByCall() {
        // registerApp returned true but no callback yet: still registering.
        assertEquals(Phase.REGISTERING, run(StartRequested(), ProxyConnected).phase)
        val rejected = run(StartRequested(), ProxyConnected, AppStatus(false))
        assertEquals(Phase.IDLE, rejected.phase)
        assertEquals(Problem.REGISTRATION_REJECTED, rejected.problem)
        val callRejected = run(StartRequested(), ProxyConnected, RegistrationRejected("registerApp returned false"))
        assertEquals(Problem.REGISTRATION_REJECTED, callRejected.problem)
    }

    @Test
    fun distinctFailureCategories() {
        assertEquals(Problem.MISSING_PERMISSIONS, run(Blocked(Problem.MISSING_PERMISSIONS)).problem)
        assertEquals(Problem.BLUETOOTH_DISABLED, run(Blocked(Problem.BLUETOOTH_DISABLED)).problem)
        assertEquals(Problem.PROFILE_UNAVAILABLE, run(StartRequested(), ProxyFailed("getProfileProxy=false")).problem)
        assertEquals(Problem.CONNECTION_TIMEOUT, run(*registered, ConnectRequested(host), ConnectTimeout).problem)
        assertEquals(
            Problem.CONNECTION_FAILED,
            run(*registered, ConnectRequested(host), ConnectRejected("connect=false")).problem,
        )
        assertEquals(
            Problem.CONNECTION_FAILED,
            run(*registered, ConnectRequested(host), LinkChanged(host, LinkState.DISCONNECTED)).problem,
        )
    }

    @Test
    fun unsolicitedUnregistrationEndsTheSession() {
        val s = run(*connected, AppStatus(false))
        assertEquals(Phase.IDLE, s.phase)
        assertEquals(Problem.REGISTRATION_LOST, s.problem)
    }

    @Test
    fun unexpectedDisconnectReturnsToRegisteredWithProblem() {
        val s = run(*connected, LinkChanged(host, LinkState.DISCONNECTED))
        assertEquals(Phase.REGISTERED, s.phase)
        assertEquals(Problem.CONNECTION_LOST, s.problem)
        assertNull(s.hostAddress)
    }

    @Test
    fun userDisconnectHasNoProblem() {
        val s = run(*connected, DisconnectRequested, LinkChanged(host, LinkState.DISCONNECTED))
        assertEquals(Phase.REGISTERED, s.phase)
        assertNull(s.problem)
    }

    @Test
    fun virtualCableUnplugEndsHostSessionAndAsksForRepair() {
        val s = run(*connected, VirtualCableUnplugged(host))
        assertEquals(Phase.REGISTERED, s.phase)
        assertEquals(Problem.HOST_UNPLUGGED, s.problem)
        // The later disconnect callback does not replace the unplug problem.
        assertEquals(
            Problem.HOST_UNPLUGGED,
            SessionMachine.reduce(s, LinkChanged(host, LinkState.DISCONNECTED)).problem,
        )
    }

    @Test
    fun hostInitiatedConnectionIsAccepted() {
        val s = run(*registered, LinkChanged(host, LinkState.CONNECTING), LinkChanged(host, LinkState.CONNECTED))
        assertEquals(Phase.CONNECTED, s.phase)
        assertEquals(host, s.hostAddress)
    }

    @Test
    fun protocolModeTracksHostAndResetsOnNewConnection() {
        assertEquals(ProtocolMode.BOOT, run(*connected, ProtocolChanged(ProtocolMode.BOOT)).protocolMode)
        val reconnected =
            run(
                *connected,
                ProtocolChanged(ProtocolMode.BOOT),
                LinkChanged(host, LinkState.DISCONNECTED),
                LinkChanged(host, LinkState.CONNECTED),
            )
        assertEquals(ProtocolMode.REPORT, reconnected.protocolMode)
    }

    @Test
    fun eventsForAnotherHostAreIgnored() {
        val s = run(*connected, LinkChanged("11:22:33:44:55:66", LinkState.DISCONNECTED))
        assertEquals(Phase.CONNECTED, s.phase)
    }

    @Test
    fun stopEndsCleanlyWithoutProblem() {
        val s =
            run(
                *connected,
                StopRequested,
                LinkChanged(host, LinkState.DISCONNECTED),
                AppStatus(false),
                ProxyDisconnected,
                Stopped,
            )
        assertEquals(SessionState(), s)
    }

    @Test
    fun bluetoothOffEndsActiveSession() {
        val s = run(*connected, AdapterOff)
        assertEquals(Phase.IDLE, s.phase)
        assertEquals(Problem.BLUETOOTH_TURNED_OFF_WHILE_ACTIVE, s.problem)
    }

    @Test
    fun bluetoothOffWhileOnlyRegisteredIsReportedTheSameWay() {
        assertEquals(Problem.BLUETOOTH_TURNED_OFF_WHILE_ACTIVE, run(*registered, AdapterOff).problem)
    }

    @Test
    fun bluetoothAlreadyOffAtStartIsAPlainDisabledProblem() {
        assertEquals(Problem.BLUETOOTH_DISABLED, run(Blocked(Problem.BLUETOOTH_DISABLED)).problem)
        assertEquals(SessionState(), run(AdapterOff))
    }

    @Test
    fun registrationTimeoutIsDistinctFromRejection() {
        val s = run(StartRequested(), ProxyConnected, SessionEvent.RegistrationTimedOut)
        assertEquals(Phase.IDLE, s.phase)
        assertEquals(Problem.REGISTRATION_STALLED, s.problem)
        // A late timeout after registration completed changes nothing.
        assertEquals(Phase.REGISTERED, run(*registered, SessionEvent.RegistrationTimedOut).phase)
    }

    @Test
    fun startClearsAPreviousProblem() {
        val s = run(Blocked(Problem.MISSING_PERMISSIONS), StartRequested())
        assertEquals(Phase.ACQUIRING_PROXY, s.phase)
        assertNull(s.problem)
    }

    @Test
    fun connectIsIgnoredUnlessRegistered() {
        assertEquals(Phase.REGISTERING, run(StartRequested(), ProxyConnected, ConnectRequested(host)).phase)
    }
}
