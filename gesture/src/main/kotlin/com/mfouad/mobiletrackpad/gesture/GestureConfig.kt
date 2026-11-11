package com.mfouad.mobiletrackpad.gesture

/**
 * Thresholds for gesture recognition. Distances are in surface pixels, times in nanoseconds of
 * the frame clock (the touch event timestamps), velocities in pixels per second.
 *
 * Use [forDensity] to build defaults from a dp-to-px factor.
 */
data class GestureConfig(
    /** Movement allowed during a tap; also the distance that turns a touch into pointer motion. */
    val tapSlopPx: Float,
    /** Longest touch that still counts as a one-finger tap. */
    val tapTimeoutNanos: Long = DEFAULT_TAP_TIMEOUT,
    /** A touch starting this soon after a tap, then moving, becomes a drag. */
    val doubleTapWindowNanos: Long = DEFAULT_DOUBLE_TAP_WINDOW,
    /** Longest two- or three-finger touch that still counts as a multi-finger tap. */
    val multiTapTimeoutNanos: Long = DEFAULT_MULTI_TAP_TIMEOUT,
    /** Centroid travel that starts a two-finger scroll. */
    val scrollSlopPx: Float,
    /** Change in finger distance that starts a pinch. */
    val pinchSlopPx: Float,
    /** Change in finger angle that starts a rotation. */
    val rotateSlopDegrees: Float = 15f,
    /** Centroid travel that triggers a three-finger swipe. */
    val swipe3SlopPx: Float,
    /** Scroll locks to one axis when that axis' travel is at least this many times the other. 0 = never. */
    val axisLockRatio: Float = 2f,
    val tapToClick: Boolean = true,
    /** Keep the button held after a drag until the next tap. */
    val dragLock: Boolean = false,
    val inertia: Boolean = true,
    val inertiaMinVelocityPxPerSec: Float,
    val inertiaStopVelocityPxPerSec: Float,
    /** Velocity decays by a factor of e every this many nanoseconds. */
    val inertiaTimeConstantNanos: Long = DEFAULT_INERTIA_TIME_CONSTANT,
    val inertiaTickNanos: Long = DEFAULT_INERTIA_TICK,
    /** Samples within this window before lift-off determine fling velocity. */
    val velocityWindowNanos: Long = DEFAULT_VELOCITY_WINDOW,
    /** If fingers lift at different times, the last lift must follow the first within this window. */
    val liftGraceNanos: Long = DEFAULT_LIFT_GRACE,
    /**
     * A contact whose reported size (0..1, MotionEvent.getSize) is at least this when it lands
     * is treated as a palm and ignored until it lifts. Null disables. Best effort: what "size"
     * means varies by device, and some report nothing useful.
     */
    val largeContactSize: Float? = DEFAULT_LARGE_CONTACT_SIZE,
) {
    companion object {
        const val MS = 1_000_000L
        const val DEFAULT_TAP_TIMEOUT = 200 * MS
        const val DEFAULT_DOUBLE_TAP_WINDOW = 300 * MS
        const val DEFAULT_MULTI_TAP_TIMEOUT = 300 * MS
        const val DEFAULT_INERTIA_TIME_CONSTANT = 325 * MS
        const val DEFAULT_INERTIA_TICK = 8 * MS
        const val DEFAULT_VELOCITY_WINDOW = 80 * MS
        const val DEFAULT_LIFT_GRACE = 100 * MS
        const val DEFAULT_LARGE_CONTACT_SIZE = 0.6f

        @Suppress("MagicNumber") // Defaults in dp, tuned on real hardware later.
        fun forDensity(pxPerDp: Float): GestureConfig =
            GestureConfig(
                tapSlopPx = 8f * pxPerDp,
                scrollSlopPx = 10f * pxPerDp,
                pinchSlopPx = 28f * pxPerDp,
                swipe3SlopPx = 48f * pxPerDp,
                inertiaMinVelocityPxPerSec = 300f * pxPerDp,
                inertiaStopVelocityPxPerSec = 20f * pxPerDp,
            )
    }
}
