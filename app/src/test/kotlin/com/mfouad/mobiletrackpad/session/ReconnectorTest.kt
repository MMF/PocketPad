package com.mfouad.mobiletrackpad.session

import com.mfouad.mobiletrackpad.gesture.FakeScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReconnectorTest {
    private val host = "AA:BB:CC:DD:EE:FF"
    private val scheduler = FakeScheduler()
    private val attempts = mutableListOf<Pair<Long, String>>()
    private val reconnector =
        Reconnector(scheduler, listOf(2 * S, 5 * S, 10 * S), connect = {
            attempts +=
                scheduler.nowNanos() to it
        })

    private val connected = SessionState(Phase.CONNECTED, hostAddress = host)
    private val lost = SessionState(Phase.REGISTERED, problem = Problem.CONNECTION_LOST)
    private val connecting = SessionState(Phase.CONNECTING, hostAddress = host)
    private val failed = SessionState(Phase.REGISTERED, problem = Problem.CONNECTION_FAILED)

    @Test
    fun lostConnectionRetriesAfterEachDelayThenGivesUp() {
        reconnector.onTransition(connected, lost)
        scheduler.advanceBy(2 * S)
        assertEquals(listOf(2 * S to host), attempts)
        reconnector.onTransition(lost, connecting)
        reconnector.onTransition(connecting, failed)
        assertEquals(2, reconnector.status?.attempt)
        scheduler.advanceBy(5 * S)
        reconnector.onTransition(failed, connecting)
        reconnector.onTransition(connecting, failed.copy(problem = Problem.CONNECTION_TIMEOUT))
        scheduler.advanceBy(10 * S)
        assertEquals(listOf(2 * S, 7 * S, 17 * S), attempts.map { it.first })
        reconnector.onTransition(failed, connecting)
        reconnector.onTransition(connecting, failed)
        assertNull(reconnector.status, "gave up after three attempts")
        scheduler.advanceBy(60 * S)
        assertEquals(3, attempts.size)
    }

    @Test
    fun successStopsRetrying() {
        reconnector.onTransition(connected, lost)
        scheduler.advanceBy(2 * S)
        reconnector.onTransition(lost, connecting)
        reconnector.onTransition(connecting, connected)
        assertNull(reconnector.status)
        assertEquals(0, scheduler.pendingCount)
    }

    @Test
    fun explicitDisconnectDoesNotReconnect() {
        val disconnecting = connected.copy(phase = Phase.DISCONNECTING)
        reconnector.onTransition(connected, disconnecting)
        reconnector.onTransition(disconnecting, SessionState(Phase.REGISTERED))
        scheduler.advanceBy(60 * S)
        assertEquals(emptyList(), attempts)
    }

    @Test
    fun unplugDoesNotReconnect() {
        reconnector.onTransition(connected, SessionState(Phase.REGISTERED, problem = Problem.HOST_UNPLUGGED))
        scheduler.advanceBy(60 * S)
        assertEquals(emptyList(), attempts)
    }

    @Test
    fun stopOrSessionEndCancels() {
        reconnector.onTransition(connected, lost)
        reconnector.onTransition(lost, lost.copy(phase = Phase.STOPPING))
        scheduler.advanceBy(60 * S)
        assertEquals(emptyList(), attempts)
        reconnector.onTransition(connected, lost)
        reconnector.onTransition(lost, SessionState(problem = Problem.BLUETOOTH_TURNED_OFF_WHILE_ACTIVE))
        scheduler.advanceBy(60 * S)
        assertEquals(emptyList(), attempts)
    }

    @Test
    fun userCancelStopsPendingAttempt() {
        reconnector.onTransition(connected, lost)
        reconnector.cancel()
        scheduler.advanceBy(60 * S)
        assertEquals(emptyList(), attempts)
        assertNull(reconnector.status)
    }

    @Test
    fun simulatedSessionsNeverReconnect() {
        reconnector.onTransition(connected.copy(simulated = true), lost.copy(simulated = true))
        scheduler.advanceBy(60 * S)
        assertEquals(emptyList(), attempts)
    }

    private companion object {
        const val S = 1_000_000_000L
    }
}
