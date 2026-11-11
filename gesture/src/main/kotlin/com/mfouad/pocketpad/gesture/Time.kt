package com.mfouad.pocketpad.gesture

/** Monotonic time source. Injected so timing behaviour is deterministic in tests. */
fun interface Clock {
    fun nowNanos(): Long
}

/** Handle for a scheduled task. */
fun interface Cancellable {
    fun cancel()
}

/**
 * Runs tasks after a delay on the owning (single) thread.
 *
 * Implementations must run tasks on the same thread that calls [schedule], in due-time order.
 */
interface Scheduler : Clock {
    fun schedule(
        delayNanos: Long,
        task: () -> Unit,
    ): Cancellable
}
