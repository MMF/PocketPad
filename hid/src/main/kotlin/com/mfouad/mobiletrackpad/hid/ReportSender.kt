package com.mfouad.mobiletrackpad.hid

import com.mfouad.mobiletrackpad.gesture.Cancellable
import com.mfouad.mobiletrackpad.gesture.Scheduler

/**
 * The single ordered output path for HID reports. See docs/ARCHITECTURE.md section 6.
 *
 * Not thread-safe: every method must be called on the scheduler's thread.
 *
 * - Movement and scrolling accumulate as fractions between sends; each send emits whole counts
 *   and keeps the remainder, so total displacement is preserved.
 * - Button, key and consumer transitions are sent in order, one per tick. Whole-count movement
 *   pending when a transition is queued is queued just before it, so order is preserved.
 * - A tick is scheduled only while there is something to send. There is no idle polling.
 * - Nothing is accumulated or queued while disconnected, so nothing is replayed on reconnect.
 */
class ReportSender(
    private val sink: OutputSink,
    private val scheduler: Scheduler,
    private val intervalNanos: Long = DEFAULT_INTERVAL_NANOS,
    private val maxQueuedTransitions: Int = DEFAULT_MAX_QUEUED_TRANSITIONS,
) {
    var isConnected: Boolean = false
        private set

    var protocolMode: ProtocolMode = ProtocolMode.REPORT
        private set

    /** True when no send is scheduled. */
    val isIdle: Boolean get() = tick == null

    private val queue = ArrayDeque<Transition>()
    private val motion = MotionAccumulator()

    /** State the queued transitions lead to. */
    private var target = NEUTRAL_STATE

    /** State last sent to the host. */
    private var sent = NEUTRAL_STATE

    private var tick: Cancellable? = null
    private var lastSendNanos: Long? = null

    /** A host connected. Protocol mode starts as Report; neutral reports are sent first. */
    fun onConnected() {
        resetAll()
        isConnected = true
        protocolMode = ProtocolMode.REPORT
        sendNeutralReports()
    }

    /** The link dropped. Local state is cleared; nothing can be sent. */
    fun onDisconnected() {
        isConnected = false
        resetAll()
    }

    /** The host selected a protocol mode. Pending input is discarded and neutral reports sent. */
    fun setProtocolMode(mode: ProtocolMode) {
        if (mode == protocolMode) return
        protocolMode = mode
        resetAll()
        if (isConnected) sendNeutralReports()
    }

    /** Adds relative pointer movement, in HID counts. */
    fun move(
        dx: Float,
        dy: Float,
    ) {
        if (!isConnected) return
        motion.addMove(dx, dy)
        scheduleIfNeeded()
    }

    /**
     * Adds scrolling, in wheel detents. Ignored in boot mode, and ignored while a keyboard
     * modifier is held or queued so scrolling can never combine with a shortcut's modifier.
     */
    fun scroll(
        wheel: Float,
        pan: Float,
    ) {
        if (!isConnected || protocolMode == ProtocolMode.BOOT || modifiersActive()) return
        motion.addScroll(wheel, pan)
        scheduleIfNeeded()
    }

    /** Discards scrolling that has not been sent yet (for example when inertia is cancelled). */
    fun discardPendingScroll() = motion.clearScroll()

    fun press(button: MouseButton) = setButton(button, pressed = true)

    fun release(button: MouseButton) = setButton(button, pressed = false)

    /**
     * Queues an action. Returns false, queueing nothing, when disconnected, when the action is
     * unavailable in the current protocol mode, or when the queue is full.
     */
    fun perform(action: Action): Boolean {
        val steps = if (isConnected && action.isAvailableIn(protocolMode)) action.expand(target) else emptyList()
        val accepted = steps.isNotEmpty() && queue.size + steps.size <= maxQueuedTransitions
        if (accepted) {
            if (action.usesModifiers()) motion.clearScroll()
            captureMotion()
            queue.addAll(steps)
            scheduleIfNeeded()
        }
        return accepted
    }

    /**
     * Releases everything held (touch cancel, trackpad paused). Pending input is discarded and
     * neutral reports are sent at once for any report whose last sent state was not neutral.
     */
    fun releaseAll() {
        clearPending()
        if (isConnected) {
            if (sent.buttons != 0) send(ReportEncoder.neutralMouse(protocolMode))
            if (!sent.keyboard.isNeutral) send(ReportEncoder.keyboard(KeyboardState.NEUTRAL))
            if (sent.consumer != ConsumerUsage.NONE) send(ReportEncoder.consumer(ConsumerUsage.NONE))
        }
        sent = NEUTRAL_STATE
        target = NEUTRAL_STATE
    }

    /** Before an intentional disconnect or shutdown: send neutral reports for every report ID. */
    fun shutdown() {
        resetAll()
        if (isConnected) sendNeutralReports()
    }

    private fun setButton(
        button: MouseButton,
        pressed: Boolean,
    ) {
        val allowed = isConnected && (protocolMode == ProtocolMode.REPORT || button.availableInBoot)
        val next = if (pressed) target.buttons or button.mask else target.buttons and button.mask.inv()
        if (!allowed || next == target.buttons) return
        target = target.copy(buttons = next)
        captureMotion()
        queue.addLast(Transition.Buttons(next))
        scheduleIfNeeded()
    }

    private fun modifiersActive(): Boolean =
        sent.keyboard.modifiers != 0 ||
            target.keyboard.modifiers != 0 ||
            queue.any { it is Transition.Keys && it.state.modifiers != 0 }

    private fun captureMotion() {
        val whole = motion.takeWhole()
        if (!whole.isZero) queue.addLast(Transition.Motion(whole))
    }

    private fun scheduleIfNeeded() {
        if (tick != null || !isConnected) return
        if (queue.isEmpty() && motion.whole().isZero) return
        val now = scheduler.nowNanos()
        val delay = lastSendNanos?.let { maxOf(0L, it + intervalNanos - now) } ?: 0L
        tick = scheduler.schedule(delay) { onTick() }
    }

    private fun onTick() {
        tick = null
        if (!isConnected) return
        val next = queue.removeFirstOrNull()
        if (next != null) apply(next) else sendMotionChunk()
        scheduleIfNeeded()
    }

    private fun apply(t: Transition) {
        when (t) {
            is Transition.Buttons -> {
                sent = sent.copy(buttons = t.mask)
                send(mouseReport(Delta.ZERO))
            }

            is Transition.Keys -> {
                sent = sent.copy(keyboard = t.state)
                send(ReportEncoder.keyboard(t.state))
            }

            is Transition.Consumer -> {
                sent = sent.copy(consumer = t.usage)
                send(ReportEncoder.consumer(t.usage))
            }

            is Transition.Motion -> {
                var rest = t.delta
                while (!rest.isZero) {
                    val chunk = rest.clamp(xyLimit(), ReportEncoder.INT8_MAX)
                    send(mouseReport(chunk))
                    rest -= chunk
                }
            }
        }
    }

    /** Sends one mouse report with as much accumulated motion as fits the fields. */
    private fun sendMotionChunk() {
        val chunk = motion.whole().clamp(xyLimit(), ReportEncoder.INT8_MAX)
        if (chunk.isZero) return
        motion.remove(chunk)
        send(mouseReport(chunk))
    }

    private fun xyLimit(): Int =
        if (protocolMode ==
            ProtocolMode.BOOT
        ) {
            ReportEncoder.INT8_MAX
        } else {
            ReportEncoder.INT16_MAX
        }

    private fun mouseReport(d: Delta): HidReport =
        when (protocolMode) {
            ProtocolMode.REPORT -> {
                ReportEncoder.mouse(sent.buttons, d.x.toInt(), d.y.toInt(), d.wheel.toInt(), d.pan.toInt())
            }

            ProtocolMode.BOOT -> {
                ReportEncoder.bootMouse(sent.buttons and ReportEncoder.BOOT_BUTTONS_MASK, d.x.toInt(), d.y.toInt())
            }
        }

    private fun sendNeutralReports() {
        send(ReportEncoder.keyboard(KeyboardState.NEUTRAL))
        send(ReportEncoder.neutralMouse(protocolMode))
        if (protocolMode == ProtocolMode.REPORT) send(ReportEncoder.consumer(ConsumerUsage.NONE))
    }

    private fun send(report: HidReport) {
        sink.send(report)
        lastSendNanos = scheduler.nowNanos()
    }

    /** Drops queued and accumulated input; held state is unchanged. */
    private fun clearPending() {
        tick?.cancel()
        tick = null
        queue.clear()
        motion.clear()
        target = sent
    }

    /** Drops pending input and forgets held state (used when reports are neutral again). */
    private fun resetAll() {
        clearPending()
        sent = NEUTRAL_STATE
        target = NEUTRAL_STATE
    }

    companion object {
        /** 8 ms, about 125 reports per second while input is changing. */
        const val DEFAULT_INTERVAL_NANOS = 8_000_000L

        /** Cap on queued transitions from actions, so shortcut bursts cannot build latency. */
        const val DEFAULT_MAX_QUEUED_TRANSITIONS = 32

        private val NEUTRAL_STATE = TargetState(0, KeyboardState.NEUTRAL, ConsumerUsage.NONE)
    }
}
