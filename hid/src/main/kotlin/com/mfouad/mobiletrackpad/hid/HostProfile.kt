package com.mfouad.mobiletrackpad.hid

/**
 * Gestures whose action is chosen by the host profile. Clicks and scrolling are fixed.
 *
 * [key] is the stored identifier. It never changes, independent of the constant's name.
 */
enum class GestureSlot(
    val key: String,
) {
    PINCH_OUT("pinch_out"),
    PINCH_IN("pinch_in"),
    ROTATE_CLOCKWISE("rotate_cw"),
    ROTATE_COUNTERCLOCKWISE("rotate_ccw"),
    SWIPE3_UP("swipe3_up"),
    SWIPE3_DOWN("swipe3_down"),
    SWIPE3_LEFT("swipe3_left"),
    SWIPE3_RIGHT("swipe3_right"),
}

/**
 * Data-defined shortcut mappings for one kind of host. Users can edit any profile; built-in
 * defaults remain available through [HostProfiles.default].
 *
 * Shortcuts are sent as physical key positions and only behave as described when the host uses
 * its default shortcuts and a layout where those keys are where a US layout has them.
 */
data class HostProfile(
    val id: String,
    val name: String,
    val mappings: Map<GestureSlot, Action> = emptyMap(),
) {
    fun actionFor(slot: GestureSlot): Action? = mappings[slot]

    fun with(
        slot: GestureSlot,
        action: Action?,
    ): HostProfile = copy(mappings = if (action == null) mappings - slot else mappings + (slot to action))
}

/**
 * Built-in profiles. Swipe directions follow "content moves with the fingers": swiping left
 * reveals what is to the right (next desktop or space, or browser forward).
 * Each default is an assumption about the host's stock shortcuts, documented
 * in README.md ("Gesture mappings by profile"). None has been verified on every host version.
 */
object HostProfiles {
    const val WINDOWS = "windows"
    const val MACOS = "macos"
    const val GNOME = "gnome"
    const val KDE = "kde"
    const val CUSTOM = "custom"

    val ids: List<String> = listOf(WINDOWS, MACOS, GNOME, KDE, CUSTOM)

    private val ctrl = setOf(Modifier.LEFT_CTRL)
    private val gui = setOf(Modifier.LEFT_GUI)
    private val alt = setOf(Modifier.LEFT_ALT)

    private fun keys(
        mods: Set<Modifier>,
        vararg k: Int,
    ) = Action.KeyChord(mods, k.toList())

    private val ctrlWheelUp = Action.ModifierWheel(ctrl, wheel = 1)
    private val ctrlWheelDown = Action.ModifierWheel(ctrl, wheel = -1)

    fun default(id: String): HostProfile =
        when (id) {
            WINDOWS -> windows()
            MACOS -> macos()
            GNOME -> gnome()
            KDE -> kde()
            else -> custom()
        }

    val defaults: List<HostProfile> get() = ids.map(::default)

    private fun windows() =
        HostProfile(
            WINDOWS,
            "Windows",
            mapOf(
                GestureSlot.PINCH_OUT to ctrlWheelUp,
                GestureSlot.PINCH_IN to ctrlWheelDown,
                // Task view, show desktop, switch virtual desktop.
                GestureSlot.SWIPE3_UP to keys(gui, KeyUsage.TAB),
                GestureSlot.SWIPE3_DOWN to keys(gui, KeyUsage.letter('d')),
                GestureSlot.SWIPE3_LEFT to keys(gui + ctrl, KeyUsage.RIGHT_ARROW),
                GestureSlot.SWIPE3_RIGHT to keys(gui + ctrl, KeyUsage.LEFT_ARROW),
            ),
        )

    private fun macos() =
        HostProfile(
            MACOS,
            "macOS",
            mapOf(
                // Application zoom (Cmd + = / Cmd + -). Cmd + wheel is not application zoom on macOS.
                GestureSlot.PINCH_OUT to keys(gui, KeyUsage.EQUAL),
                GestureSlot.PINCH_IN to keys(gui, KeyUsage.MINUS),
                // Mission Control, application windows, previous/next space.
                GestureSlot.SWIPE3_UP to keys(ctrl, KeyUsage.UP_ARROW),
                GestureSlot.SWIPE3_DOWN to keys(ctrl, KeyUsage.DOWN_ARROW),
                GestureSlot.SWIPE3_LEFT to keys(ctrl, KeyUsage.RIGHT_ARROW),
                GestureSlot.SWIPE3_RIGHT to keys(ctrl, KeyUsage.LEFT_ARROW),
            ),
        )

    private fun gnome() =
        HostProfile(
            GNOME,
            "Linux (GNOME)",
            mapOf(
                GestureSlot.PINCH_OUT to ctrlWheelUp,
                GestureSlot.PINCH_IN to ctrlWheelDown,
                // Activities overview, then workspace switching.
                GestureSlot.SWIPE3_UP to keys(gui, KeyUsage.letter('s')),
                GestureSlot.SWIPE3_LEFT to keys(gui, KeyUsage.PAGE_DOWN),
                GestureSlot.SWIPE3_RIGHT to keys(gui, KeyUsage.PAGE_UP),
            ),
        )

    private fun kde() =
        HostProfile(
            KDE,
            "Linux (KDE Plasma)",
            mapOf(
                GestureSlot.PINCH_OUT to ctrlWheelUp,
                GestureSlot.PINCH_IN to ctrlWheelDown,
                // Overview, peek at desktop, switch desktop.
                GestureSlot.SWIPE3_UP to keys(gui, KeyUsage.letter('w')),
                GestureSlot.SWIPE3_DOWN to keys(gui, KeyUsage.letter('d')),
                GestureSlot.SWIPE3_LEFT to keys(gui + ctrl, KeyUsage.RIGHT_ARROW),
                GestureSlot.SWIPE3_RIGHT to keys(gui + ctrl, KeyUsage.LEFT_ARROW),
            ),
        )

    private fun custom() =
        HostProfile(
            CUSTOM,
            "Custom",
            mapOf(
                GestureSlot.PINCH_OUT to ctrlWheelUp,
                GestureSlot.PINCH_IN to ctrlWheelDown,
                // Browser back and forward.
                GestureSlot.SWIPE3_LEFT to keys(alt, KeyUsage.RIGHT_ARROW),
                GestureSlot.SWIPE3_RIGHT to keys(alt, KeyUsage.LEFT_ARROW),
            ),
        )
}
