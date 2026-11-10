package com.mfouad.mobiletrackpad.hid

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Expected bytes below are written out by hand from docs/HID.md section 4. */
class ReportEncoderTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun keyboardLayoutAndId() {
        val r =
            ReportEncoder.keyboard(
                KeyboardState(Modifier.maskOf(setOf(Modifier.LEFT_CTRL, Modifier.LEFT_SHIFT)), listOf(0x04, 0x05)),
            )
        assertEquals(1, r.id)
        assertContentEquals(bytes(0x03, 0x00, 0x04, 0x05, 0x00, 0x00, 0x00, 0x00), r.payload)
    }

    @Test
    fun keyboardModifierBitOrder() {
        val all = Modifier.entries.map { ReportEncoder.keyboard(KeyboardState(it.mask)).payload[0].toInt() and 0xFF }
        assertEquals(listOf(0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80), all)
    }

    @Test
    fun keyboardSixKeysAndLimits() {
        val r = ReportEncoder.keyboard(KeyboardState(0x80, listOf(0x04, 0x05, 0x06, 0x07, 0x08, 0xFF)))
        assertContentEquals(bytes(0x80, 0x00, 0x04, 0x05, 0x06, 0x07, 0x08, 0xFF), r.payload)
        assertFailsWith<IllegalArgumentException> { KeyboardState(0, List(7) { 0x04 }) }
        assertFailsWith<IllegalArgumentException> { KeyboardState(0x100) }
        assertFailsWith<IllegalArgumentException> { KeyboardState(0, listOf(0x100)) }
        assertFailsWith<IllegalArgumentException> { KeyboardState(0, listOf(0)) }
    }

    @Test
    fun neutralKeyboardIsAllZero() {
        assertContentEquals(ByteArray(8), ReportEncoder.keyboard(KeyboardState.NEUTRAL).payload)
    }

    @Test
    fun mouseLayoutLittleEndianAndSigned() {
        val r = ReportEncoder.mouse(MouseButton.PRIMARY.mask or MouseButton.BUTTON5.mask, 1, -1, -127, 127)
        assertEquals(2, r.id)
        assertContentEquals(bytes(0x11, 0x01, 0x00, 0xFF, 0xFF, 0x81, 0x7F), r.payload)
    }

    @Test
    fun mouseInt16Extremes() {
        assertContentEquals(
            bytes(0x00, 0xFF, 0x7F, 0x01, 0x80, 0x00, 0x00),
            ReportEncoder.mouse(0, 32767, -32767).payload,
        )
        assertContentEquals(
            bytes(0x00, 0x34, 0x12, 0xCC, 0xED, 0x00, 0x00),
            ReportEncoder.mouse(0, 0x1234, -0x1234).payload,
        )
    }

    @Test
    fun mouseButtonBits() {
        val masks = MouseButton.entries.map { ReportEncoder.mouse(it.mask, 0, 0).payload[0].toInt() }
        assertEquals(listOf(0x01, 0x02, 0x04, 0x08, 0x10), masks)
        assertContentEquals(bytes(0x1F, 0, 0, 0, 0, 0, 0), ReportEncoder.mouse(0x1F, 0, 0).payload)
    }

    @Test
    fun mouseRejectsOutOfRange() {
        assertFailsWith<IllegalArgumentException> { ReportEncoder.mouse(0, 32768, 0) }
        assertFailsWith<IllegalArgumentException> { ReportEncoder.mouse(0, -32768, 0) }
        assertFailsWith<IllegalArgumentException> { ReportEncoder.mouse(0, 0, 0, wheel = -128) }
        assertFailsWith<IllegalArgumentException> { ReportEncoder.mouse(0, 0, 0, pan = 128) }
        assertFailsWith<IllegalArgumentException> { ReportEncoder.mouse(0x20, 0, 0) }
    }

    @Test
    fun bootMouse() {
        val r = ReportEncoder.bootMouse(0x07, 127, -127)
        assertEquals(2, r.id)
        assertContentEquals(bytes(0x07, 0x7F, 0x81), r.payload)
        assertContentEquals(bytes(0x00, 0x00, 0x00), ReportEncoder.neutralMouse(ProtocolMode.BOOT).payload)
        assertFailsWith<IllegalArgumentException> { ReportEncoder.bootMouse(0x08, 0, 0) }
        assertFailsWith<IllegalArgumentException> { ReportEncoder.bootMouse(0, 128, 0) }
        assertFailsWith<IllegalArgumentException> { ReportEncoder.bootMouse(0, 0, -128) }
    }

    @Test
    fun consumer() {
        assertEquals(3, ReportEncoder.consumer(0).id)
        assertContentEquals(bytes(0xE9, 0x00), ReportEncoder.consumer(ConsumerUsage.VOLUME_INCREMENT).payload)
        assertContentEquals(bytes(0xEA, 0x00), ReportEncoder.consumer(ConsumerUsage.VOLUME_DECREMENT).payload)
        assertContentEquals(bytes(0xE2, 0x00), ReportEncoder.consumer(ConsumerUsage.MUTE).payload)
        assertContentEquals(bytes(0xCD, 0x00), ReportEncoder.consumer(ConsumerUsage.PLAY_PAUSE).payload)
        assertContentEquals(bytes(0xB5, 0x00), ReportEncoder.consumer(ConsumerUsage.SCAN_NEXT_TRACK).payload)
        assertContentEquals(bytes(0xB6, 0x00), ReportEncoder.consumer(ConsumerUsage.SCAN_PREVIOUS_TRACK).payload)
        assertContentEquals(bytes(0x2D, 0x02), ReportEncoder.consumer(ConsumerUsage.AC_ZOOM_IN).payload)
        assertContentEquals(bytes(0x00, 0x00), ReportEncoder.consumer(ConsumerUsage.NONE).payload)
        assertFailsWith<IllegalArgumentException> { ReportEncoder.consumer(0x400) }
        assertFailsWith<IllegalArgumentException> { ReportEncoder.consumer(-1) }
    }

    @Test
    fun payloadNeverContainsReportIdAndIsDefensivelyCopied() {
        val r = ReportEncoder.consumer(ConsumerUsage.MUTE)
        assertEquals(2, r.size)
        r.payload[0] = 0
        assertContentEquals(bytes(0xE2, 0x00), r.payload)
        assertEquals("E2 00", r.toHex())
    }
}
