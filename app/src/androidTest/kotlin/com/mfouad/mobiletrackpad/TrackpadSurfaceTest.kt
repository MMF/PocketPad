package com.mfouad.mobiletrackpad

import android.view.View
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mfouad.mobiletrackpad.hid.HidReport
import com.mfouad.mobiletrackpad.session.Phase
import com.mfouad.mobiletrackpad.ui.TRACKPAD_SURFACE_TAG
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Injects multi-touch input on the trackpad surface, with the debug-only simulated computer
 * connected, and checks the exact reports the full touch -> gesture -> report path produced.
 */
@RunWith(AndroidJUnit4::class)
class TrackpadSurfaceTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val controller get() = AppGraph.controller

    private val reports: List<HidReport>
        get() = (controller.simulatedRecorder as? RecordingOutputSink)?.reports?.toList().orEmpty()

    private var baseline = 0

    private val newReports get() = reports.drop(baseline)

    private val mouse get() = newReports.filter { it.id == 2 }

    private fun hex(r: HidReport) = "${r.id}: ${r.toHex()}"

    private fun click(
        @StringRes id: Int,
    ) {
        rule.onNodeWithText(rule.activity.getString(id)).performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun waitFor(
        what: String,
        condition: () -> Boolean,
    ) {
        try {
            rule.waitUntil(TIMEOUT_MS, condition)
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("Timed out waiting for $what. New reports: ${newReports.map(::hex)}", e)
        }
    }

    /** Waits until no new report has arrived for a while (inertia and queued output finished). */
    private fun settle() {
        var last = -1
        waitFor("output to settle") {
            val n = reports.size
            val stable = n == last
            last = n
            if (!stable) Thread.sleep(SETTLE_MS)
            stable
        }
    }

    private fun touch(block: TouchInjectionScope.() -> Unit) {
        rule.onNodeWithTag(TRACKPAD_SURFACE_TAG).performTouchInput(block)
        rule.waitForIdle()
    }

    private fun int16(
        r: HidReport,
        o: Int,
    ) = ((r.payload[o].toInt() and 0xFF) or (r.payload[o + 1].toInt() shl 8)).toShort().toInt()

    @Before
    fun openTrackpadWithSimulatedComputer() {
        rule.passOnboardingIfShown()
        controller.stop()
        waitFor("idle") { !controller.state.value.isActive }
        click(R.string.action_simulate)
        waitFor("connected") { controller.state.value.phase == Phase.CONNECTED && reports.size >= 3 }
        click(R.string.action_open_trackpad)
        rule.onNodeWithTag(TRACKPAD_SURFACE_TAG).assertExists()
        baseline = reports.size
    }

    @After
    fun stop() {
        controller.stop()
    }

    @Test
    fun tapIsLeftClick() {
        touch {
            down(center)
            advanceEventTime(60)
            up()
        }
        waitFor("click") { mouse.size >= 2 }
        settle()
        assertEquals(listOf("2: 01 00 00 00 00 00 00", "2: 00 00 00 00 00 00 00"), newReports.map(::hex))
    }

    @Test
    fun oneFingerMovesThePointerWithoutClicking() {
        touch {
            down(center)
            repeat(15) { moveBy(Offset(10.dp.toPx(), 0f)) }
            up()
        }
        settle()
        val dx = mouse.sumOf { int16(it, 1) }
        val dy = mouse.sumOf { int16(it, 3) }
        // 150 dp at the default 1 count per dp.
        assertTrue(dx in 145..151, "dx=$dx")
        assertEquals(0, dy)
        assertTrue(mouse.all { it.payload[0].toInt() == 0 }, "no button pressed")
    }

    @Test
    fun twoFingerTapIsRightClick() {
        touch {
            down(0, center)
            down(1, center + Offset(60.dp.toPx(), 0f))
            advanceEventTime(60)
            up(1)
            up(0)
        }
        waitFor("right click") { mouse.size >= 2 }
        settle()
        assertEquals(listOf("2: 02 00 00 00 00 00 00", "2: 00 00 00 00 00 00 00"), newReports.map(::hex))
    }

    @Test
    fun threeFingerTapIsMiddleClick() {
        touch {
            down(0, center)
            down(1, center + Offset(50.dp.toPx(), 0f))
            down(2, center + Offset(100.dp.toPx(), 0f))
            advanceEventTime(60)
            up(2)
            up(1)
            up(0)
        }
        waitFor("middle click") { mouse.size >= 2 }
        settle()
        assertEquals(listOf("2: 04 00 00 00 00 00 00", "2: 00 00 00 00 00 00 00"), newReports.map(::hex))
    }

    @Test
    fun twoFingersMovingUpScrollWheelUp() {
        touch {
            down(0, center)
            down(1, center + Offset(60.dp.toPx(), 0f))
            repeat(10) {
                updatePointerBy(0, Offset(0f, -10.dp.toPx()))
                updatePointerBy(1, Offset(0f, -10.dp.toPx()))
                move()
            }
            // Rest before lifting so there is no inertia to wait for.
            advanceEventTime(200)
            move()
            up(0)
            up(1)
        }
        settle()
        val wheel = mouse.sumOf { it.payload[5].toInt() }
        // 100 dp at 20 dp per detent.
        assertEquals(5, wheel)
        assertTrue(mouse.all { it.payload[6].toInt() == 0 && it.payload[0].toInt() == 0 && int16(it, 1) == 0 })
    }

    @Test
    fun tapThenTouchAndMoveDrags() {
        touch {
            down(center)
            advanceEventTime(60)
            up()
            advanceEventTime(120)
            down(center)
            repeat(10) { moveBy(Offset(10.dp.toPx(), 0f)) }
            up()
        }
        settle()
        val buttons = mouse.map { it.payload[0].toInt() }
        // Click (down, up), then press, movement with the button held, release.
        assertEquals(listOf(1, 0, 1), buttons.take(3))
        assertEquals(0, buttons.last())
        val moving = mouse.filter { int16(it, 1) != 0 }
        assertTrue(moving.isNotEmpty() && moving.all { it.payload[0].toInt() == 1 }, "movement carries the held button")
    }

    @Test
    fun dragIsReleasedWhenTheTrackpadStopsBeingVisible() {
        touch {
            down(center)
            advanceEventTime(60)
            up()
            advanceEventTime(120)
            down(center)
            repeat(5) { moveBy(Offset(10.dp.toPx(), 0f)) }
        }
        waitFor("button held") {
            mouse
                .lastOrNull()
                ?.payload
                ?.get(0)
                ?.toInt() == 1
        }
        // Same lifecycle path as the screen turning off or switching apps.
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        waitFor("release") { mouse.last().payload[0].toInt() == 0 }
    }

    @Test
    fun screenStaysOnOnlyWhileTheTrackpadIsShown() {
        val content = rule.activity.findViewById<View>(android.R.id.content)

        fun keepOn() =
            generateSequence(listOf(content)) { views ->
                views
                    .flatMap {
                        (it as? android.view.ViewGroup)?.let { g -> List(g.childCount, g::getChildAt) }
                            ?: emptyList()
                    }.ifEmpty { null }
            }.flatten()
                .any { it.keepScreenOn }
        assertTrue(keepOn(), "keep-screen-on while trackpad is shown and connected")
        // The trackpad has no scroll container, so click Back directly.
        rule.onNodeWithText(rule.activity.getString(R.string.action_back)).performClick()
        rule.waitForIdle()
        assertTrue(!keepOn(), "released after leaving the trackpad")
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L // generous: the emulator can be slow under load
        const val SETTLE_MS = 300L
    }
}
