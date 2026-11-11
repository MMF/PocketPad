package com.mfouad.mobiletrackpad.hid

import com.mfouad.mobiletrackpad.gesture.FakeScheduler
import com.mfouad.mobiletrackpad.gesture.GestureEvent
import com.mfouad.mobiletrackpad.gesture.SwipeDirection
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShortcutMappingTest {
    private val reports = mutableListOf<HidReport>()
    private val scheduler = FakeScheduler()
    private val sender =
        ReportSender({
            reports += it
            true
        }, scheduler)
    private var shortcutCalls = 0
    private val results = mutableListOf<Triple<GestureSlot, Action, Boolean>>()
    private val mapper =
        TrackpadMapper(
            sender,
            PointerSettings(countsPerPx = 1f, pxPerDetent = 10f),
            onShortcut = { shortcutCalls++ },
            onShortcutResult = { slot, action, ok -> results += Triple(slot, action, ok) },
        )

    @BeforeTest
    fun connect() {
        sender.onConnected()
        reports.clear()
        scheduler.advanceBy(ReportSender.DEFAULT_INTERVAL_NANOS)
    }

    private fun drain() = scheduler.advanceBy(1_000_000_000L)

    private fun hex() = reports.map { "${it.id}: ${it.toHex()}" }

    private fun profileWith(
        slot: GestureSlot,
        action: Action,
    ) = HostProfile("t", "Test").with(slot, action)

    // --- each kind of action ---------------------------------------------------------------

    @Test
    fun mouseButtonAction() {
        mapper.profile = profileWith(GestureSlot.SWIPE3_UP, Action.MouseClick(MouseButton.BUTTON4))
        mapper.onEvent(GestureEvent.Swipe3(SwipeDirection.UP))
        drain()
        assertEquals(listOf("2: 08 00 00 00 00 00 00", "2: 00 00 00 00 00 00 00"), hex())
    }

    @Test
    fun keyboardShortcutAction() {
        mapper.profile = HostProfiles.default(HostProfiles.MACOS)
        mapper.onEvent(GestureEvent.Swipe3(SwipeDirection.UP)) // Ctrl + Up
        drain()
        assertEquals(
            listOf(
                "1: 01 00 00 00 00 00 00 00",
                "1: 01 00 52 00 00 00 00 00",
                "1: 01 00 00 00 00 00 00 00",
                "1: 00 00 00 00 00 00 00 00",
            ),
            hex(),
        )
    }

    @Test
    fun consumerAction() {
        mapper.profile = profileWith(GestureSlot.SWIPE3_DOWN, Action.Consumer(ConsumerUsage.PLAY_PAUSE))
        mapper.onEvent(GestureEvent.Swipe3(SwipeDirection.DOWN))
        drain()
        assertEquals(listOf("3: CD 00", "3: 00 00"), hex())
    }

    @Test
    fun sequenceAction() {
        val seq =
            Action.Sequence(
                listOf(Action.Consumer(ConsumerUsage.MUTE), Action.KeyChord(keys = listOf(KeyUsage.ESCAPE))),
            )
        mapper.profile = profileWith(GestureSlot.SWIPE3_LEFT, seq)
        mapper.onEvent(GestureEvent.Swipe3(SwipeDirection.LEFT))
        drain()
        assertEquals(listOf("3: E2 00", "3: 00 00", "1: 00 00 29 00 00 00 00 00", "1: 00 00 00 00 00 00 00 00"), hex())
    }

    @Test
    fun unassignedSlotDoesNothing() {
        mapper.profile = HostProfile("empty", "Empty")
        mapper.onEvent(GestureEvent.Swipe3(SwipeDirection.RIGHT))
        mapper.onEvent(GestureEvent.Rotate(90f, start = true))
        drain()
        assertTrue(reports.isEmpty())
        assertEquals(0, shortcutCalls)
    }

    // --- pinch and rotate steps --------------------------------------------------------------

    @Test
    fun pinchOutPerformsZoomInPerStep() {
        mapper.profile = HostProfiles.default(HostProfiles.WINDOWS) // Ctrl + wheel
        // Spread to 1.25^2 = 1.5625x in small increments: two zoom steps.
        mapper.onEvent(GestureEvent.Pinch(0.1f, start = true))
        var scale = 1.1
        while (scale < 1.5625) {
            val next = minOf(scale * 1.05, 1.5625)
            mapper.onEvent(GestureEvent.Pinch((next / scale - 1).toFloat()))
            scale = next
        }
        drain()
        assertEquals(2, results.count { it.first == GestureSlot.PINCH_OUT })
        val wheel = reports.filter { it.id == 2 }.sumOf { it.payload[5].toInt() }
        assertEquals(2, wheel)
        assertTrue(reports.filter { it.id == 2 && it.payload[5].toInt() != 0 }.isNotEmpty())
    }

    @Test
    fun pinchInPerformsZoomOut() {
        mapper.profile = HostProfiles.default(HostProfiles.MACOS)
        mapper.onEvent(GestureEvent.Pinch(-0.3f, start = true)) // 0.7x: beyond one step of 1/1.25 = 0.8
        drain()
        assertEquals(listOf(GestureSlot.PINCH_IN), results.map { it.first })
        assertEquals("1: 08 00 2D 00 00 00 00 00", hex()[1], "Cmd + -")
    }

    @Test
    fun newPinchStartsFromZero() {
        mapper.profile = HostProfiles.default(HostProfiles.WINDOWS)
        mapper.onEvent(GestureEvent.Pinch(0.2f, start = true)) // 0.18 of 0.22 needed
        mapper.onEvent(GestureEvent.Pinch(0.1f, start = true)) // restarts: not added to the previous
        assertEquals(0, results.size)
    }

    @Test
    fun rotationStepsBothWays() {
        mapper.profile =
            HostProfile("r", "R")
                .with(GestureSlot.ROTATE_CLOCKWISE, Action.KeyChord(keys = listOf(KeyUsage.RIGHT_ARROW)))
                .with(GestureSlot.ROTATE_COUNTERCLOCKWISE, Action.KeyChord(keys = listOf(KeyUsage.LEFT_ARROW)))
        mapper.onEvent(GestureEvent.Rotate(20f, start = true))
        mapper.onEvent(GestureEvent.Rotate(45f)) // 65 total: two clockwise steps
        mapper.onEvent(GestureEvent.Rotate(-60f)) // 5 left over, then -55: one counter-clockwise step
        assertEquals(
            listOf(GestureSlot.ROTATE_CLOCKWISE, GestureSlot.ROTATE_CLOCKWISE, GestureSlot.ROTATE_COUNTERCLOCKWISE),
            results.map { it.first },
        )
    }

    // --- shortcut safety ---------------------------------------------------------------------

    @Test
    fun shortcutCallbackRunsBeforeEachAction() {
        mapper.profile = HostProfiles.default(HostProfiles.WINDOWS)
        mapper.onEvent(GestureEvent.Swipe3(SwipeDirection.UP))
        assertEquals(1, shortcutCalls)
    }

    @Test
    fun residualScrollNeverReachesAModifierShortcut() {
        mapper.profile = HostProfiles.default(HostProfiles.WINDOWS)
        mapper.onEvent(GestureEvent.Scroll(0f, -9f)) // 0.9 detent pending
        mapper.onEvent(GestureEvent.Swipe3(SwipeDirection.UP)) // Win + Tab
        mapper.onEvent(GestureEvent.Scroll(0f, -5f)) // would complete a detent while Win is queued
        drain()
        assertTrue(reports.none { it.id == 2 && it.payload[5].toInt() != 0 }, "no wheel report: ${hex()}")
    }

    @Test
    fun unavailableInBootModeIsReportedNotSent() {
        sender.setProtocolMode(ProtocolMode.BOOT)
        reports.clear()
        mapper.profile = profileWith(GestureSlot.SWIPE3_UP, Action.Consumer(ConsumerUsage.MUTE))
        mapper.onEvent(GestureEvent.Swipe3(SwipeDirection.UP))
        drain()
        assertTrue(reports.isEmpty())
        assertEquals(false, results.single().third)
    }

    // --- profiles and codec --------------------------------------------------------------------

    @Test
    fun everyBuiltInProfileRoundTripsThroughTheCodec() {
        HostProfiles.defaults.forEach { p ->
            assertEquals(p, ProfileCodec.decode(p.id, ProfileCodec.encode(p), "fallback"))
        }
    }

    @Test
    fun builtInsHaveNoRotationByDefault() {
        HostProfiles.defaults.forEach { p ->
            assertNull(p.actionFor(GestureSlot.ROTATE_CLOCKWISE), p.id)
            assertNull(p.actionFor(GestureSlot.ROTATE_COUNTERCLOCKWISE), p.id)
        }
    }

    @Test
    fun macosZoomUsesKeysNotCommandWheel() {
        val p = HostProfiles.default(HostProfiles.MACOS)
        assertEquals(
            Action.KeyChord(setOf(Modifier.LEFT_GUI), listOf(KeyUsage.EQUAL)),
            p.actionFor(GestureSlot.PINCH_OUT),
        )
    }

    @Test
    fun actionCodecCoversEveryKind() {
        val actions =
            listOf(
                Action.MouseClick(MouseButton.BUTTON5),
                Action.KeyChord(setOf(Modifier.LEFT_CTRL, Modifier.LEFT_SHIFT), listOf(0x2E, 0x04)),
                Action.KeyChord(keys = listOf(KeyUsage.F1)),
                Action.KeyChord(setOf(Modifier.LEFT_GUI)),
                Action.Consumer(ConsumerUsage.AC_ZOOM_IN),
                Action.ModifierWheel(setOf(Modifier.LEFT_CTRL), wheel = -1),
                Action.Sequence(listOf(Action.Consumer(ConsumerUsage.MUTE), Action.MouseClick(MouseButton.PRIMARY))),
            )
        actions.forEach { assertEquals(it, ActionCodec.decode(ActionCodec.encode(it)), ActionCodec.encode(it)) }
        assertEquals("keys LEFT_CTRL,LEFT_SHIFT 2E,04", ActionCodec.encode(actions[1]))
    }

    @Test
    fun damagedTextDecodesSafely() {
        listOf("", "nonsense", "keys", "consumer ZZ", "click NOPE", "keys LEFT_CTRL 100").forEach {
            assertNull(ActionCodec.decode(it), it)
        }
        val p =
            ProfileCodec.decode(
                "x",
                "name=Mine\nPINCH_OUT=garbage\nNOT_A_SLOT=click PRIMARY\nSWIPE3_UP=consumer E2",
                "X",
            )
        assertEquals(HostProfile("x", "Mine", mapOf(GestureSlot.SWIPE3_UP to Action.Consumer(ConsumerUsage.MUTE))), p)
    }
}
