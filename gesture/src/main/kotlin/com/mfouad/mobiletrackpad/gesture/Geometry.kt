package com.mfouad.mobiletrackpad.gesture

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

internal data class Pt(
    val x: Float,
    val y: Float,
) {
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)

    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)

    operator fun times(k: Float) = Pt(x * k, y * k)

    val length: Float get() = hypot(x, y)

    val isZero: Boolean get() = x == 0f && y == 0f

    companion object {
        val ZERO = Pt(0f, 0f)
    }
}

internal fun Pointer.pt() = Pt(x, y)

internal fun centroid(points: Collection<Pointer>): Pt {
    if (points.isEmpty()) return Pt.ZERO
    var sx = 0f
    var sy = 0f
    points.forEach {
        sx += it.x
        sy += it.y
    }
    return Pt(sx / points.size, sy / points.size)
}

/** Angle of the line from [a] to [b], in degrees. */
internal fun angleDegrees(
    a: Pointer,
    b: Pointer,
): Float = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()

/** Smallest signed difference [to] - [from] in degrees, in -180..180. */
internal fun angleDelta(
    from: Float,
    to: Float,
): Float {
    var d = (to - from) % FULL_TURN
    if (d > HALF_TURN) d -= FULL_TURN
    if (d < -HALF_TURN) d += FULL_TURN
    return d
}

internal enum class Axis { FREE, HORIZONTAL, VERTICAL }

internal fun lockAxis(
    travel: Pt,
    ratio: Float,
): Axis =
    when {
        ratio <= 0f -> Axis.FREE
        abs(travel.y) >= ratio * abs(travel.x) -> Axis.VERTICAL
        abs(travel.x) >= ratio * abs(travel.y) -> Axis.HORIZONTAL
        else -> Axis.FREE
    }

internal fun Pt.constrain(axis: Axis): Pt =
    when (axis) {
        Axis.FREE -> this
        Axis.HORIZONTAL -> Pt(x, 0f)
        Axis.VERTICAL -> Pt(0f, y)
    }

internal fun swipeDirection(travel: Pt): SwipeDirection =
    if (abs(travel.x) >= abs(travel.y)) {
        if (travel.x >= 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
    } else {
        if (travel.y >= 0) SwipeDirection.DOWN else SwipeDirection.UP
    }

private const val FULL_TURN = 360f
private const val HALF_TURN = 180f
