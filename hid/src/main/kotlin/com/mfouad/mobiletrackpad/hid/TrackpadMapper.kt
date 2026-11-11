package com.mfouad.mobiletrackpad.hid

import com.mfouad.mobiletrackpad.gesture.GestureEvent
import com.mfouad.mobiletrackpad.gesture.PointerButton
import com.mfouad.mobiletrackpad.gesture.SwipeDirection
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min

/**
 * How surface pixels become HID counts and wheel detents.
 *
 * @param countsPerPx pointer counts per surface pixel (sensitivity).
 * @param acceleration extra gain for fast strokes, 0 = none. Kept light because hosts apply
 *   their own acceleration: gain = 1 + acceleration * min(stepPx / accelerationRefPx, 1).
 * @param pxPerDetent finger travel for one wheel detent.
 * @param reverseScroll invert scroll direction. By default fingers moving up send wheel-up,
 *   like a physical wheel rolled away from the user; the host applies its own natural-scrolling
 *   setting on top (verified on macOS, docs/HID.md section 4.3).
 */
data class PointerSettings(
    val countsPerPx: Float,
    val acceleration: Float = 0f,
    val accelerationRefPx: Float = 1f,
    val pxPerDetent: Float,
    val reverseScroll: Boolean = false,
) {
    companion object {
        /** Defaults from a dp-to-px factor: 1 count per dp, one detent per 20 dp. */
        @Suppress("MagicNumber")
        fun forDensity(pxPerDp: Float) =
            PointerSettings(
                countsPerPx = 1f / pxPerDp,
                accelerationRefPx = 24f * pxPerDp,
                pxPerDetent = 20f * pxPerDp,
            )
    }
}

/**
 * Maps recognised gestures to the report sender. Engine thread only.
 *
 * Pinch and rotate are continuous: each [ShortcutSteps.pinchLogStep] of scale change or
 * [ShortcutSteps.rotateDegrees] of rotation performs the mapped action once. Three-finger swipes
 * perform their action once per gesture.
 *
 * @param onShortcut called before a shortcut is performed, so the caller can stop scroll
 *   inertia; together with the sender discarding pending scroll this keeps residual scrolling
 *   out of modifier shortcuts.
 * @param onShortcutResult called with each performed action and whether the sender accepted it.
 */
class TrackpadMapper(
    private val sender: ReportSender,
    var settings: PointerSettings,
    var profile: HostProfile = HostProfiles.default(HostProfiles.CUSTOM),
    var steps: ShortcutSteps = ShortcutSteps(),
    private val onShortcut: () -> Unit = {},
    private val onShortcutResult: (GestureSlot, Action, Boolean) -> Unit = { _, _, _ -> },
) {
    private var pinchAccumulated = 0.0
    private var rotateAccumulated = 0f

    fun onEvent(e: GestureEvent) {
        when (e) {
            is GestureEvent.Move -> move(e.dx, e.dy)
            is GestureEvent.ButtonDown -> sender.press(e.button.toMouseButton())
            is GestureEvent.ButtonUp -> sender.release(e.button.toMouseButton())
            is GestureEvent.Scroll -> scroll(e.dx, e.dy)
            GestureEvent.Cancel -> sender.releaseAll()
            is GestureEvent.Pinch -> pinch(e)
            is GestureEvent.Rotate -> rotate(e)
            is GestureEvent.Swipe3 -> perform(e.direction.slot())
        }
    }

    private fun pinch(e: GestureEvent.Pinch) {
        if (e.start) pinchAccumulated = 0.0
        pinchAccumulated += ln(1.0 + e.scaleDelta)
        while (pinchAccumulated >= steps.pinchLogStep) {
            pinchAccumulated -= steps.pinchLogStep
            perform(GestureSlot.PINCH_OUT)
        }
        while (pinchAccumulated <= -steps.pinchLogStep) {
            pinchAccumulated += steps.pinchLogStep
            perform(GestureSlot.PINCH_IN)
        }
    }

    private fun rotate(e: GestureEvent.Rotate) {
        if (e.start) rotateAccumulated = 0f
        rotateAccumulated += e.degrees
        while (rotateAccumulated >= steps.rotateDegrees) {
            rotateAccumulated -= steps.rotateDegrees
            perform(GestureSlot.ROTATE_CLOCKWISE)
        }
        while (rotateAccumulated <= -steps.rotateDegrees) {
            rotateAccumulated += steps.rotateDegrees
            perform(GestureSlot.ROTATE_COUNTERCLOCKWISE)
        }
    }

    private fun perform(slot: GestureSlot) {
        val action = profile.actionFor(slot) ?: return
        onShortcut()
        onShortcutResult(slot, action, sender.perform(action))
    }

    private fun move(
        dx: Float,
        dy: Float,
    ) {
        val s = settings
        val gain = 1f + s.acceleration * min(hypot(dx, dy) / s.accelerationRefPx, 1f)
        sender.move(dx * s.countsPerPx * gain, dy * s.countsPerPx * gain)
    }

    private fun scroll(
        dx: Float,
        dy: Float,
    ) {
        val s = settings
        val sign = if (s.reverseScroll) -1f else 1f
        // Fingers up (dy < 0) = wheel up (positive); fingers right (dx > 0) = pan right.
        sender.scroll(wheel = -dy / s.pxPerDetent * sign, pan = dx / s.pxPerDetent * sign)
    }
}

/** How much pinch or rotation triggers one shortcut. */
data class ShortcutSteps(
    /** Natural log of the scale change per step; ln(1.25) means every 25% of spread. */
    val pinchLogStep: Double = ln(DEFAULT_PINCH_STEP_SCALE),
    val rotateDegrees: Float = DEFAULT_ROTATE_STEP_DEGREES,
) {
    companion object {
        const val DEFAULT_PINCH_STEP_SCALE = 1.25
        const val DEFAULT_ROTATE_STEP_DEGREES = 30f
    }
}

private fun SwipeDirection.slot(): GestureSlot =
    when (this) {
        SwipeDirection.UP -> GestureSlot.SWIPE3_UP
        SwipeDirection.DOWN -> GestureSlot.SWIPE3_DOWN
        SwipeDirection.LEFT -> GestureSlot.SWIPE3_LEFT
        SwipeDirection.RIGHT -> GestureSlot.SWIPE3_RIGHT
    }

fun PointerButton.toMouseButton(): MouseButton =
    when (this) {
        PointerButton.PRIMARY -> MouseButton.PRIMARY
        PointerButton.SECONDARY -> MouseButton.SECONDARY
        PointerButton.TERTIARY -> MouseButton.TERTIARY
    }
