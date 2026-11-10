package com.mfouad.mobiletrackpad.session

import com.mfouad.mobiletrackpad.diagnostics.DiagnosticsLog
import com.mfouad.mobiletrackpad.diagnostics.DiagnosticsLog.Category
import com.mfouad.mobiletrackpad.gesture.Cancellable
import com.mfouad.mobiletrackpad.gesture.Scheduler
import com.mfouad.mobiletrackpad.hid.Action
import com.mfouad.mobiletrackpad.hid.ConsumerUsage
import com.mfouad.mobiletrackpad.hid.MouseButton
import com.mfouad.mobiletrackpad.hid.ReportSender

/** Fixed test inputs for checking a connection before the trackpad surface exists. */
enum class TestAction {
    SQUARE,
    LEFT_CLICK,
    RIGHT_CLICK,
    SCROLL_UP,
    SCROLL_DOWN,
    VOLUME_UP,
    VOLUME_DOWN,
    HOLD_LEFT,
    RELEASE_LEFT,
}

/** Runs [TestAction]s through the normal sender. Engine thread only. */
class TestInput(
    private val sender: ReportSender,
    private val scheduler: Scheduler,
    private val log: DiagnosticsLog,
) {
    private var pattern: Cancellable? = null

    fun run(action: TestAction) {
        log.log(Category.GESTURE) { "test: $action" }
        when (action) {
            TestAction.SQUARE -> square()
            TestAction.LEFT_CLICK -> sender.perform(Action.MouseClick(MouseButton.PRIMARY))
            TestAction.RIGHT_CLICK -> sender.perform(Action.MouseClick(MouseButton.SECONDARY))
            TestAction.SCROLL_UP -> sender.scroll(SCROLL_DETENTS, 0f)
            TestAction.SCROLL_DOWN -> sender.scroll(-SCROLL_DETENTS, 0f)
            TestAction.VOLUME_UP -> sender.perform(Action.Consumer(ConsumerUsage.VOLUME_INCREMENT))
            TestAction.VOLUME_DOWN -> sender.perform(Action.Consumer(ConsumerUsage.VOLUME_DECREMENT))
            TestAction.HOLD_LEFT -> sender.press(MouseButton.PRIMARY)
            TestAction.RELEASE_LEFT -> sender.release(MouseButton.PRIMARY)
        }
    }

    /** Stops any running pattern. Held buttons are released by the caller via releaseAll. */
    fun cancel() {
        pattern?.cancel()
        pattern = null
    }

    /** Moves right, down, left, up: SIDE counts per side in small steps, ending where it began. */
    private fun square() {
        cancel()
        val steps =
            listOf(1f to 0f, 0f to 1f, -1f to 0f, 0f to -1f).flatMap { (x, y) ->
                List(STEPS_PER_SIDE) { x * STEP to y * STEP }
            }

        fun next(i: Int) {
            if (i >= steps.size) {
                pattern = null
                return
            }
            sender.move(steps[i].first, steps[i].second)
            pattern = scheduler.schedule(STEP_INTERVAL_NANOS) { next(i + 1) }
        }
        next(0)
    }

    private companion object {
        const val STEPS_PER_SIDE = 25
        const val STEP = 8f
        const val STEP_INTERVAL_NANOS = 10_000_000L
        const val SCROLL_DETENTS = 3f
    }
}
