package com.mfouad.pocketpad.hid

import com.mfouad.pocketpad.gesture.FakeScheduler
import com.mfouad.pocketpad.gesture.GestureEvent
import com.mfouad.pocketpad.gesture.PointerButton
import com.mfouad.pocketpad.gesture.SwipeDirection
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackpadMapperTest {
    private val reports = mutableListOf<HidReport>()
    private val scheduler = FakeScheduler()
    private val sender =
        ReportSender({
            reports += it
            true
        }, scheduler)
    private val mapper = TrackpadMapper(sender, PointerSettings(countsPerPx = 0.5f, pxPerDetent = 10f))

    @BeforeTest
    fun connect() {
        sender.onConnected()
        reports.clear()
        scheduler.advanceBy(ReportSender.DEFAULT_INTERVAL_NANOS)
    }

    private fun drain() = scheduler.advanceBy(1_000_000_000L)

    private fun hex() = reports.map { "${it.id}: ${it.toHex()}" }

    private fun int16(
        r: HidReport,
        o: Int,
    ) = ((r.payload[o].toInt() and 0xFF) or (r.payload[o + 1].toInt() shl 8)).toShort().toInt()

    @Test
    fun moveIsScaledBySensitivity() {
        mapper.onEvent(GestureEvent.Move(40f, -20f))
        drain()
        assertEquals(20, reports.sumOf { int16(it, 1) })
        assertEquals(-10, reports.sumOf { int16(it, 3) })
    }

    @Test
    fun accelerationAddsGainForLargeSteps() {
        mapper.settings = mapper.settings.copy(acceleration = 1f, accelerationRefPx = 40f)
        mapper.onEvent(GestureEvent.Move(40f, 0f)) // full extra gain: 40 * 0.5 * 2
        mapper.onEvent(GestureEvent.Move(4f, 0f)) // 4 * 0.5 * 1.1
        drain()
        assertEquals(42, reports.sumOf { int16(it, 1) })
    }

    @Test
    fun buttonsMapToMouseButtons() {
        mapper.onEvent(GestureEvent.ButtonDown(PointerButton.SECONDARY))
        mapper.onEvent(GestureEvent.ButtonUp(PointerButton.SECONDARY))
        mapper.onEvent(GestureEvent.ButtonDown(PointerButton.TERTIARY))
        mapper.onEvent(GestureEvent.ButtonUp(PointerButton.TERTIARY))
        drain()
        assertEquals(
            listOf(
                "2: 02 00 00 00 00 00 00",
                "2: 00 00 00 00 00 00 00",
                "2: 04 00 00 00 00 00 00",
                "2: 00 00 00 00 00 00 00",
            ),
            hex(),
        )
    }

    @Test
    fun fingersUpScrollWheelUpAndFingersRightPanRight() {
        mapper.onEvent(GestureEvent.Scroll(dx = 30f, dy = -20f))
        drain()
        assertEquals(listOf("2: 00 00 00 00 00 02 03"), hex())
    }

    @Test
    fun reverseScrollInvertsBothAxes() {
        mapper.settings = mapper.settings.copy(reverseScroll = true)
        mapper.onEvent(GestureEvent.Scroll(dx = 30f, dy = -20f))
        drain()
        assertEquals(listOf("2: 00 00 00 00 00 FE FD"), hex())
    }

    @Test
    fun smallScrollStepsAccumulateIntoDetents() {
        repeat(25) { mapper.onEvent(GestureEvent.Scroll(0f, -1f)) } // 25 px = 2.5 detents
        drain()
        assertEquals(2, reports.sumOf { it.payload[5].toInt() })
    }

    @Test
    fun cancelReleasesHeldButton() {
        mapper.onEvent(GestureEvent.ButtonDown(PointerButton.PRIMARY))
        drain()
        reports.clear()
        mapper.onEvent(GestureEvent.Cancel)
        assertEquals(listOf("2: 00 00 00 00 00 00 00"), hex())
    }

    @Test
    fun smallPinchAndRotateBelowOneStepSendNothing() {
        mapper.onEvent(GestureEvent.Pinch(0.2f, start = true))
        mapper.onEvent(GestureEvent.Rotate(10f, start = true))
        drain()
        assertTrue(reports.isEmpty())
    }
}
