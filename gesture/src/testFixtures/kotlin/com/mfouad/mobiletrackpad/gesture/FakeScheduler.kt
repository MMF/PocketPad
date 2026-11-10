package com.mfouad.mobiletrackpad.gesture

/** Virtual-time scheduler for tests. Time only moves when [advanceBy] or [advanceTo] is called. */
class FakeScheduler(
    startNanos: Long = 0L,
) : Scheduler {
    private data class Entry(
        val due: Long,
        val seq: Long,
        val task: () -> Unit,
    ) {
        var cancelled = false
    }

    private var now = startNanos
    private var seq = 0L
    private val entries = mutableListOf<Entry>()

    /** Number of scheduled, not-yet-run, not-cancelled tasks. */
    val pendingCount: Int get() = entries.count { !it.cancelled }

    override fun nowNanos(): Long = now

    override fun schedule(
        delayNanos: Long,
        task: () -> Unit,
    ): Cancellable {
        val entry = Entry(now + maxOf(0L, delayNanos), seq++, task)
        entries += entry
        return Cancellable {
            entry.cancelled = true
            entries.remove(entry)
        }
    }

    fun advanceBy(nanos: Long) = advanceTo(now + nanos)

    fun advanceTo(targetNanos: Long) {
        while (true) {
            val next =
                entries
                    .filter { !it.cancelled && it.due <= targetNanos }
                    .minWithOrNull(compareBy<Entry> { it.due }.thenBy { it.seq })
                    ?: break
            entries.remove(next)
            now = maxOf(now, next.due)
            next.task()
        }
        now = maxOf(now, targetNanos)
    }

    /** Runs everything currently due without moving time. */
    fun runDue() = advanceTo(now)
}
