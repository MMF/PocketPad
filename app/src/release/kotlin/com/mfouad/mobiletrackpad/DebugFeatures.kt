package com.mfouad.mobiletrackpad

import com.mfouad.mobiletrackpad.hid.OutputSink

/** Release builds: no simulated host. */
object DebugFeatures {
    const val SIMULATED_HOST_AVAILABLE = false

    fun simulatedSink(): OutputSink? = null
}
