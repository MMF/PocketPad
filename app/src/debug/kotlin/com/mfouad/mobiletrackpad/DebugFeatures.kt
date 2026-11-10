package com.mfouad.mobiletrackpad

import com.mfouad.mobiletrackpad.hid.HidReport
import com.mfouad.mobiletrackpad.hid.OutputSink
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Debug builds only: a simulated host that records reports instead of sending them over
 * Bluetooth, so the full input-to-report path can run on the emulator. Release builds get a
 * version of this object with the simulation disabled.
 */
object DebugFeatures {
    const val SIMULATED_HOST_AVAILABLE = true

    fun simulatedSink(): OutputSink = RecordingOutputSink()
}

/** Records every report it is given. Used by the simulated host and by instrumented tests. */
class RecordingOutputSink : OutputSink {
    val reports: MutableList<HidReport> = CopyOnWriteArrayList()

    override fun send(report: HidReport): Boolean {
        reports += report
        return true
    }
}
