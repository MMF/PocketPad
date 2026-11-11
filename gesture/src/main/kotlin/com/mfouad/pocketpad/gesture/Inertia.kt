package com.mfouad.pocketpad.gesture

import kotlin.math.exp

/** Recent centroid positions, for estimating lift-off velocity. */
internal class VelocityTracker(
    private val windowNanos: Long,
) {
    private data class Sample(
        val t: Long,
        val p: Pt,
    )

    private val samples = ArrayDeque<Sample>()

    fun clear() = samples.clear()

    fun add(
        t: Long,
        p: Pt,
    ) {
        samples.addLast(Sample(t, p))
        while (samples.size > 2 && samples.first().t < t - windowNanos) samples.removeFirst()
    }

    /**
     * Velocity in px/s over the samples within the window before [endNanos], or null if the
     * fingers had stopped (no two samples inside the window).
     */
    fun velocity(endNanos: Long): Pt? {
        val recent = samples.filter { it.t >= endNanos - windowNanos }
        if (recent.size < 2) return null
        val first = recent.first()
        val last = recent.last()
        val dt = (last.t - first.t).toFloat() / NANOS_PER_SECOND
        return if (dt <= 0f) null else (last.p - first.p) * (1f / dt)
    }
}

/** Emits exponentially decaying scroll after a fling, on the injected scheduler. */
internal class InertiaRunner(
    private val scheduler: Scheduler,
    private val emit: (Pt) -> Unit,
) {
    private var task: Cancellable? = null

    val isRunning: Boolean get() = task != null

    fun start(
        initial: Pt,
        config: GestureConfig,
    ) {
        stop()
        var v = initial
        val dt = config.inertiaTickNanos.toFloat() / NANOS_PER_SECOND
        val decay = exp(-config.inertiaTickNanos.toDouble() / config.inertiaTimeConstantNanos).toFloat()

        fun step() {
            emit(v * dt)
            v = v * decay
            task =
                if (v.length < config.inertiaStopVelocityPxPerSec) {
                    null
                } else {
                    scheduler.schedule(config.inertiaTickNanos) { step() }
                }
        }
        task = scheduler.schedule(config.inertiaTickNanos) { step() }
    }

    fun stop() {
        task?.cancel()
        task = null
    }
}

private const val NANOS_PER_SECOND = 1_000_000_000f
