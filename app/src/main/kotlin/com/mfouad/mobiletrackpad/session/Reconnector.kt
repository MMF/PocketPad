package com.mfouad.mobiletrackpad.session

import com.mfouad.mobiletrackpad.gesture.Cancellable
import com.mfouad.mobiletrackpad.gesture.Scheduler

/**
 * Bounded, cancellable reconnection to the last host after an unexpected disconnect during an
 * active session. See docs/ARCHITECTURE.md section 5.4.
 *
 * - Starts only when a connected link is lost (CONNECTION_LOST), never after an explicit
 *   disconnect, a stop, or a virtual cable unplug.
 * - Tries once after each delay in [delaysNanos], then gives up.
 * - Stops as soon as a connection succeeds, the session ends, or [cancel] is called.
 *
 * Pure Kotlin; call from one thread.
 */
class Reconnector(
    private val scheduler: Scheduler,
    private val delaysNanos: List<Long> = DEFAULT_DELAYS,
    private val connect: (address: String) -> Unit,
    private val onStatus: (Status?) -> Unit = {},
) {
    data class Status(
        val address: String,
        val attempt: Int,
        val maxAttempts: Int,
    )

    var status: Status? = null
        private set(value) {
            field = value
            onStatus(value)
        }

    private var task: Cancellable? = null

    fun onTransition(
        old: SessionState,
        new: SessionState,
    ) {
        val target = status?.address
        when {
            new.phase == Phase.CONNECTED || !new.isActive || new.phase == Phase.STOPPING -> cancel()
            new.problem == Problem.HOST_UNPLUGGED -> cancel()
            lostConnection(old, new) && old.hostAddress != null -> start(old.hostAddress)
            target != null && attemptFailed(old, new) -> scheduleNext(target)
        }
    }

    /** User cancelled, or chose to connect or disconnect themselves. */
    fun cancel() {
        task?.cancel()
        task = null
        if (status != null) status = null
    }

    private fun lostConnection(
        old: SessionState,
        new: SessionState,
    ) = !new.simulated && old.phase == Phase.CONNECTED && new.phase == Phase.REGISTERED &&
        new.problem == Problem.CONNECTION_LOST

    private fun attemptFailed(
        old: SessionState,
        new: SessionState,
    ) = old.phase == Phase.CONNECTING &&
        new.phase == Phase.REGISTERED &&
        (new.problem == Problem.CONNECTION_FAILED || new.problem == Problem.CONNECTION_TIMEOUT)

    private fun start(address: String) {
        cancel()
        scheduleNext(address, attempt = 1)
    }

    private fun scheduleNext(
        address: String,
        attempt: Int = (status?.attempt ?: 0) + 1,
    ) {
        task?.cancel()
        val delay = delaysNanos.getOrNull(attempt - 1)
        if (delay == null) {
            cancel()
            return
        }
        status = Status(address, attempt, delaysNanos.size)
        task =
            scheduler.schedule(delay) {
                task = null
                connect(address)
            }
    }

    companion object {
        private const val SECOND = 1_000_000_000L
        val DEFAULT_DELAYS: List<Long> = listOf(2 * SECOND, 5 * SECOND, 10 * SECOND)
    }
}
