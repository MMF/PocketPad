package com.mfouad.mobiletrackpad.hid

import com.mfouad.mobiletrackpad.gesture.GestureEvent
import com.mfouad.mobiletrackpad.gesture.PointerButton
import kotlin.math.hypot
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

/** Maps recognised gestures to the report sender. Engine thread only. */
class TrackpadMapper(
    private val sender: ReportSender,
    var settings: PointerSettings,
) {
    fun onEvent(e: GestureEvent) {
        when (e) {
            is GestureEvent.Move -> move(e.dx, e.dy)

            is GestureEvent.ButtonDown -> sender.press(e.button.toMouseButton())

            is GestureEvent.ButtonUp -> sender.release(e.button.toMouseButton())

            is GestureEvent.Scroll -> scroll(e.dx, e.dy)

            GestureEvent.Cancel -> sender.releaseAll()

            // Mapped to shortcuts in Milestone 4.
            is GestureEvent.Pinch, is GestureEvent.Rotate, is GestureEvent.Swipe3 -> Unit
        }
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

fun PointerButton.toMouseButton(): MouseButton =
    when (this) {
        PointerButton.PRIMARY -> MouseButton.PRIMARY
        PointerButton.SECONDARY -> MouseButton.SECONDARY
        PointerButton.TERTIARY -> MouseButton.TERTIARY
    }
