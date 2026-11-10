package com.mfouad.mobiletrackpad.hid

/** Report types as used by `BluetoothHidDevice` (REPORT_TYPE_*). */
object HidReportType {
    const val INPUT = 1
    const val OUTPUT = 2
    const val FEATURE = 3
}

/** HANDSHAKE result codes, matching `BluetoothHidDevice.ERROR_RSP_*` and the BT HID profile. */
object HidHandshake {
    const val SUCCESS = 0
    const val NOT_READY = 1
    const val INVALID_REPORT_ID = 2
    const val UNSUPPORTED_REQUEST = 3
    const val INVALID_PARAMETER = 4
    const val UNKNOWN = 14
}

/** How to answer a host request. */
sealed interface HidReply {
    /** Reply with `replyReport(device, type, id, payload)`. */
    data class Report(
        val type: Int,
        val report: HidReport,
    ) : HidReply

    /** Reply with `reportError(device, code)`. */
    data class Handshake(
        val code: Int,
    ) : HidReply
}

/**
 * Decides answers to GET_REPORT, SET_REPORT and interrupt-channel data, as tabled in
 * docs/HID.md section 7. Pure logic; the Bluetooth layer performs the reply.
 *
 * @param currentInput the current input report for an ID in the current protocol mode, or
 *   null if that report does not exist in this mode.
 */
class HidRequestHandler(
    private val currentInput: (reportId: Int) -> HidReport?,
) {
    /** Last keyboard LED byte received from the host (Num, Caps, Scroll, Compose, Kana). */
    var ledState: Int = 0
        private set

    fun onGetReport(
        type: Int,
        reportId: Int,
    ): HidReply =
        when (type) {
            HidReportType.INPUT -> {
                currentInput(reportId)?.let { HidReply.Report(type, it) }
                    ?: HidReply.Handshake(HidHandshake.INVALID_REPORT_ID)
            }

            HidReportType.OUTPUT -> {
                if (reportId == HidDescriptor.REPORT_ID_KEYBOARD) {
                    HidReply.Report(type, HidReport(reportId, byteArrayOf(ledState.toByte())))
                } else {
                    HidReply.Handshake(HidHandshake.INVALID_REPORT_ID)
                }
            }

            HidReportType.FEATURE -> {
                HidReply.Handshake(HidHandshake.UNSUPPORTED_REQUEST)
            }

            else -> {
                HidReply.Handshake(HidHandshake.INVALID_PARAMETER)
            }
        }

    /** SET_REPORT always gets a handshake; the Android stack does not send one itself. */
    fun onSetReport(
        type: Int,
        reportId: Int,
        data: ByteArray,
    ): HidReply.Handshake {
        val code =
            when {
                type == HidReportType.FEATURE -> HidHandshake.UNSUPPORTED_REQUEST
                type != HidReportType.OUTPUT -> HidHandshake.INVALID_REPORT_ID
                reportId != HidDescriptor.REPORT_ID_KEYBOARD -> HidHandshake.INVALID_REPORT_ID
                data.size != HidDescriptor.KEYBOARD_OUTPUT_SIZE -> HidHandshake.INVALID_PARAMETER
                else -> HidHandshake.SUCCESS
            }
        if (code == HidHandshake.SUCCESS) ledState = data[0].toInt() and LED_MASK
        return HidReply.Handshake(code)
    }

    /** Output data on the interrupt channel: no reply is sent. Returns whether it was used. */
    fun onInterruptData(
        reportId: Int,
        data: ByteArray,
    ): Boolean {
        val accepted = reportId == HidDescriptor.REPORT_ID_KEYBOARD && data.size == HidDescriptor.KEYBOARD_OUTPUT_SIZE
        if (accepted) ledState = data[0].toInt() and LED_MASK
        return accepted
    }

    private companion object {
        /** Five LED bits; the top three are padding. */
        const val LED_MASK = 0x1F
    }
}
