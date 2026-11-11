package com.mfouad.pocketpad.hid

/**
 * Destination for HID reports: Bluetooth on a phone, a recorder in tests and on the emulator.
 *
 * Gesture recognition and mapping never depend on which implementation is used.
 */
fun interface OutputSink {
    /**
     * Submits one report. A `true` result means the transport accepted it for sending; it is
     * not proof that the host received or processed it.
     */
    fun send(report: HidReport): Boolean
}
