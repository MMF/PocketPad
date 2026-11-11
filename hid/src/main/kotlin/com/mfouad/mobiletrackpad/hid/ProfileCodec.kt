package com.mfouad.mobiletrackpad.hid

/**
 * Plain-text encoding of actions and profiles for storage. One action per string:
 *
 * - `click PRIMARY`
 * - `keys LEFT_CTRL,LEFT_SHIFT 2E,04` (modifiers or `-`, then hex key usages or `-`)
 * - `consumer E9` (hex usage)
 * - `modwheel LEFT_CTRL 1 0` (modifiers, wheel, pan)
 * - steps of a sequence joined by ` ; `
 *
 * Decoding never throws: anything unreadable decodes to null (no action).
 */
object ActionCodec {
    private const val SEQUENCE_SEPARATOR = " ; "
    private const val NONE = "-"
    private const val HEX = 16

    fun encode(action: Action): String =
        when (action) {
            is Action.MouseClick -> "click ${action.button.name}"
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
                Action.MouseClick(MouseButton.valueOf(f[1]))
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

    private fun mods(m: Set<Modifier>) =
        if (m.isEmpty()) {
            NONE
        } else {
            Modifier.entries
                .filter {
                    it in m
                }.joinToString(",") { it.name }
        }

    private fun parseMods(s: String) = if (s == NONE) emptySet() else s.split(',').map { Modifier.valueOf(it) }.toSet()

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
                profile.mappings[slot]?.let { appendLine("${slot.name}=${ActionCodec.encode(it)}") }
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
                    val slot = GestureSlot.entries.firstOrNull { it.name == key }
                    val action = ActionCodec.decode(value)
                    if (slot != null && action != null) mappings[slot] = action
                }
            }
        }
        return HostProfile(id, name, mappings)
    }
}
