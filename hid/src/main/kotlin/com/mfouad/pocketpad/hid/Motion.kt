package com.mfouad.pocketpad.hid

import kotlin.math.truncate

/** Whole-count relative motion: pointer X/Y, vertical wheel and horizontal pan. */
internal data class Delta(
    val x: Long = 0,
    val y: Long = 0,
    val wheel: Long = 0,
    val pan: Long = 0,
) {
    val isZero: Boolean get() = this == ZERO

    operator fun minus(o: Delta) = Delta(x - o.x, y - o.y, wheel - o.wheel, pan - o.pan)

    /** The largest part of this delta that fits the given field limits. */
    fun clamp(
        xyLimit: Int,
        wheelPanLimit: Int,
    ): Delta =
        Delta(
            x.coerceIn(-xyLimit.toLong(), xyLimit.toLong()),
            y.coerceIn(-xyLimit.toLong(), xyLimit.toLong()),
            wheel.coerceIn(-wheelPanLimit.toLong(), wheelPanLimit.toLong()),
            pan.coerceIn(-wheelPanLimit.toLong(), wheelPanLimit.toLong()),
        )

    companion object {
        val ZERO = Delta()
    }
}

/**
 * Accumulates fractional motion. Whole counts are taken out for sending; fractions stay, so
 * the total sent always equals the total added, truncated toward zero.
 */
internal class MotionAccumulator {
    private var x = 0.0
    private var y = 0.0
    private var wheel = 0.0
    private var pan = 0.0

    fun addMove(
        dx: Float,
        dy: Float,
    ) {
        x += dx
        y += dy
    }

    fun addScroll(
        dWheel: Float,
        dPan: Float,
    ) {
        wheel += dWheel
        pan += dPan
    }

    fun clearScroll() {
        wheel = 0.0
        pan = 0.0
    }

    fun clear() {
        x = 0.0
        y = 0.0
        clearScroll()
    }

    /** The whole-count part currently accumulated. */
    fun whole(): Delta = Delta(trunc(x), trunc(y), trunc(wheel), trunc(pan))

    fun remove(d: Delta) {
        x -= d.x
        y -= d.y
        wheel -= d.wheel
        pan -= d.pan
    }

    fun takeWhole(): Delta = whole().also { remove(it) }

    private fun trunc(v: Double): Long = truncate(v).toLong()
}
