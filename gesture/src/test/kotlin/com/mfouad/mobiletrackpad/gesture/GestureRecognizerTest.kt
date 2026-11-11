package com.mfouad.mobiletrackpad.gesture

import com.mfouad.mobiletrackpad.gesture.GestureEvent.ButtonDown
import com.mfouad.mobiletrackpad.gesture.GestureEvent.ButtonUp
import com.mfouad.mobiletrackpad.gesture.GestureEvent.Cancel
import com.mfouad.mobiletrackpad.gesture.GestureEvent.Move
import com.mfouad.mobiletrackpad.gesture.GestureEvent.Pinch
import com.mfouad.mobiletrackpad.gesture.GestureEvent.Rotate
import com.mfouad.mobiletrackpad.gesture.GestureEvent.Scroll
import com.mfouad.mobiletrackpad.gesture.GestureEvent.Swipe3
import com.mfouad.mobiletrackpad.gesture.PointerButton.PRIMARY
import com.mfouad.mobiletrackpad.gesture.PointerButton.SECONDARY
import com.mfouad.mobiletrackpad.gesture.PointerButton.TERTIARY
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Synthetic touch sequences at density 1 (1 dp = 1 px): tap slop 8, scroll slop 10, pinch slop
 * 28, rotate slop 15 degrees, three-finger swipe slop 48.
 */
class GestureRecognizerTest {
    private val scheduler = FakeScheduler()
    private val events = mutableListOf<GestureEvent>()
    private var config = GestureConfig.forDensity(1f)
    private val recognizer by lazy { GestureRecognizer(config, scheduler) { events += it } }

    /** Builds frames for a set of fingers; time in milliseconds of the frame clock. */
    private inner class Touch {
        var t = 0L
        val fingers = LinkedHashMap<Int, Pointer>()

        private fun frame(
            action: FrameAction,
            changed: Int? = null,
        ) = recognizer.onFrame(PointerFrame(t * MS, action, fingers.values.toList(), changed))

        fun down(
            id: Int,
            x: Float,
            y: Float,
        ) {
            fingers[id] = Pointer(id, x, y)
            frame(FrameAction.DOWN, id)
        }

        fun up(id: Int) {
            frame(FrameAction.UP, id)
            fingers.remove(id)
        }

        fun cancel() {
            frame(FrameAction.CANCEL)
            fingers.clear()
        }

        /** Moves the given fingers by (dx, dy) in [steps] frames, [stepMs] apart. */
        fun move(
            dx: Float,
            dy: Float,
            steps: Int = 10,
            stepMs: Long = 8,
            ids: Collection<Int> = fingers.keys.toList(),
        ) {
            repeat(steps) {
                t += stepMs
                ids.forEach { id ->
                    fingers[id] =
                        fingers.getValue(id).let { it.copy(x = it.x + dx / steps, y = it.y + dy / steps) }
                }
                frame(FrameAction.MOVE)
            }
        }

        fun moveTo(
            id: Int,
            x: Float,
            y: Float,
        ) {
            t += 8
            fingers[id] = fingers.getValue(id).copy(x = x, y = y)
            frame(FrameAction.MOVE)
        }

        fun pause(ms: Long) {
            t += ms
        }
    }

    private fun touch(block: Touch.() -> Unit) = Touch().apply(block)

    private fun clicks() = events.filter { it is ButtonDown || it is ButtonUp }

    private fun sumMove() = events.filterIsInstance<Move>().fold(0f to 0f) { (x, y), m -> (x + m.dx) to (y + m.dy) }

    private fun sumScroll() = events.filterIsInstance<Scroll>().fold(0f to 0f) { (x, y), s -> (x + s.dx) to (y + s.dy) }

    private fun assertNear(
        expected: Float,
        actual: Float,
        message: String = "",
    ) = assertTrue(kotlin.math.abs(expected - actual) < 0.01f, "$message expected $expected, got $actual")

    // --- tap versus movement ------------------------------------------------------------------

    @Test
    fun quickStillTouchIsLeftClick() {
        touch {
            down(0, 100f, 100f)
            pause(80)
            up(0)
        }
        assertEquals(listOf(ButtonDown(PRIMARY), ButtonUp(PRIMARY)), events)
    }

    @Test
    fun jitterInsideSlopIsStillATapAndDoesNotMove() {
        touch {
            down(0, 100f, 100f)
            move(5f, -4f, steps = 3)
            up(0)
        }
        assertEquals(listOf(ButtonDown(PRIMARY), ButtonUp(PRIMARY)), events)
    }

    @Test
    fun longTouchIsNotATap() {
        touch {
            down(0, 100f, 100f)
            pause(250)
            up(0)
        }
        assertEquals(emptyList(), events)
    }

    @Test
    fun movementBeyondSlopMovesAndDoesNotClick() {
        touch {
            down(0, 100f, 100f)
            move(120f, -30f)
            up(0)
        }
        assertEquals(emptyList(), clicks())
        val (x, y) = sumMove()
        assertNear(120f, x, "x")
        assertNear(-30f, y, "y")
    }

    @Test
    fun tapToClickCanBeTurnedOff() {
        config = config.copy(tapToClick = false)
        touch {
            down(0, 100f, 100f)
            up(0)
        }
        assertEquals(emptyList(), events)
    }

    // --- double tap and drag ------------------------------------------------------------------

    @Test
    fun twoQuickTapsAreTwoClicks() {
        touch {
            down(0, 100f, 100f)
            pause(60)
            up(0)
            pause(120)
            down(1, 102f, 101f)
            pause(60)
            up(1)
        }
        assertEquals(
            listOf(ButtonDown(PRIMARY), ButtonUp(PRIMARY), ButtonDown(PRIMARY), ButtonUp(PRIMARY)),
            events,
        )
    }

    @Test
    fun tapThenTouchAndMoveDrags() {
        touch {
            down(0, 100f, 100f)
            pause(60)
            up(0)
            pause(150)
            down(1, 100f, 100f)
            move(80f, 0f)
            up(1)
        }
        assertEquals(
            listOf(ButtonDown(PRIMARY), ButtonUp(PRIMARY), ButtonDown(PRIMARY), ButtonUp(PRIMARY)),
            clicks(),
        )
        // The drag's button goes down before the movement and up after it.
        val downIndex = events.indexOfLast { it == ButtonDown(PRIMARY) }
        val upIndex = events.lastIndexOf(ButtonUp(PRIMARY))
        assertTrue(events.subList(downIndex, upIndex).any { it is Move })
        assertNear(80f, sumMove().first)
    }

    @Test
    fun touchAfterTheDoubleTapWindowJustMoves() {
        touch {
            down(0, 100f, 100f)
            pause(60)
            up(0)
            pause(400)
            down(1, 100f, 100f)
            move(80f, 0f)
            up(1)
        }
        assertEquals(listOf(ButtonDown(PRIMARY), ButtonUp(PRIMARY)), clicks())
    }

    @Test
    fun dragLockKeepsButtonUntilNextTap() {
        config = config.copy(dragLock = true)
        touch {
            down(0, 100f, 100f)
            up(0)
            pause(100)
            down(1, 100f, 100f)
            move(50f, 0f)
            up(1)
        }
        assertEquals(listOf(ButtonDown(PRIMARY), ButtonUp(PRIMARY), ButtonDown(PRIMARY)), clicks())
        assertTrue(recognizer.isDragLocked)
        events.clear()
        touch {
            t = 2000
            // Moving while locked continues the drag without another press.
            down(2, 150f, 100f)
            move(30f, 0f)
            up(2)
            pause(500)
            // A tap releases the lock, without clicking.
            down(3, 180f, 100f)
            up(3)
        }
        assertEquals(listOf(ButtonUp(PRIMARY)), clicks())
        assertTrue(!recognizer.isDragLocked)
    }

    // --- multi-finger taps -----------------------------------------------------------------

    @Test
    fun twoFingerTapIsRightClickEvenWithStaggeredLift() {
        touch {
            down(0, 100f, 100f)
            pause(20)
            down(1, 160f, 100f)
            pause(60)
            up(0)
            pause(40)
            up(1)
        }
        assertEquals(listOf(ButtonDown(SECONDARY), ButtonUp(SECONDARY)), events)
    }

    @Test
    fun threeFingerTapIsMiddleClick() {
        touch {
            down(0, 100f, 100f)
            down(1, 160f, 100f)
            down(2, 220f, 100f)
            pause(80)
            up(2)
            up(1)
            up(0)
        }
        assertEquals(listOf(ButtonDown(TERTIARY), ButtonUp(TERTIARY)), events)
    }

    @Test
    fun slowTwoFingerTouchIsNotAClick() {
        touch {
            down(0, 100f, 100f)
            down(1, 160f, 100f)
            pause(400)
            up(0)
            up(1)
        }
        assertEquals(emptyList(), events)
    }

    // --- scroll, pinch, rotate -------------------------------------------------------------

    @Test
    fun twoFingerParallelMoveScrollsAndLocksToVertical() {
        config = config.copy(inertia = false)
        touch {
            down(0, 100f, 300f)
            down(1, 160f, 300f)
            move(6f, -120f)
            up(0)
            up(1)
        }
        assertEquals(emptyList(), clicks())
        assertTrue(events.none { it is Pinch || it is Rotate || it is Move })
        val (x, y) = sumScroll()
        assertNear(0f, x, "locked x")
        assertNear(-120f, y, "y")
    }

    @Test
    fun diagonalScrollIsNotLocked() {
        config = config.copy(inertia = false)
        touch {
            down(0, 100f, 300f)
            down(1, 160f, 300f)
            move(60f, 60f)
            up(0)
            up(1)
        }
        val (x, y) = sumScroll()
        assertNear(60f, x)
        assertNear(60f, y)
    }

    @Test
    fun spreadingFingersPinchesInsteadOfScrolling() {
        touch {
            down(0, 100f, 300f)
            down(1, 160f, 300f)
            repeat(10) {
                moveTo(0, 100f - 6f * (it + 1), 300f)
                moveTo(1, 160f + 6f * (it + 1), 300f)
            }
            up(0)
            up(1)
        }
        assertTrue(events.none { it is Scroll || it is ButtonDown })
        val scale = events.filterIsInstance<Pinch>().fold(1f) { acc, p -> acc * (1f + p.scaleDelta) }
        assertNear(180f / 60f, scale, "total scale")
    }

    @Test
    fun twistingFingersRotatesInsteadOfScrolling() {
        touch {
            down(0, 100f, 300f)
            down(1, 200f, 300f)
            // Rotate the pair 40 degrees clockwise about its centre.
            repeat(8) {
                val a = Math.toRadians(5.0 * (it + 1))
                moveTo(0, (150 - 50 * cos(a)).toFloat(), (300 - 50 * sin(a)).toFloat())
                moveTo(1, (150 + 50 * cos(a)).toFloat(), (300 + 50 * sin(a)).toFloat())
            }
            up(0)
            up(1)
        }
        assertTrue(events.none { it is Scroll || it is Pinch || it is ButtonDown })
        val total = events.filterIsInstance<Rotate>().sumOf { it.degrees.toDouble() }
        assertNear(40f, total.toFloat(), "degrees")
    }

    // --- three-finger swipes ---------------------------------------------------------------

    @Test
    fun threeFingerSwipeEmitsOneDirection() {
        touch {
            down(0, 100f, 300f)
            down(1, 150f, 300f)
            down(2, 200f, 300f)
            move(-150f, 10f)
            up(0)
            up(1)
            up(2)
        }
        assertEquals(listOf<GestureEvent>(Swipe3(SwipeDirection.LEFT)), events)
    }

    @Test
    fun threeFingerSwipeUp() {
        touch {
            down(0, 100f, 300f)
            down(1, 150f, 300f)
            down(2, 200f, 300f)
            move(5f, -100f)
            up(0)
            up(1)
            up(2)
        }
        assertEquals(listOf<GestureEvent>(Swipe3(SwipeDirection.UP)), events)
    }

    // --- pointer-count transitions ---------------------------------------------------------

    @Test
    fun secondFingerDuringPointerMotionNeverClicks() {
        touch {
            down(0, 100f, 100f)
            move(60f, 0f)
            down(1, 300f, 300f)
            pause(30)
            up(1)
            up(0)
        }
        assertEquals(emptyList(), clicks())
    }

    @Test
    fun remainingFingerAfterScrollDoesNotMoveThePointer() {
        config = config.copy(inertia = false)
        touch {
            down(0, 100f, 300f)
            down(1, 160f, 300f)
            move(0f, -60f)
            up(1)
            move(80f, 40f, ids = listOf(0))
            up(0)
        }
        assertTrue(events.none { it is Move || it is ButtonDown })
    }

    @Test
    fun liftingOneOfTwoFingersAndMovingTheOtherDoesNothing() {
        touch {
            down(0, 100f, 300f)
            down(1, 160f, 300f)
            pause(30)
            up(0)
            move(90f, 0f, ids = listOf(1))
            up(1)
        }
        assertEquals(emptyList(), events)
    }

    @Test
    fun newTouchAfterAMultiFingerGestureStartsFresh() {
        config = config.copy(inertia = false)
        touch {
            down(0, 100f, 300f)
            down(1, 160f, 300f)
            move(0f, -60f)
            up(0)
            up(1)
            pause(50)
            down(2, 100f, 100f)
            move(40f, 0f)
            up(2)
        }
        assertNear(40f, sumMove().first)
        assertEquals(emptyList(), clicks())
    }

    // --- large-contact (palm) rejection ---------------------------------------------------

    @Test
    fun largeContactIsIgnoredWhileAFingerStillWorks() {
        touch {
            fingers[9] = Pointer(9, 500f, 900f, size = 0.8f) // palm lands first
            recognizer.onFrame(PointerFrame(t * MS, FrameAction.DOWN, fingers.values.toList(), 9))
            down(0, 100f, 100f)
            move(60f, 0f, ids = listOf(0))
            up(0)
            recognizer.onFrame(PointerFrame(t * MS, FrameAction.UP, fingers.values.toList(), 9))
        }
        assertEquals(emptyList(), clicks(), "the palm does not turn a one-finger move into a two-finger gesture")
        assertNear(60f, sumMove().first)
    }

    @Test
    fun largeContactAloneDoesNotClick() {
        touch {
            fingers[9] = Pointer(9, 500f, 900f, size = 0.9f)
            recognizer.onFrame(PointerFrame(t * MS, FrameAction.DOWN, fingers.values.toList(), 9))
            pause(60)
            recognizer.onFrame(PointerFrame(t * MS, FrameAction.UP, fingers.values.toList(), 9))
        }
        assertEquals(emptyList(), events)
    }

    @Test
    fun normalContactSizeIsNotRejected() {
        touch {
            fingers[0] = Pointer(0, 100f, 100f, size = 0.2f)
            recognizer.onFrame(PointerFrame(t * MS, FrameAction.DOWN, fingers.values.toList(), 0))
            pause(60)
            up(0)
        }
        assertEquals(listOf(ButtonDown(PRIMARY), ButtonUp(PRIMARY)), events)
    }

    // --- cancellation --------------------------------------------------------------------------

    @Test
    fun platformCancelDuringDragReleasesTheButton() {
        touch {
            down(0, 100f, 100f)
            up(0)
            pause(100)
            down(1, 100f, 100f)
            move(40f, 0f)
            cancel()
        }
        assertEquals(listOf(ButtonDown(PRIMARY), ButtonUp(PRIMARY), ButtonDown(PRIMARY), ButtonUp(PRIMARY)), clicks())
        assertEquals(Cancel, events.last())
    }

    @Test
    fun cancelForgetsTapHistory() {
        touch {
            down(0, 100f, 100f)
            up(0)
        }
        recognizer.cancel()
        events.clear()
        touch {
            t = 100
            down(1, 100f, 100f)
            move(40f, 0f)
            up(1)
        }
        assertEquals(emptyList(), clicks(), "no drag after cancel")
    }

    // --- inertia ------------------------------------------------------------------------------

    private fun fling(liftPauseMs: Long = 0) =
        touch {
            down(0, 100f, 600f)
            down(1, 160f, 600f)
            move(0f, -400f, steps = 10, stepMs = 8) // 5000 px/s
            pause(liftPauseMs)
            up(0)
            up(1)
        }

    @Test
    fun flingContinuesWithDecayingScrollThenStops() {
        fling()
        val during = events.filterIsInstance<Scroll>().size
        assertTrue(recognizer.isInertiaRunning)
        scheduler.advanceBy(5_000 * MS)
        val inertial = events.filterIsInstance<Scroll>().drop(during)
        assertTrue(inertial.size > 10, "inertia emitted ${inertial.size} steps")
        assertTrue(inertial.all { it.dy < 0f && it.dx == 0f }, "same direction as the fling")
        assertTrue(kotlin.math.abs(inertial.first().dy) > kotlin.math.abs(inertial.last().dy), "decays")
        assertTrue(!recognizer.isInertiaRunning)
        assertEquals(0, scheduler.pendingCount, "nothing scheduled once stopped")
    }

    @Test
    fun staggeredLiftStillFlings() {
        touch {
            down(0, 100f, 600f)
            down(1, 160f, 600f)
            move(0f, -400f)
            up(0)
            pause(40)
            up(1)
        }
        assertTrue(recognizer.isInertiaRunning)
    }

    @Test
    fun noFlingWhenFingersRestBeforeLifting() {
        fling(liftPauseMs = 150)
        assertTrue(!recognizer.isInertiaRunning)
    }

    @Test
    fun newTouchStopsInertia() {
        fling()
        scheduler.advanceBy(50 * MS)
        val before = events.size
        touch {
            t = 1000
            down(5, 100f, 100f)
        }
        scheduler.advanceBy(1_000 * MS)
        assertEquals(before, events.size, "no events after the new touch")
        assertTrue(!recognizer.isInertiaRunning)
    }

    @Test
    fun externalCancelStopsInertia() {
        fling()
        recognizer.cancel()
        val before = events.size
        scheduler.advanceBy(1_000 * MS)
        assertEquals(before, events.size)
        assertEquals(Cancel, events.last())
    }

    @Test
    fun inertiaCanBeTurnedOff() {
        config = config.copy(inertia = false)
        fling()
        assertTrue(!recognizer.isInertiaRunning)
        assertEquals(0, scheduler.pendingCount)
    }

    private companion object {
        const val MS = 1_000_000L
    }
}
