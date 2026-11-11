package com.mfouad.mobiletrackpad.input

import android.os.Build
import android.view.MotionEvent
import com.mfouad.mobiletrackpad.gesture.FrameAction
import com.mfouad.mobiletrackpad.gesture.Pointer
import com.mfouad.mobiletrackpad.gesture.PointerFrame

/**
 * Converts a [MotionEvent] into pointer frames, in order: every historical (batched) sample of
 * a move first, then the current sample. Must be called synchronously; MotionEvents are
 * recycled after dispatch.
 */
object MotionEventFrames {
    private const val NANOS_PER_MILLI = 1_000_000L

    fun from(ev: MotionEvent): List<PointerFrame> =
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                buildList(ev.historySize + 1) {
                    for (h in 0 until ev.historySize) add(frame(ev, FrameAction.MOVE, h))
                    add(frame(ev, FrameAction.MOVE, null))
                }
            }

            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                listOf(frame(ev, FrameAction.DOWN, null, ev.getPointerId(ev.actionIndex)))
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                // Android 13+ marks a lift it has judged accidental (for example a palm).
                val action = if (isCanceledFlag(ev)) FrameAction.CANCEL else FrameAction.UP
                listOf(frame(ev, action, null, ev.getPointerId(ev.actionIndex)))
            }

            MotionEvent.ACTION_CANCEL -> {
                listOf(frame(ev, FrameAction.CANCEL, null))
            }

            else -> {
                emptyList()
            }
        }

    private fun isCanceledFlag(ev: MotionEvent) =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (ev.flags and MotionEvent.FLAG_CANCELED) != 0

    private fun frame(
        ev: MotionEvent,
        action: FrameAction,
        history: Int?,
        changed: Int? = null,
    ): PointerFrame {
        val pointers =
            List(ev.pointerCount) { i ->
                Pointer(
                    id = ev.getPointerId(i),
                    x = if (history == null) ev.getX(i) else ev.getHistoricalX(i, history),
                    y = if (history == null) ev.getY(i) else ev.getHistoricalY(i, history),
                    size = if (history == null) ev.getSize(i) else ev.getHistoricalSize(i, history),
                    pressure = if (history == null) ev.getPressure(i) else ev.getHistoricalPressure(i, history),
                )
            }
        return PointerFrame(timeNanos(ev, history), action, pointers, changed)
    }

    private fun timeNanos(
        ev: MotionEvent,
        history: Int?,
    ): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (history == null) ev.eventTimeNanos else ev.getHistoricalEventTimeNanos(history)
        } else {
            (if (history == null) ev.eventTime else ev.getHistoricalEventTime(history)) * NANOS_PER_MILLI
        }
}
