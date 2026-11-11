package com.mfouad.pocketpad.hid

/**
 * Plain-text encoding of actions and profiles for storage. One action per string:
 *
 * - `click 08` (mouse button bit mask, hex)
 * - `keys 03 2E,04` (modifier bit mask, then hex key usages or `-`)
 * - `consumer E9` (hex usage)
 * - `modwheel 01 1 0` (modifier bit mask, wheel, pan)
 * - steps of a sequence joined by ` ; `
 *
 * Only HID values (bit masks, usage IDs) and fixed keys are stored, never Kotlin enum names,
 * so release-build minification cannot change the stored format.
 *
 * Decoding never throws: anything unreadable decodes to null (no action).
 */
object ActionCodec {
    private const val SEQUENCE_SEPARATOR = " ; "
    private const val NONE = "-"
    private const val HEX = 16

    fun encode(action: Action): String =
        when (action) {
            is Action.MouseClick -> "click ${hex(action.button.mask)}"
            is Action.KeyChord -> "keys ${mods(action.modifiers)} ${hexList(action.keys)}"
            is Action.Consumer -> "consumer ${hex(action.usage)}"
            is Action.ModifierWheel -> "modwheel ${mods(action.modifiers)} ${action.wheel} ${action.pan}"
            is Action.Sequence -> action.steps.joinToString(SEQUENCE_SEPARATOR) { encode(it) }
        }

    fun decode(text: String): Action? =
        runCatching {
            val parts = text.split(SEQUENCE_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
            val steps = parts.map { decodeStep(it) ?: return@runCatching null }
            when (steps.size) {
                0 -> null
                1 -> steps.single()
                else -> Action.Sequence(steps)
            }
        }.getOrNull()

    private fun decodeStep(text: String): Action? {
        val f = text.split(' ').filter { it.isNotEmpty() }
        return when (f.firstOrNull()) {
            "click" -> {
                val mask = f[1].toInt(HEX)
                MouseButton.entries.firstOrNull { it.mask == mask }?.let { Action.MouseClick(it) }
            }

            "keys" -> {
                Action.KeyChord(parseMods(f[1]), parseHexList(f[2]))
            }

            "consumer" -> {
                Action.Consumer(f[1].toInt(HEX))
            }

            "modwheel" -> {
                f.drop(2).let { (wheel, pan) -> Action.ModifierWheel(parseMods(f[1]), wheel.toInt(), pan.toInt()) }
            }

            else -> {
                null
            }
        }
    }

    private fun mods(m: Set<Modifier>) = hex(Modifier.maskOf(m))

    private fun parseMods(s: String): Set<Modifier> {
        val mask = s.toInt(HEX)
        require(mask in 0..BYTE_MASK)
        return Modifier.entries.filter { it.mask and mask != 0 }.toSet()
    }

    private fun hex(v: Int) = v.toString(HEX).uppercase().padStart(2, '0')

    private fun hexList(k: List<Int>) = if (k.isEmpty()) NONE else k.joinToString(",") { hex(it) }

    private fun parseHexList(s: String) = if (s == NONE) emptyList() else s.split(',').map { it.toInt(HEX) }
}

/**
 * Profile encoding: `name=<display name>` followed by `<SLOT>=<action>` lines. Unknown slots and
 * unreadable actions are skipped, so a damaged entry loses only that mapping.
 */
object ProfileCodec {
    private const val NAME_KEY = "name"

    fun encode(profile: HostProfile): String =
        buildString {
            appendLine("$NAME_KEY=${profile.name.replace('\n', ' ')}")
            GestureSlot.entries.forEach { slot ->
                profile.mappings[slot]?.let { appendLine("${slot.key}=${ActionCodec.encode(it)}") }
            }
        }

    fun decode(
        id: String,
        text: String,
        fallbackName: String,
    ): HostProfile {
        var name = fallbackName
        val mappings = mutableMapOf<GestureSlot, Action>()
        text.lineSequence().forEach { line ->
            val key = line.substringBefore('=', missingDelimiterValue = "")
            val value = line.substringAfter('=', missingDelimiterValue = "")
            when {
                key == NAME_KEY -> {
                    if (value.isNotBlank()) name = value
                }

                key.isNotEmpty() -> {
                    val slot = GestureSlot.entries.firstOrNull { it.key == key }
                    val action = ActionCodec.decode(value)
                    if (slot != null && action != null) mappings[slot] = action
                }
            }
        }
        return HostProfile(id, name, mappings)
    }
}
