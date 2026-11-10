package com.mfouad.mobiletrackpad

import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mfouad.mobiletrackpad.hid.HidReport
import com.mfouad.mobiletrackpad.session.Phase
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the real UI against the debug-only simulated computer and checks the exact reports
 * that would have been sent over Bluetooth. Runs on an emulator; no Bluetooth link is involved.
 */
@RunWith(AndroidJUnit4::class)
class SimulatedSessionTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val controller get() = AppGraph.controller

    private val reports: List<HidReport>
        get() = (controller.simulatedRecorder as? RecordingOutputSink)?.reports?.toList().orEmpty()

    private fun hex(r: HidReport) = "${r.id}: ${r.toHex()}"

    private fun text(
        @StringRes id: Int,
    ) = rule.activity.getString(id)

    private fun click(
        @StringRes id: Int,
    ) {
        rule.onNodeWithText(text(id)).performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun waitFor(
        what: String,
        condition: () -> Boolean,
    ) {
        try {
            rule.waitUntil(TIMEOUT_MS, condition)
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Timed out waiting for $what. Reports: ${reports.map(::hex)}", e)
        }
    }

    @Before
    fun startSimulatedComputer() {
        controller.stop()
        waitFor("idle") { !controller.state.value.isActive }
        click(R.string.action_simulate)
        waitFor("connected") { controller.state.value.phase == Phase.CONNECTED && reports.size >= 3 }
    }

    @After
    fun stop() {
        controller.stop()
    }

    @Test
    fun firstReportsAfterConnectAreNeutral() {
        assertEquals(
            listOf("1: 00 00 00 00 00 00 00 00", "2: 00 00 00 00 00 00 00", "3: 00 00"),
            reports.take(3).map(::hex),
        )
    }

    @Test
    fun leftClickSendsPressThenRelease() {
        click(R.string.action_open_test)
        val before = reports.size
        click(R.string.test_left_click)
        waitFor("click") { reports.size >= before + 2 }
        assertEquals(
            listOf("2: 01 00 00 00 00 00 00", "2: 00 00 00 00 00 00 00"),
            reports.drop(before).map(::hex),
        )
    }

    @Test
    fun volumeUpSendsConsumerPressThenRelease() {
        click(R.string.action_open_test)
        val before = reports.size
        click(R.string.test_volume_up)
        waitFor("volume") { reports.size >= before + 2 }
        assertEquals(listOf("3: E9 00", "3: 00 00"), reports.drop(before).map(::hex))
    }

    @Test
    fun scrollDownSendsThreeDetentsDown() {
        click(R.string.action_open_test)
        val before = reports.size
        click(R.string.test_scroll_down)
        waitFor("scroll") { reports.drop(before).sumOf { it.payload[WHEEL].toInt() } == -3 }
        assertTrue(reports.drop(before).all { it.id == 2 && it.payload[0].toInt() == 0 })
    }

    @Test
    fun squareMovesAndReturnsToStart() {
        click(R.string.action_open_test)
        val before = reports.size
        click(R.string.test_square)
        // 4 sides x 25 steps x 8 counts: 200 counts per side.
        waitFor("square") { reports.drop(before).sumOf { abs16(it, X) + abs16(it, Y) } >= 800 }
        val moves = reports.drop(before)
        assertEquals(0, moves.sumOf { int16(it, X) }, "net X")
        assertEquals(0, moves.sumOf { int16(it, Y) }, "net Y")
        assertEquals(200, moves.sumOf { maxOf(0, int16(it, X)) }, "rightward travel")
    }

    @Test
    fun heldButtonIsReleasedWhenLeavingTheTestScreen() {
        click(R.string.action_open_test)
        click(R.string.test_hold_left)
        waitFor("button down") { reports.lastOrNull()?.let(::hex) == "2: 01 00 00 00 00 00 00" }
        click(R.string.action_back)
        waitFor("release") { reports.last { it.id == 2 }.let(::hex) == "2: 00 00 00 00 00 00 00" }
    }

    @Test
    fun stopSendsNeutralReportsAndEndsSession() {
        click(R.string.action_open_test)
        click(R.string.test_hold_left)
        waitFor("button down") { reports.lastOrNull()?.let(::hex) == "2: 01 00 00 00 00 00 00" }
        val recorder = controller.simulatedRecorder as RecordingOutputSink
        controller.stop()
        waitFor("idle") { !controller.state.value.isActive }
        assertEquals(
            listOf("1: 00 00 00 00 00 00 00 00", "2: 00 00 00 00 00 00 00", "3: 00 00"),
            recorder.reports.takeLast(3).map(::hex),
        )
    }

    private fun int16(
        r: HidReport,
        offset: Int,
    ): Int {
        if (r.id != 2) return 0
        val p = r.payload
        return ((p[offset].toInt() and 0xFF) or (p[offset + 1].toInt() shl 8)).toShort().toInt()
    }

    private fun abs16(
        r: HidReport,
        offset: Int,
    ) = kotlin.math.abs(int16(r, offset))

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val X = 1
        const val Y = 3
        const val WHEEL = 5
    }
}
