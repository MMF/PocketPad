package com.mfouad.mobiletrackpad.gesture

/** One touch pointer at one instant. [id] is stable for the lifetime of the contact. */
data class Pointer(
    val id: Int,
    val x: Float,
    val y: Float,
    /** Normalised contact size (0..1) where the device reports it, otherwise null. */
    val size: Float? = null,
    val pressure: Float? = null,
)

enum class FrameAction {
    /** A pointer went down; [PointerFrame.changedId] identifies it. */
    DOWN,

    /** Positions changed. */
    MOVE,

    /** A pointer went up; it is still present in [PointerFrame.pointers] for this frame. */
    UP,

    /** The platform cancelled the gesture. All held input must be released. */
    CANCEL,
}

/** A timestamped snapshot of all active pointers, produced from platform touch events. */
data class PointerFrame(
    val timeNanos: Long,
    val action: FrameAction,
    val pointers: List<Pointer>,
    val changedId: Int? = null,
)
