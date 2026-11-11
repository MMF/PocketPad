package com.mfouad.pocketpad

import com.mfouad.pocketpad.hid.OutputSink

/** Release builds: no simulated host. */
object DebugFeatures {
    const val SIMULATED_HOST_AVAILABLE = false

    fun simulatedSink(): OutputSink? = null
}
