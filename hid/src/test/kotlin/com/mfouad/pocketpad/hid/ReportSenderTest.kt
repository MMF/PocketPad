package com.mfouad.pocketpad.hid

import com.mfouad.pocketpad.gesture.FakeScheduler
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportSenderTest {
    private class Recorder : OutputSink {
        val reports = mutableListOf<HidReport>()

        override fun send(report: HidReport): Boolean {
            reports += report
            return true
        }

        fun hex() = reports.map { "${it.id}: ${it.toHex()}" }

        fun take(): List<String> = hex().also { reports.clear() }
    }

    private val interval = ReportSender.DEFAULT_INTERVAL_NANOS
    private lateinit var scheduler: FakeScheduler
    private lateinit var sink: Recorder
    private lateinit var sender: ReportSender

    private val neutralKeyboard = "1: 00 00 00 00 00 00 00 00"
    private val neutralMouse = "2: 00 00 00 00 00 00 00"
    private val neutralConsumer = "3: 00 00"

    @BeforeTest
    fun setUp() {
        scheduler = FakeScheduler()
        sink = Recorder()
        sender = ReportSender(sink, scheduler)
    }

    private fun connect() {
        sender.onConnected()
        sink.reports.clear()
        // Let the post-connect interval elapse so the next send is immediate.
        scheduler.advanceBy(interval)
    }

    private fun drain() = scheduler.advanceBy(interval * 100)

    // --- connection and neutral reports -------------------------------------------------

    @Test
    fun connectSendsNeutralReportsForAllThreeIdsFirst() {
        sender.onConnected()
        assertEquals(listOf(neutralKeyboard, neutralMouse, neutralConsumer), sink.hex())
    }

    @Test
    fun inputWhileDisconnectedIsIgnoredAndNotReplayed() {
        sender.move(50f, 0f)
        sender.scroll(3f, 0f)
        sender.press(MouseButton.PRIMARY)
        assertFalse(sender.perform(Action.Consumer(ConsumerUsage.MUTE)))
        drain()
        assertEquals(emptyList(), sink.hex())
        sender.onConnected()
        drain()
        assertEquals(listOf(neutralKeyboard, neutralMouse, neutralConsumer), sink.hex())
    }

    @Test
    fun unexpectedDisconnectClearsEverythingAndSendsNothing() {
        connect()
        sender.press(MouseButton.PRIMARY)
        scheduler.runDue()
        sender.move(1000f, 0f)
        sender.perform(Action.KeyChord(setOf(Modifier.LEFT_CTRL), listOf(KeyUsage.TAB)))
        sink.reports.clear()

        sender.onDisconnected()
        drain()
        assertEquals(emptyList(), sink.hex())
        assertTrue(sender.isIdle)
        assertEquals(0, scheduler.pendingCount)

        // Reconnect: only neutral reports, no replay of the button, movement or shortcut.
        sender.onConnected()
        drain()
        assertEquals(listOf(neutralKeyboard, neutralMouse, neutralConsumer), sink.hex())
    }

    @Test
    fun shutdownSendsAllNeutralReportsWhileConnected() {
        connect()
        sender.press(MouseButton.SECONDARY)
        scheduler.runDue()
        sink.reports.clear()
        sender.shutdown()
        assertEquals(listOf(neutralKeyboard, neutralMouse, neutralConsumer), sink.hex())
    }

    @Test
    fun releaseAllReleasesOnlyWhatIsHeldImmediately() {
        connect()
        sender.press(MouseButton.PRIMARY)
        scheduler.runDue()
        sender.move(5f, 0f)
        sink.reports.clear()
        sender.releaseAll()
        assertEquals(listOf(neutralMouse), sink.hex())
        drain()
        assertEquals(listOf(neutralMouse), sink.hex(), "pending movement must be discarded")
    }

    // --- idle behaviour ------------------------------------------------------------------

    @Test
    fun idleSenderSchedulesNoWork() {
        connect()
        assertTrue(sender.isIdle)
        assertEquals(0, scheduler.pendingCount)
        sender.move(0.4f, -0.3f) // below one count: nothing to send yet
        assertTrue(sender.isIdle)
        assertEquals(0, scheduler.pendingCount)
        drain()
        assertEquals(emptyList(), sink.hex())
    }

    @Test
    fun stopsSchedulingOnceEverythingIsSent() {
        connect()
        sender.move(3f, 0f)
        drain()
        assertEquals(listOf("2: 00 03 00 00 00 00 00"), sink.hex())
        assertTrue(sender.isIdle)
        assertEquals(0, scheduler.pendingCount)
    }

    // --- accumulation and splitting -------------------------------------------------------

    @Test
    fun fractionalMovementAccumulatesAndPreservesTotal() {
        connect()
        var totalX = 0
        repeat(10) {
            sender.move(0.35f, 0f)
            scheduler.advanceBy(interval)
        }
        drain()
        sink.reports.forEach { totalX += readInt16(it.payload, 1) }
        // 10 * 0.35 = 3.5: three whole counts sent, 0.5 kept.
        assertEquals(3, totalX)
        sender.move(0.6f, 0f)
        drain()
        assertEquals(4, sink.reports.sumOf { readInt16(it.payload, 1) })
    }

    @Test
    fun negativeFractionsTruncateTowardZero() {
        connect()
        sender.move(-2.7f, 0f)
        drain()
        assertEquals(listOf(-2), sink.reports.map { readInt16(it.payload, 1) })
        sender.move(-0.3f, 0f)
        drain()
        assertEquals(listOf(-2, -1), sink.reports.map { readInt16(it.payload, 1) })
    }

    @Test
    fun largeDeltasAreSplitIntoInt16Range() {
        connect()
        sender.move(40000f, -70000f)
        drain()
        val xy = sink.reports.map { readInt16(it.payload, 1) to readInt16(it.payload, 3) }
        assertEquals(listOf(32767 to -32767, 7233 to -32767, 0 to -4466), xy)
        assertEquals(40000, xy.sumOf { it.first })
        assertEquals(-70000, xy.sumOf { it.second })
    }

    @Test
    fun scrollAccumulatesAndSplitsIntoInt8Range() {
        connect()
        sender.scroll(300.5f, -0.6f)
        sender.scroll(0f, -0.6f)
        drain()
        val wp = sink.reports.map { it.payload[5].toInt() to it.payload[6].toInt() }
        assertEquals(listOf(127 to -1, 127 to 0, 46 to 0), wp)
    }

    @Test
    fun motionChunksAreSpacedByTheInterval() {
        connect()
        sender.move(70000f, 0f)
        val times = mutableListOf<Long>()
        repeat(5) {
            val before = sink.reports.size
            scheduler.advanceBy(1_000_000)
            if (sink.reports.size > before) times += scheduler.nowNanos()
        }
        drain()
        assertTrue(times.zipWithNext().all { (a, b) -> b - a >= interval }, "sends too close together: $times")
    }

    // --- ordering -------------------------------------------------------------------------

    @Test
    fun movementAndTransitionsKeepTheirOrder() {
        connect()
        sender.move(5f, 0f)
        sender.press(MouseButton.PRIMARY)
        sender.move(3f, 0f)
        sender.release(MouseButton.PRIMARY)
        drain()
        assertEquals(
            listOf(
                "2: 00 05 00 00 00 00 00",
                "2: 01 00 00 00 00 00 00",
                "2: 01 03 00 00 00 00 00", // movement after the press carries the held button
                "2: 00 00 00 00 00 00 00",
            ),
            sink.hex(),
        )
    }

    @Test
    fun eachTransitionGetsItsOwnTick() {
        connect()
        sender.perform(Action.MouseClick(MouseButton.PRIMARY))
        scheduler.runDue()
        assertEquals(listOf("2: 01 00 00 00 00 00 00"), sink.take())
        scheduler.advanceBy(interval - 1)
        assertEquals(emptyList(), sink.take())
        scheduler.advanceBy(1)
        assertEquals(listOf(neutralMouse), sink.take())
    }

    // --- actions of each kind -------------------------------------------------------------

    @Test
    fun mouseButtonAction() {
        connect()
        assertTrue(sender.perform(Action.MouseClick(MouseButton.SECONDARY)))
        drain()
        assertEquals(listOf("2: 02 00 00 00 00 00 00", neutralMouse), sink.hex())
    }

    @Test
    fun keyboardShortcutPressesModifiersFirstAndReleasesKeysFirst() {
        connect()
        assertTrue(sender.perform(Action.KeyChord(setOf(Modifier.LEFT_CTRL), listOf(KeyUsage.EQUAL))))
        drain()
        assertEquals(
            listOf(
                "1: 01 00 00 00 00 00 00 00",
                "1: 01 00 2E 00 00 00 00 00",
                "1: 01 00 00 00 00 00 00 00",
                neutralKeyboard,
            ),
            sink.hex(),
        )
    }

    @Test
    fun keyWithoutModifiers() {
        connect()
        sender.perform(Action.KeyChord(keys = listOf(KeyUsage.PAGE_DOWN)))
        drain()
        assertEquals(listOf("1: 00 00 4E 00 00 00 00 00", neutralKeyboard), sink.hex())
    }

    @Test
    fun consumerAction() {
        connect()
        assertTrue(sender.perform(Action.Consumer(ConsumerUsage.VOLUME_INCREMENT)))
        drain()
        assertEquals(listOf("3: E9 00", neutralConsumer), sink.hex())
    }

    @Test
    fun modifierWheelAction() {
        connect()
        assertTrue(sender.perform(Action.ModifierWheel(setOf(Modifier.LEFT_CTRL), wheel = 1)))
        drain()
        assertEquals(
            listOf("1: 01 00 00 00 00 00 00 00", "2: 00 00 00 00 00 01 00", neutralKeyboard),
            sink.hex(),
        )
    }

    @Test
    fun sequenceActionKeepsOrder() {
        connect()
        val seq =
            Action.Sequence(
                listOf(
                    Action.Consumer(ConsumerUsage.MUTE),
                    Action.MouseClick(MouseButton.TERTIARY),
                    Action.KeyChord(keys = listOf(KeyUsage.ESCAPE)),
                ),
            )
        assertTrue(sender.perform(seq))
        drain()
        assertEquals(
            listOf(
                "3: E2 00",
                neutralConsumer,
                "2: 04 00 00 00 00 00 00",
                neutralMouse,
                "1: 00 00 29 00 00 00 00 00",
                neutralKeyboard,
            ),
            sink.hex(),
        )
    }

    @Test
    fun largeMovementBeforeATransitionIsSplitAndSentFirst() {
        connect()
        sender.move(40000f, 0f)
        sender.press(MouseButton.PRIMARY)
        drain()
        assertEquals(
            listOf("2: 00 FF 7F 00 00 00 00", "2: 00 41 1C 00 00 00 00", "2: 01 00 00 00 00 00 00"),
            sink.hex(),
        )
    }

    @Test
    fun clickWhileHoldingAnotherButtonRestoresTheHeldButton() {
        connect()
        sender.press(MouseButton.PRIMARY)
        sender.perform(Action.MouseClick(MouseButton.SECONDARY))
        drain()
        assertEquals(
            listOf("2: 01 00 00 00 00 00 00", "2: 03 00 00 00 00 00 00", "2: 01 00 00 00 00 00 00"),
            sink.hex(),
        )
    }

    @Test
    fun queueIsBounded() {
        connect()
        val chord = Action.KeyChord(setOf(Modifier.LEFT_ALT), listOf(KeyUsage.TAB)) // 4 transitions
        var accepted = 0
        while (sender.perform(chord)) accepted++
        assertEquals(ReportSender.DEFAULT_MAX_QUEUED_TRANSITIONS / 4, accepted)
        drain()
        assertTrue(sender.perform(chord), "queue accepts work again once drained")
    }

    // --- scrolling never leaks into modifier shortcuts ------------------------------------

    @Test
    fun pendingScrollIsDiscardedWhenAModifierShortcutIsQueued() {
        connect()
        sender.scroll(0.9f, 0f) // fraction pending, not yet sent
        sender.perform(Action.KeyChord(setOf(Modifier.LEFT_CTRL), listOf(KeyUsage.EQUAL)))
        sender.scroll(0.2f, 0f) // would complete a detent; must be ignored while Ctrl is queued
        drain()
        assertTrue(sink.reports.none { it.id == 2 }, "no wheel report expected: ${sink.hex()}")
    }

    @Test
    fun scrollWhileModifierHeldIsIgnoredAndResumesAfterRelease() {
        connect()
        sender.perform(Action.KeyChord(setOf(Modifier.LEFT_CTRL), listOf(KeyUsage.EQUAL)))
        scheduler.runDue() // Ctrl down sent
        sender.scroll(5f, 0f)
        drain()
        assertTrue(sink.reports.none { it.id == 2 })
        sink.reports.clear()
        sender.scroll(2f, 0f)
        drain()
        assertEquals(listOf("2: 00 00 00 00 00 02 00"), sink.hex())
    }

    // --- boot protocol --------------------------------------------------------------------

    @Test
    fun switchingToBootSendsNeutralReportsInBootFormat() {
        connect()
        sender.setProtocolMode(ProtocolMode.BOOT)
        assertEquals(listOf(neutralKeyboard, "2: 00 00 00"), sink.hex())
    }

    @Test
    fun bootModeSplitsDeltasIntoInt8RangePreservingTotal() {
        connect()
        sender.setProtocolMode(ProtocolMode.BOOT)
        sink.reports.clear()
        scheduler.advanceBy(interval)
        sender.move(300f, -130f)
        drain()
        assertEquals(listOf("2: 00 7F 81", "2: 00 7F FD", "2: 00 2E 00"), sink.hex())
        assertTrue(sink.reports.all { it.size == HidDescriptor.BOOT_MOUSE_INPUT_SIZE })
    }

    @Test
    fun bootModeDropsWheelPanConsumerAndButtons4And5() {
        connect()
        sender.setProtocolMode(ProtocolMode.BOOT)
        sink.reports.clear()
        sender.scroll(3f, 3f)
        sender.press(MouseButton.BUTTON4)
        assertFalse(sender.perform(Action.Consumer(ConsumerUsage.MUTE)))
        assertFalse(sender.perform(Action.MouseClick(MouseButton.BUTTON5)))
        assertFalse(sender.perform(Action.ModifierWheel(setOf(Modifier.LEFT_CTRL), wheel = 1)))
        assertFalse(
            sender.perform(
                Action.Sequence(listOf(Action.MouseClick(MouseButton.PRIMARY), Action.Consumer(ConsumerUsage.MUTE))),
            ),
        )
        drain()
        assertEquals(emptyList(), sink.hex())
        // Keyboard shortcuts and buttons 1-3 still work.
        assertTrue(sender.perform(Action.MouseClick(MouseButton.PRIMARY)))
        assertTrue(sender.perform(Action.KeyChord(keys = listOf(KeyUsage.ESCAPE))))
        drain()
        assertEquals(
            listOf("2: 01 00 00", "2: 00 00 00", "1: 00 00 29 00 00 00 00 00", neutralKeyboard),
            sink.hex(),
        )
    }

    @Test
    fun protocolChangeDiscardsPendingMovement() {
        connect()
        sender.move(500f, 0f)
        sender.setProtocolMode(ProtocolMode.BOOT)
        sink.reports.clear()
        drain()
        assertEquals(emptyList(), sink.hex())
    }

    @Test
    fun reconnectResetsToReportMode() {
        connect()
        sender.setProtocolMode(ProtocolMode.BOOT)
        sender.onDisconnected()
        sender.onConnected()
        assertEquals(ProtocolMode.REPORT, sender.protocolMode)
    }

    // --- report IDs and sizes -------------------------------------------------------------

    @Test
    fun everySentReportHasAValidIdAndSizeForItsMode() {
        connect()
        sender.move(40000f, 10f)
        sender.scroll(2f, -2f)
        sender.perform(Action.Consumer(ConsumerUsage.PLAY_PAUSE))
        sender.perform(Action.KeyChord(setOf(Modifier.LEFT_GUI), listOf(KeyUsage.TAB)))
        drain()
        val expected = mapOf(1 to 8, 2 to 7, 3 to 2)
        assertTrue(sink.reports.isNotEmpty())
        sink.reports.forEach { assertEquals(expected[it.id], it.size, "report $it") }
    }

    private fun readInt16(
        p: ByteArray,
        offset: Int,
    ): Int = ((p[offset].toInt() and 0xFF) or (p[offset + 1].toInt() shl 8)).toShort().toInt()
}
