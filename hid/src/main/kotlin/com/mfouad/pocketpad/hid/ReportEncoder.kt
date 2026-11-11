package com.mfouad.pocketpad.hid

/**
 * A report ready for `BluetoothHidDevice.sendReport(device, id, payload)`.
 *
 * [payload] never contains the report ID; the ID travels separately in [id].
 */
class HidReport(
    val id: Int,
    payload: ByteArray,
) {
    private val bytes = payload.copyOf()

    val payload: ByteArray get() = bytes.copyOf()

    val size: Int get() = bytes.size

    fun toHex(): String = bytes.joinToString(" ") { "%02X".format(it.toInt() and BYTE_MASK) }

    override fun equals(other: Any?): Boolean = other is HidReport && other.id == id && other.bytes.contentEquals(bytes)

    override fun hashCode(): Int = 31 * id + bytes.contentHashCode()

    override fun toString(): String = "HidReport(id=$id, ${toHex()})"
}

/** State of the keyboard report: a modifier bitmask and up to six pressed key usages. */
data class KeyboardState(
    val modifiers: Int = 0,
    val keys: List<Int> = emptyList(),
) {
    init {
        require(modifiers in 0..BYTE_MASK) { "Modifier mask out of range: $modifiers" }
        require(keys.size <= MAX_KEYS) { "At most $MAX_KEYS keys, got ${keys.size}" }
        require(keys.all { it in 1..KeyUsage.MAX }) { "Key usage out of range: $keys" }
    }

    val isNeutral: Boolean get() = modifiers == 0 && keys.isEmpty()

    /** This state with [other]'s modifiers and keys added (keys de-duplicated, order kept). */
    fun plus(other: KeyboardState): KeyboardState =
        KeyboardState(modifiers or other.modifiers, (keys + other.keys).distinct())

    companion object {
        const val MAX_KEYS = 6
        val NEUTRAL = KeyboardState()
    }
}

/** Encodes report payloads exactly as laid out in docs/HID.md section 4 and section 6. */
object ReportEncoder {
    const val INT16_MAX = 32767
    const val INT8_MAX = 127
    private const val BUTTONS_MASK_REPORT = 0x1F
    const val BOOT_BUTTONS_MASK = 0x07

    // Byte offsets within payloads (docs/HID.md section 4).
    private const val KEYBOARD_MODIFIERS = 0
    private const val KEYBOARD_FIRST_KEY = 2
    private const val MOUSE_BUTTONS = 0
    private const val MOUSE_X = 1
    private const val MOUSE_Y = 3
    private const val MOUSE_WHEEL = 5
    private const val MOUSE_PAN = 6

    fun keyboard(state: KeyboardState): HidReport {
        val p = ByteArray(HidDescriptor.KEYBOARD_INPUT_SIZE)
        p[KEYBOARD_MODIFIERS] = state.modifiers.toByte()
        // Byte 1 is the reserved byte and stays 0.
        state.keys.forEachIndexed { i, key -> p[KEYBOARD_FIRST_KEY + i] = key.toByte() }
        return HidReport(HidDescriptor.REPORT_ID_KEYBOARD, p)
    }

    /** Report-mode mouse: 5 buttons, int16 X/Y, int8 wheel, int8 AC Pan. */
    fun mouse(
        buttons: Int,
        dx: Int,
        dy: Int,
        wheel: Int = 0,
        pan: Int = 0,
    ): HidReport {
        require(buttons and BUTTONS_MASK_REPORT.inv() == 0) { "Invalid button mask: $buttons" }
        requireRange("dx", dx, INT16_MAX)
        requireRange("dy", dy, INT16_MAX)
        requireRange("wheel", wheel, INT8_MAX)
        requireRange("pan", pan, INT8_MAX)
        val p = ByteArray(HidDescriptor.MOUSE_INPUT_SIZE)
        p[MOUSE_BUTTONS] = buttons.toByte()
        putInt16(p, MOUSE_X, dx)
        putInt16(p, MOUSE_Y, dy)
        p[MOUSE_WHEEL] = wheel.toByte()
        p[MOUSE_PAN] = pan.toByte()
        return HidReport(HidDescriptor.REPORT_ID_MOUSE, p)
    }

    /** Boot-protocol mouse: 3 buttons, int8 X/Y. */
    fun bootMouse(
        buttons: Int,
        dx: Int,
        dy: Int,
    ): HidReport {
        require(buttons and BOOT_BUTTONS_MASK.inv() == 0) { "Invalid boot button mask: $buttons" }
        requireRange("dx", dx, INT8_MAX)
        requireRange("dy", dy, INT8_MAX)
        return HidReport(
            HidDescriptor.REPORT_ID_MOUSE,
            byteArrayOf(buttons.toByte(), dx.toByte(), dy.toByte()),
        )
    }

    fun consumer(usage: Int): HidReport {
        require(usage in 0..ConsumerUsage.MAX) { "Consumer usage out of range: $usage" }
        val p = ByteArray(HidDescriptor.CONSUMER_INPUT_SIZE)
        putInt16(p, 0, usage)
        return HidReport(HidDescriptor.REPORT_ID_CONSUMER, p)
    }

    fun neutralMouse(mode: ProtocolMode): HidReport =
        when (mode) {
            ProtocolMode.REPORT -> mouse(0, 0, 0)
            ProtocolMode.BOOT -> bootMouse(0, 0, 0)
        }

    private fun requireRange(
        name: String,
        value: Int,
        max: Int,
    ) = require(value in -max..max) { "$name out of range -$max..$max: $value" }

    /** Little-endian: least significant byte at the lower index. */
    private fun putInt16(
        p: ByteArray,
        offset: Int,
        value: Int,
    ) {
        p[offset] = (value and BYTE_MASK).toByte()
        p[offset + 1] = ((value shr Byte.SIZE_BITS) and BYTE_MASK).toByte()
    }
}

internal const val BYTE_MASK = 0xFF
