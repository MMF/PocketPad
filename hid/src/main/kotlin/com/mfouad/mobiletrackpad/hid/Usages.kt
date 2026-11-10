package com.mfouad.mobiletrackpad.hid

/** Mouse buttons, as bits of byte 0 of the mouse report (Button page usages 1..5). */
enum class MouseButton(
    val mask: Int,
    val availableInBoot: Boolean,
) {
    PRIMARY(0x01, true),
    SECONDARY(0x02, true),
    TERTIARY(0x04, true),
    BUTTON4(0x08, false),
    BUTTON5(0x10, false),
}

/** Keyboard modifier bits (byte 0 of the keyboard report; usages 0xE0..0xE7). */
enum class Modifier(
    val mask: Int,
) {
    LEFT_CTRL(0x01),
    LEFT_SHIFT(0x02),
    LEFT_ALT(0x04),
    LEFT_GUI(0x08),
    RIGHT_CTRL(0x10),
    RIGHT_SHIFT(0x20),
    RIGHT_ALT(0x40),
    RIGHT_GUI(0x80),
    ;

    companion object {
        fun maskOf(modifiers: Set<Modifier>): Int = modifiers.fold(0) { acc, m -> acc or m.mask }
    }
}

/**
 * Keyboard/Keypad page (0x07) usage IDs from HID Usage Tables 1.7 section 10.
 *
 * These are physical key positions. The host maps them to characters with its own layout,
 * so letters and punctuation depend on the host's keyboard layout.
 */
object KeyUsage {
    const val A = 0x04
    const val Z = 0x1D
    const val DIGIT_1 = 0x1E
    const val DIGIT_0 = 0x27
    const val ENTER = 0x28
    const val ESCAPE = 0x29
    const val BACKSPACE = 0x2A
    const val TAB = 0x2B
    const val SPACE = 0x2C
    const val MINUS = 0x2D
    const val EQUAL = 0x2E
    const val F1 = 0x3A
    const val F12 = 0x45
    const val HOME = 0x4A
    const val PAGE_UP = 0x4B
    const val DELETE_FORWARD = 0x4C
    const val END = 0x4D
    const val PAGE_DOWN = 0x4E
    const val RIGHT_ARROW = 0x4F
    const val LEFT_ARROW = 0x50
    const val DOWN_ARROW = 0x51
    const val UP_ARROW = 0x52
    const val KEYPAD_MINUS = 0x56
    const val KEYPAD_PLUS = 0x57

    /** Highest usage the descriptor's key array can carry. */
    const val MAX = 0xFF

    fun letter(c: Char): Int {
        require(c in 'a'..'z') { "Not a lowercase letter: $c" }
        return A + (c - 'a')
    }

    private const val FUNCTION_KEY_COUNT = 12

    fun function(n: Int): Int {
        require(n in 1..FUNCTION_KEY_COUNT) { "Function key out of range: $n" }
        return F1 + n - 1
    }
}

/** Consumer page (0x0C) usage IDs from HID Usage Tables 1.7 section 15. */
object ConsumerUsage {
    const val NONE = 0x0000
    const val SCAN_NEXT_TRACK = 0x00B5
    const val SCAN_PREVIOUS_TRACK = 0x00B6
    const val PLAY_PAUSE = 0x00CD
    const val MUTE = 0x00E2
    const val VOLUME_INCREMENT = 0x00E9
    const val VOLUME_DECREMENT = 0x00EA
    const val AC_ZOOM_IN = 0x022D
    const val AC_ZOOM_OUT = 0x022E

    /** Highest usage the descriptor's consumer array can carry (Logical/Usage Maximum). */
    const val MAX = 0x03FF
}
