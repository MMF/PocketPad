package com.mfouad.mobiletrackpad.gesture

/** Semantic output of gesture recognition. Units are surface pixels unless stated. */
sealed interface GestureEvent {
    data class Move(
        val dx: Float,
        val dy: Float,
    ) : GestureEvent

    data class ButtonDown(
        val button: PointerButton,
    ) : GestureEvent

    data class ButtonUp(
        val button: PointerButton,
    ) : GestureEvent

    /** Positive [dy] scrolls content up (towards the top); positive [dx] scrolls right. */
    data class Scroll(
        val dx: Float,
        val dy: Float,
    ) : GestureEvent

    /** [scaleDelta] > 0 when fingers spread apart. */
    data class Pinch(
        val scaleDelta: Float,
    ) : GestureEvent

    /** Clockwise rotation in degrees since the previous event. */
    data class Rotate(
        val degrees: Float,
    ) : GestureEvent

    data class Swipe3(
        val direction: SwipeDirection,
    ) : GestureEvent

    /** Gesture cancelled: release anything held and stop inertia. */
    data object Cancel : GestureEvent
}

enum class PointerButton { PRIMARY, SECONDARY, TERTIARY }

enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }
