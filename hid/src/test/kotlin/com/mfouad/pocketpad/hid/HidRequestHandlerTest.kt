package com.mfouad.pocketpad.hid

import com.mfouad.pocketpad.gesture.FakeScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HidRequestHandlerTest {
    private val scheduler = FakeScheduler()
    private val sender = ReportSender({ true }, scheduler)
    private val handler = HidRequestHandler(sender::currentInputReport)

    private fun reportHex(reply: HidReply): String = (reply as HidReply.Report).report.let { "${it.id}: ${it.toHex()}" }

    @Test
    fun getInputReportsReturnCurrentHeldState() {
        sender.onConnected()
        sender.press(MouseButton.PRIMARY)
        sender.perform(Action.Consumer(ConsumerUsage.MUTE))
        scheduler.advanceBy(ReportSender.DEFAULT_INTERVAL_NANOS * 2) // button down, then mute down
        assertEquals("2: 01 00 00 00 00 00 00", reportHex(handler.onGetReport(HidReportType.INPUT, 2)))
        assertEquals("3: E2 00", reportHex(handler.onGetReport(HidReportType.INPUT, 3)))
        assertEquals("1: 00 00 00 00 00 00 00 00", reportHex(handler.onGetReport(HidReportType.INPUT, 1)))
    }

    @Test
    fun getInputReportsInBootMode() {
        sender.onConnected()
        sender.setProtocolMode(ProtocolMode.BOOT)
        assertEquals("2: 00 00 00", reportHex(handler.onGetReport(HidReportType.INPUT, 2)))
        assertEquals(HidReply.Handshake(HidHandshake.INVALID_REPORT_ID), handler.onGetReport(HidReportType.INPUT, 3))
    }

    @Test
    fun getUnknownOrFeatureReports() {
        assertEquals(HidReply.Handshake(HidHandshake.INVALID_REPORT_ID), handler.onGetReport(HidReportType.INPUT, 9))
        assertEquals(
            HidReply.Handshake(HidHandshake.UNSUPPORTED_REQUEST),
            handler.onGetReport(HidReportType.FEATURE, 1),
        )
        assertEquals(HidReply.Handshake(HidHandshake.INVALID_REPORT_ID), handler.onGetReport(HidReportType.OUTPUT, 2))
        assertEquals(HidReply.Handshake(HidHandshake.INVALID_PARAMETER), handler.onGetReport(7, 1))
    }

    @Test
    fun setLedReportIsAcknowledgedAndReadBack() {
        assertEquals(
            HidReply.Handshake(HidHandshake.SUCCESS),
            handler.onSetReport(HidReportType.OUTPUT, 1, byteArrayOf(0x02)),
        )
        assertEquals(0x02, handler.ledState)
        assertEquals("1: 02", reportHex(handler.onGetReport(HidReportType.OUTPUT, 1)))
    }

    @Test
    fun setLedIgnoresPaddingBits() {
        handler.onSetReport(HidReportType.OUTPUT, 1, byteArrayOf(0xFF.toByte()))
        assertEquals(0x1F, handler.ledState)
    }

    @Test
    fun invalidSetReports() {
        assertEquals(
            HidReply.Handshake(HidHandshake.INVALID_PARAMETER),
            handler.onSetReport(HidReportType.OUTPUT, 1, byteArrayOf(1, 2)),
        )
        assertEquals(
            HidReply.Handshake(HidHandshake.INVALID_PARAMETER),
            handler.onSetReport(HidReportType.OUTPUT, 1, byteArrayOf()),
        )
        assertEquals(
            HidReply.Handshake(HidHandshake.INVALID_REPORT_ID),
            handler.onSetReport(HidReportType.OUTPUT, 2, byteArrayOf(1)),
        )
        assertEquals(
            HidReply.Handshake(HidHandshake.INVALID_REPORT_ID),
            handler.onSetReport(HidReportType.INPUT, 1, ByteArray(8)),
        )
        assertEquals(
            HidReply.Handshake(HidHandshake.UNSUPPORTED_REQUEST),
            handler.onSetReport(HidReportType.FEATURE, 1, byteArrayOf(1)),
        )
        assertEquals(0, handler.ledState)
    }

    @Test
    fun interruptData() {
        assertTrue(handler.onInterruptData(1, byteArrayOf(0x01)))
        assertEquals(0x01, handler.ledState)
        assertFalse(handler.onInterruptData(2, byteArrayOf(0x01)))
        assertFalse(handler.onInterruptData(1, byteArrayOf(0x01, 0x02)))
    }
}
