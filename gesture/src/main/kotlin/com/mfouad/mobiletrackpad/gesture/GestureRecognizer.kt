package com.mfouad.mobiletrackpad.gesture

import kotlin.math.abs
import kotlin.math.max

/**
 * Turns pointer frames into [GestureEvent]s. See docs/ARCHITECTURE.md section 4.3.
 *
 * - One finger: moves after leaving the tap slop; a short, still touch is a left click.
 * - Tap, then touch again within the double-tap window and move: drag with the left button.
 * - Two or three fingers that stay still and lift quickly: right or middle click.
 * - Two fingers: scroll, pinch or rotate, whichever crosses its threshold first; the choice
 *   holds until the finger count changes.
 * - Three fingers moving: one swipe event.
 * - After any multi-finger gesture, remaining fingers are ignored until all have lifted, so
 *   lifting fingers one by one never clicks or jumps the pointer.
 *
 * Not thread-safe; call from one thread. Timing uses frame timestamps only; [scheduler] drives
 * inertia.
 */
class GestureRecognizer(
    config: GestureConfig,
    private val scheduler: Scheduler,
    private val emit: (GestureEvent) -> Unit,
) {
    private enum class Mode { IDLE, PENDING, POINTER, DRAG, MULTI_PENDING, SCROLL, PINCH, ROTATE, SETTLE }

    var config: GestureConfig = config
        set(value) {
            field = value
            velocity = VelocityTracker(value.velocityWindowNanos)
        }

    /** Called with a short description whenever the gesture mode changes (for diagnostics). */
    var onModeChange: ((String) -> Unit)? = null

    private var mode = Mode.IDLE
        set(value) {
            if (field != value) onModeChange?.invoke("$field -> $value")
            field = value
        }

    private var positions: Map<Int, Pointer> = emptyMap()
    private var previous: Map<Int, Pointer> = emptyMap()
    private val starts = HashMap<Int, Pointer>()

    private var downTime = 0L
    private var maxCount = 0
    private var moved = false
    private var primaryId = -1
    private var dragArmed = false
    private var lockedSession = false
    private var lastTapUpTime: Long? = null
    private var firstLiftTime: Long? = null

    private var buttonHeld = false

    /** True while drag lock keeps the left button held between touches. */
    var isDragLocked: Boolean = false
        private set

    private var baseCentroid = Pt.ZERO
    private var lastCentroid = Pt.ZERO
    private var baseDistance = 0f
    private var lastDistance = 0f
    private var baseAngle = 0f
    private var lastAngle = 0f
    private var axis = Axis.FREE

    private var velocity = VelocityTracker(config.velocityWindowNanos)
    private val inertia = InertiaRunner(scheduler) { d -> emitScroll(d) }

    val isInertiaRunning: Boolean get() = inertia.isRunning

    fun onFrame(f: PointerFrame) {
        when (f.action) {
            FrameAction.DOWN -> onDown(f)
            FrameAction.MOVE -> onMove(f)
            FrameAction.UP -> onUp(f)
            FrameAction.CANCEL -> cancel()
        }
    }

    /** Ends everything: stops inertia, releases a held button, forgets tap history. */
    fun cancel() {
        inertia.stop()
        releaseButton()
        resetSession()
        lastTapUpTime = null
        emit(GestureEvent.Cancel)
    }

    /** Stops inertia without other effects (for example before a shortcut is sent). */
    fun stopInertia() = inertia.stop()

    // --- frame handlers ---------------------------------------------------------------------

    private fun onDown(f: PointerFrame) {
        val id = f.changedId ?: return
        val p = f.pointers.firstOrNull { it.id == id } ?: return
        setPositions(f)
        if (mode == Mode.IDLE) startSession(f.timeNanos, p) else addPointer(p)
    }

    private fun onMove(f: PointerFrame) {
        setPositions(f)
        when (mode) {
            Mode.PENDING -> pendingMove()
            Mode.POINTER, Mode.DRAG -> emitPrimaryDelta()
            Mode.MULTI_PENDING -> classifyMulti()
            Mode.SCROLL -> scrollStep(f.timeNanos)
            Mode.PINCH -> pinchStep()
            Mode.ROTATE -> rotateStep()
            Mode.IDLE, Mode.SETTLE -> Unit
        }
    }

    private fun onUp(f: PointerFrame) {
        val id = f.changedId ?: return
        setPositions(f)
        positions = positions - id
        previous = previous - id
        if (positions.isEmpty()) endSession(f.timeNanos) else partialUp(id, f.timeNanos)
    }

    // --- session lifecycle -------------------------------------------------------------------

    private fun startSession(
        t: Long,
        p: Pointer,
    ) {
        inertia.stop()
        resetSession()
        mode = Mode.PENDING
        downTime = t
        maxCount = 1
        primaryId = p.id
        starts[p.id] = p
        lockedSession = isDragLocked
        dragArmed =
            !lockedSession && config.tapToClick && lastTapUpTime?.let { t - it <= config.doubleTapWindowNanos } == true
    }

    private fun addPointer(p: Pointer) {
        starts[p.id] = p
        maxCount = max(maxCount, positions.size)
        when (mode) {
            Mode.PENDING, Mode.POINTER -> {
                if (mode == Mode.POINTER) moved = true
                mode = Mode.MULTI_PENDING
                resetMultiBaseline()
            }

            Mode.MULTI_PENDING -> {
                resetMultiBaseline()
            }

            Mode.SCROLL, Mode.PINCH, Mode.ROTATE -> {
                mode = Mode.SETTLE
            }

            // A finger added during a drag is ignored; the drag continues with the first finger.
            Mode.DRAG, Mode.SETTLE, Mode.IDLE -> {
                Unit
            }
        }
    }

    private fun partialUp(
        id: Int,
        t: Long,
    ) {
        when (mode) {
            Mode.SCROLL -> {
                firstLiftTime = t
                mode = Mode.SETTLE
            }

            Mode.PINCH, Mode.ROTATE -> {
                mode = Mode.SETTLE
            }

            Mode.MULTI_PENDING -> {
                if (positions.size >= 2) resetMultiBaseline()
            }

            Mode.DRAG -> {
                if (id == primaryId) {
                    finishDrag()
                    mode = Mode.SETTLE
                }
            }

            else -> {
                Unit
            }
        }
    }

    private fun endSession(t: Long) {
        val duration = t - downTime
        var tapped = false
        when (mode) {
            Mode.PENDING -> tapped = endPending(duration)
            Mode.DRAG -> finishDrag()
            Mode.MULTI_PENDING -> endMultiPending(duration)
            Mode.SCROLL -> startInertia(t)
            Mode.SETTLE -> firstLiftTime?.let { first -> if (t - first <= config.liftGraceNanos) startInertia(first) }
            else -> Unit
        }
        lastTapUpTime = if (tapped) t else null
        resetSession()
    }

    /** Returns true if this was a one-finger tap that clicked. */
    private fun endPending(duration: Long): Boolean {
        val isTap = !moved && duration <= config.tapTimeoutNanos
        return when {
            lockedSession -> {
                if (isTap) releaseButton()
                false
            }

            isTap && config.tapToClick -> {
                click(PointerButton.PRIMARY)
                true
            }

            else -> {
                false
            }
        }
    }

    private fun endMultiPending(duration: Long) {
        if (moved || duration > config.multiTapTimeoutNanos || !config.tapToClick) return
        when (maxCount) {
            2 -> click(PointerButton.SECONDARY)
            THREE -> click(PointerButton.TERTIARY)
        }
    }

    private fun resetSession() {
        mode = Mode.IDLE
        starts.clear()
        maxCount = 0
        moved = false
        dragArmed = false
        lockedSession = false
        firstLiftTime = null
        axis = Axis.FREE
        velocity.clear()
    }

    // --- one finger -----------------------------------------------------------------------

    private fun pendingMove() {
        val p = positions[primaryId] ?: return
        val start = starts[primaryId] ?: return
        val travel = p.pt() - start.pt()
        if (travel.length <= config.tapSlopPx) return
        moved = true
        mode =
            if (dragArmed || lockedSession) {
                pressButton()
                isDragLocked = false
                Mode.DRAG
            } else {
                Mode.POINTER
            }
        emit(GestureEvent.Move(travel.x, travel.y))
    }

    private fun emitPrimaryDelta() {
        val now = positions[primaryId] ?: return
        val before = previous[primaryId] ?: return
        val d = now.pt() - before.pt()
        if (!d.isZero) emit(GestureEvent.Move(d.x, d.y))
    }

    private fun finishDrag() {
        if (config.dragLock) {
            isDragLocked = true
        } else {
            releaseButton()
        }
    }

    // --- several fingers ----------------------------------------------------------------------

    private fun resetMultiBaseline() {
        baseCentroid = centroid(positions.values)
        lastCentroid = baseCentroid
        pair()?.let { (a, b) ->
            baseDistance = (b.pt() - a.pt()).length
            lastDistance = baseDistance
            baseAngle = angleDegrees(a, b)
            lastAngle = baseAngle
        }
        velocity.clear()
    }

    private fun classifyMulti() {
        if (positions.values.any { p -> starts[p.id]?.let { (p.pt() - it.pt()).length > config.tapSlopPx } == true }) {
            moved = true
        }
        val c = centroid(positions.values)
        val travel = c - baseCentroid
        if (positions.size >= THREE) {
            if (travel.length > config.swipe3SlopPx) {
                emit(GestureEvent.Swipe3(swipeDirection(travel)))
                mode = Mode.SETTLE
            }
            return
        }
        val (a, b) = pair() ?: return
        val distance = (b.pt() - a.pt()).length
        val angle = angleDegrees(a, b)
        val scrollRatio = travel.length / config.scrollSlopPx
        val pinchRatio = abs(distance - baseDistance) / config.pinchSlopPx
        val rotateRatio = abs(angleDelta(baseAngle, angle)) / config.rotateSlopDegrees
        val best = maxOf(scrollRatio, pinchRatio, rotateRatio)
        if (best < 1f) return
        when (best) {
            scrollRatio -> beginScroll(travel, c)
            pinchRatio -> beginPinch(distance)
            else -> beginRotate(angle)
        }
    }

    private fun beginScroll(
        travel: Pt,
        c: Pt,
    ) {
        mode = Mode.SCROLL
        axis = lockAxis(travel, config.axisLockRatio)
        lastCentroid = c
        emitScroll(travel)
    }

    private fun beginPinch(distance: Float) {
        mode = Mode.PINCH
        if (baseDistance > 0f) emit(GestureEvent.Pinch(distance / baseDistance - 1f))
        lastDistance = distance
    }

    private fun beginRotate(angle: Float) {
        mode = Mode.ROTATE
        emit(GestureEvent.Rotate(angleDelta(baseAngle, angle)))
        lastAngle = angle
    }

    private fun scrollStep(t: Long) {
        val c = centroid(positions.values)
        velocity.add(t, c)
        val d = c - lastCentroid
        lastCentroid = c
        emitScroll(d)
    }

    private fun pinchStep() {
        val (a, b) = pair() ?: return
        val distance = (b.pt() - a.pt()).length
        if (lastDistance > 0f && distance != lastDistance) emit(GestureEvent.Pinch(distance / lastDistance - 1f))
        lastDistance = distance
    }

    private fun rotateStep() {
        val (a, b) = pair() ?: return
        val angle = angleDegrees(a, b)
        val d = angleDelta(lastAngle, angle)
        if (d != 0f) emit(GestureEvent.Rotate(d))
        lastAngle = angle
    }

    private fun startInertia(liftTime: Long) {
        if (!config.inertia) return
        val v = velocity.velocity(liftTime)?.constrain(axis) ?: return
        if (v.length >= config.inertiaMinVelocityPxPerSec) inertia.start(v, config)
    }

    private fun emitScroll(d: Pt) {
        val c = d.constrain(axis)
        if (!c.isZero) emit(GestureEvent.Scroll(c.x, c.y))
    }

    // --- helpers -------------------------------------------------------------------------------

    /** The two lowest-id pointers, for two-finger measurements. */
    private fun pair(): Pair<Pointer, Pointer>? {
        val sorted = positions.values.sortedBy { it.id }
        return if (sorted.size >= 2) sorted[0] to sorted[1] else null
    }

    private fun setPositions(f: PointerFrame) {
        previous = positions
        positions = f.pointers.associateBy { it.id }
    }

    private fun click(button: PointerButton) {
        emit(GestureEvent.ButtonDown(button))
        emit(GestureEvent.ButtonUp(button))
    }

    private fun pressButton() {
        if (!buttonHeld) {
            buttonHeld = true
            emit(GestureEvent.ButtonDown(PointerButton.PRIMARY))
        }
    }

    private fun releaseButton() {
        if (buttonHeld) {
            buttonHeld = false
            emit(GestureEvent.ButtonUp(PointerButton.PRIMARY))
        }
        isDragLocked = false
    }

    private companion object {
        const val THREE = 3
    }
}
