package com.mfouad.mobiletrackpad.ui

import android.content.Context
import androidx.annotation.StringRes
import com.mfouad.mobiletrackpad.R
import com.mfouad.mobiletrackpad.hid.Action
import com.mfouad.mobiletrackpad.hid.ConsumerUsage
import com.mfouad.mobiletrackpad.hid.GestureSlot
import com.mfouad.mobiletrackpad.hid.HostProfiles
import com.mfouad.mobiletrackpad.hid.KeyUsage
import com.mfouad.mobiletrackpad.hid.Modifier
import com.mfouad.mobiletrackpad.hid.MouseButton

/** User-facing names for actions, keys and gesture slots. */
object ActionText {
    /** Keys offered by the shortcut editor, in display order. */
    val keyChoices: List<Int> =
        buildList {
            addAll(KeyUsage.A..KeyUsage.Z)
            addAll(KeyUsage.DIGIT_1..KeyUsage.DIGIT_0)
            addAll(KeyUsage.F1..KeyUsage.F12)
            addAll(
                listOf(
                    KeyUsage.LEFT_ARROW,
                    KeyUsage.RIGHT_ARROW,
                    KeyUsage.UP_ARROW,
                    KeyUsage.DOWN_ARROW,
                    KeyUsage.TAB,
                    KeyUsage.ESCAPE,
                    KeyUsage.ENTER,
                    KeyUsage.SPACE,
                    KeyUsage.BACKSPACE,
                    KeyUsage.DELETE_FORWARD,
                    KeyUsage.HOME,
                    KeyUsage.END,
                    KeyUsage.PAGE_UP,
                    KeyUsage.PAGE_DOWN,
                    KeyUsage.MINUS,
                    KeyUsage.EQUAL,
                    KeyUsage.KEYPAD_PLUS,
                    KeyUsage.KEYPAD_MINUS,
                ),
            )
        }

    val mediaChoices: List<Int> =
        listOf(
            ConsumerUsage.PLAY_PAUSE,
            ConsumerUsage.SCAN_NEXT_TRACK,
            ConsumerUsage.SCAN_PREVIOUS_TRACK,
            ConsumerUsage.MUTE,
            ConsumerUsage.VOLUME_INCREMENT,
            ConsumerUsage.VOLUME_DECREMENT,
            ConsumerUsage.AC_ZOOM_IN,
            ConsumerUsage.AC_ZOOM_OUT,
        )

    /** The modifiers offered by the editor (left-hand keys; hosts treat both sides alike). */
    val modifierChoices: List<Modifier> =
        listOf(Modifier.LEFT_CTRL, Modifier.LEFT_SHIFT, Modifier.LEFT_ALT, Modifier.LEFT_GUI)

    @StringRes
    fun slotName(slot: GestureSlot): Int =
        when (slot) {
            GestureSlot.PINCH_OUT -> R.string.slot_pinch_out
            GestureSlot.PINCH_IN -> R.string.slot_pinch_in
            GestureSlot.ROTATE_CLOCKWISE -> R.string.slot_rotate_cw
            GestureSlot.ROTATE_COUNTERCLOCKWISE -> R.string.slot_rotate_ccw
            GestureSlot.SWIPE3_UP -> R.string.slot_swipe_up
            GestureSlot.SWIPE3_DOWN -> R.string.slot_swipe_down
            GestureSlot.SWIPE3_LEFT -> R.string.slot_swipe_left
            GestureSlot.SWIPE3_RIGHT -> R.string.slot_swipe_right
        }

    @StringRes
    fun modifierName(
        m: Modifier,
        profileId: String? = null,
    ): Int =
        when (m) {
            Modifier.LEFT_CTRL -> R.string.mod_ctrl
            Modifier.LEFT_SHIFT -> R.string.mod_shift
            Modifier.LEFT_ALT -> altName(profileId)
            Modifier.LEFT_GUI -> guiName(profileId)
            Modifier.RIGHT_CTRL -> R.string.mod_right_ctrl
            Modifier.RIGHT_SHIFT -> R.string.mod_right_shift
            Modifier.RIGHT_ALT -> R.string.mod_right_alt
            Modifier.RIGHT_GUI -> R.string.mod_right_gui
        }

    @StringRes
    fun buttonName(b: MouseButton): Int =
        when (b) {
            MouseButton.PRIMARY -> R.string.button_primary
            MouseButton.SECONDARY -> R.string.button_secondary
            MouseButton.TERTIARY -> R.string.button_tertiary
            MouseButton.BUTTON4 -> R.string.button_4
            MouseButton.BUTTON5 -> R.string.button_5
        }

    fun mediaName(
        c: Context,
        usage: Int,
    ): String =
        when (usage) {
            ConsumerUsage.PLAY_PAUSE -> c.getString(R.string.media_play_pause)
            ConsumerUsage.SCAN_NEXT_TRACK -> c.getString(R.string.media_next)
            ConsumerUsage.SCAN_PREVIOUS_TRACK -> c.getString(R.string.media_previous)
            ConsumerUsage.MUTE -> c.getString(R.string.media_mute)
            ConsumerUsage.VOLUME_INCREMENT -> c.getString(R.string.media_volume_up)
            ConsumerUsage.VOLUME_DECREMENT -> c.getString(R.string.media_volume_down)
            ConsumerUsage.AC_ZOOM_IN -> c.getString(R.string.media_zoom_in)
            ConsumerUsage.AC_ZOOM_OUT -> c.getString(R.string.media_zoom_out)
            else -> c.getString(R.string.media_other, usage)
        }

    fun keyName(
        c: Context,
        usage: Int,
    ): String =
        when (usage) {
            in KeyUsage.A..KeyUsage.Z -> ('A' + (usage - KeyUsage.A)).toString()
            in KeyUsage.DIGIT_1 until KeyUsage.DIGIT_0 -> ('1' + (usage - KeyUsage.DIGIT_1)).toString()
            KeyUsage.DIGIT_0 -> "0"
            in KeyUsage.F1..KeyUsage.F12 -> "F${usage - KeyUsage.F1 + 1}"
            else -> namedKeys[usage]?.let { c.getString(it) } ?: c.getString(R.string.key_other, usage)
        }

    private val namedKeys: Map<Int, Int> =
        mapOf(
            KeyUsage.ENTER to R.string.key_enter,
            KeyUsage.ESCAPE to R.string.key_escape,
            KeyUsage.BACKSPACE to R.string.key_backspace,
            KeyUsage.TAB to R.string.key_tab,
            KeyUsage.SPACE to R.string.key_space,
            KeyUsage.MINUS to R.string.key_minus,
            KeyUsage.EQUAL to R.string.key_equal,
            KeyUsage.HOME to R.string.key_home,
            KeyUsage.END to R.string.key_end,
            KeyUsage.PAGE_UP to R.string.key_page_up,
            KeyUsage.PAGE_DOWN to R.string.key_page_down,
            KeyUsage.DELETE_FORWARD to R.string.key_delete,
            KeyUsage.LEFT_ARROW to R.string.key_left,
            KeyUsage.RIGHT_ARROW to R.string.key_right,
            KeyUsage.UP_ARROW to R.string.key_up,
            KeyUsage.DOWN_ARROW to R.string.key_down,
            KeyUsage.KEYPAD_PLUS to R.string.key_keypad_plus,
            KeyUsage.KEYPAD_MINUS to R.string.key_keypad_minus,
        )

    /** Alt as the host of [profileId] labels it; generic for Custom or no profile. */
    @StringRes
    private fun altName(profileId: String?): Int =
        when (profileId) {
            HostProfiles.MACOS -> R.string.mod_alt_mac
            HostProfiles.WINDOWS, HostProfiles.GNOME, HostProfiles.KDE -> R.string.mod_alt_pc
            else -> R.string.mod_alt
        }

    /** The GUI key: Cmd on macOS, Win on Windows, Super on Linux; generic otherwise. */
    @StringRes
    private fun guiName(profileId: String?): Int =
        when (profileId) {
            HostProfiles.MACOS -> R.string.mod_gui_mac
            HostProfiles.WINDOWS -> R.string.mod_gui_windows
            HostProfiles.GNOME, HostProfiles.KDE -> R.string.mod_gui_linux
            else -> R.string.mod_gui
        }

    fun describe(
        c: Context,
        action: Action?,
        profileId: String? = null,
    ): String =
        when (action) {
            null -> {
                c.getString(R.string.action_none)
            }

            is Action.MouseClick -> {
                c.getString(R.string.action_click_desc, c.getString(buttonName(action.button)))
            }

            is Action.KeyChord -> {
                chord(c, action.modifiers, action.keys, profileId)
            }

            is Action.Consumer -> {
                c.getString(R.string.action_media_desc, mediaName(c, action.usage))
            }

            is Action.ModifierWheel -> {
                val wheel = c.getString(if (action.wheel >= 0) R.string.action_wheel_up else R.string.action_wheel_down)
                c.getString(R.string.action_modwheel_desc, chord(c, action.modifiers, emptyList(), profileId), wheel)
            }

            is Action.Sequence -> {
                action.steps.map { describe(c, it, profileId) }.reduce {
                    a,
                    b,
                    ->
                    c.getString(R.string.action_sequence_desc, a, b)
                }
            }
        }

    private fun chord(
        c: Context,
        modifiers: Set<Modifier>,
        keys: List<Int>,
        profileId: String?,
    ): String =
        (
            Modifier.entries.filter { it in modifiers }.map {
                c.getString(
                    modifierName(it, profileId),
                )
            } + keys.map { keyName(c, it) }
        ).joinToString(" + ")
}
