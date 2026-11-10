package com.mfouad.mobiletrackpad.hid

/**
 * The composite HID report descriptor registered with the Bluetooth stack.
 *
 * Byte-by-byte annotations are in docs/HID.md section 3. Hosts cache this descriptor when
 * pairing: once released, any change requires users to re-pair.
 */
object HidDescriptor {
    const val REPORT_ID_KEYBOARD = 1
    const val REPORT_ID_MOUSE = 2
    const val REPORT_ID_CONSUMER = 3

    /** Payload sizes in bytes, excluding the report ID. */
    const val KEYBOARD_INPUT_SIZE = 8
    const val KEYBOARD_OUTPUT_SIZE = 1
    const val MOUSE_INPUT_SIZE = 7
    const val CONSUMER_INPUT_SIZE = 2
    const val BOOT_MOUSE_INPUT_SIZE = 3

    /** Maximum descriptor length accepted by BluetoothHidDeviceAppSdpSettings. */
    const val MAX_SDP_DESCRIPTOR_LENGTH = 2048

    private val KEYBOARD =
        hex(
            "05 01 09 06 A1 01 85 01",
            "05 07 19 E0 29 E7 15 00 25 01 75 01 95 08 81 02",
            "95 01 75 08 81 01",
            "95 05 75 01 05 08 19 01 29 05 91 02",
            "95 01 75 03 91 01",
            "95 06 75 08 15 00 26 FF 00 05 07 19 00 29 FF 81 00",
            "C0",
        )

    private val MOUSE =
        hex(
            "05 01 09 02 A1 01 85 02 09 01 A1 00",
            "05 09 19 01 29 05 15 00 25 01 95 05 75 01 81 02",
            "95 01 75 03 81 01",
            "05 01 09 30 09 31 16 01 80 26 FF 7F 75 10 95 02 81 06",
            "09 38 15 81 25 7F 75 08 95 01 81 06",
            "05 0C 0A 38 02 95 01 81 06",
            "C0 C0",
        )

    private val CONSUMER =
        hex(
            "05 0C 09 01 A1 01 85 03",
            "15 00 26 FF 03 19 00 2A FF 03 75 10 95 01 81 00",
            "C0",
        )

    private val BYTES = KEYBOARD + MOUSE + CONSUMER

    /** A fresh copy of the descriptor bytes. */
    fun bytes(): ByteArray = BYTES.copyOf()

    private fun hex(vararg lines: String): ByteArray =
        lines
            .flatMap { it.trim().split(Regex("\\s+")) }
            .map { it.toInt(radix = 16).toByte() }
            .toByteArray()
}

/** Protocol mode selected by the host. Report mode is the default after connection. */
enum class ProtocolMode { REPORT, BOOT }
