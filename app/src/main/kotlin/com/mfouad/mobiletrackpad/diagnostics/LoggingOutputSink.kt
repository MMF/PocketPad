package com.mfouad.mobiletrackpad.diagnostics

import com.mfouad.mobiletrackpad.hid.HidReport
import com.mfouad.mobiletrackpad.hid.OutputSink

/** Logs every report (ID and payload in hex) and whether the transport accepted it. */
class LoggingOutputSink(
    private val delegate: OutputSink,
    private val log: DiagnosticsLog,
) : OutputSink {
    override fun send(report: HidReport): Boolean {
        val submitted = delegate.send(report)
        log.log(DiagnosticsLog.Category.REPORT) {
            "R${report.id} ${report.toHex()}${if (submitted) "" else "  (not submitted)"}"
        }
        return submitted
    }
}
