package com.mfouad.mobiletrackpad.gesture

/** Semantic output of gesture recognition. Distances are surface pixels. */
sealed interface GestureEvent {
    /** One-finger pointer motion (or drag motion while a button is held). */
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

    /**
     * Two-finger scroll: movement of the finger centroid (positive [dy] = fingers moved down,
     * positive [dx] = fingers moved right). Inertia continues to emit these after lift-off.
     * Mapping to wheel direction happens later, so this stays a plain finger movement.
     */
    data class Scroll(
        val dx: Float,
        val dy: Float,
    ) : GestureEvent

    /**
     * Relative change in finger distance since the previous event; > 0 when spreading.
     * [start] is true on the first event of a pinch gesture.
     */
    data class Pinch(
        val scaleDelta: Float,
        val start: Boolean = false,
    ) : GestureEvent

    /** Clockwise rotation in degrees since the previous event; [start] marks the first event. */
    data class Rotate(
        val degrees: Float,
        val start: Boolean = false,
    ) : GestureEvent

    data class Swipe3(
        val direction: SwipeDirection,
    ) : GestureEvent

    /** Gesture cancelled: anything held has already been released with [ButtonUp]. */
    data object Cancel : GestureEvent
}

enum class PointerButton { PRIMARY, SECONDARY, TERTIARY }

enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }
