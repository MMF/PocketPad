package com.mfouad.pocketpad.hid

/**
 * Something a gesture can be mapped to. Each action expands into an ordered list of report
 * transitions that always ends with everything it pressed released.
 */
sealed interface Action {
    /** Press and release one mouse button. */
    data class MouseClick(
        val button: MouseButton,
    ) : Action

    /** Press modifiers, press keys, release keys, release modifiers. */
    data class KeyChord(
        val modifiers: Set<Modifier> = emptySet(),
        val keys: List<Int> = emptyList(),
    ) : Action {
        init {
            require(modifiers.isNotEmpty() || keys.isNotEmpty()) { "Empty key chord" }
            require(keys.size <= KeyboardState.MAX_KEYS) { "At most ${KeyboardState.MAX_KEYS} keys" }
            require(keys.all { it in 1..KeyUsage.MAX }) { "Key usage out of range: $keys" }
        }
    }

    /** Press and release one Consumer page usage (Report 3). Unavailable in boot mode. */
    data class Consumer(
        val usage: Int,
    ) : Action {
        init {
            require(usage in 1..ConsumerUsage.MAX) { "Consumer usage out of range: $usage" }
        }
    }

    /**
     * Hold modifiers, send one wheel/pan step, release modifiers. Used for zoom as
     * modifier + wheel. Unavailable in boot mode.
     */
    data class ModifierWheel(
        val modifiers: Set<Modifier>,
        val wheel: Int = 0,
        val pan: Int = 0,
    ) : Action {
        init {
            require(wheel in -ReportEncoder.INT8_MAX..ReportEncoder.INT8_MAX) { "wheel out of range" }
            require(pan in -ReportEncoder.INT8_MAX..ReportEncoder.INT8_MAX) { "pan out of range" }
            require(wheel != 0 || pan != 0) { "ModifierWheel without movement" }
        }
    }

    /** Several actions performed in order. */
    data class Sequence(
        val steps: List<Action>,
    ) : Action {
        init {
            require(steps.isNotEmpty()) { "Empty sequence" }
        }
    }
}

/** Whether every part of this action can be expressed in [mode]. */
fun Action.isAvailableIn(mode: ProtocolMode): Boolean =
    when (this) {
        is Action.MouseClick -> mode == ProtocolMode.REPORT || button.availableInBoot
        is Action.KeyChord -> true
        is Action.Consumer -> mode == ProtocolMode.REPORT
        is Action.ModifierWheel -> mode == ProtocolMode.REPORT
        is Action.Sequence -> steps.all { it.isAvailableIn(mode) }
    }

/** True if performing this action holds a keyboard modifier at some point. */
fun Action.usesModifiers(): Boolean =
    when (this) {
        is Action.MouseClick, is Action.Consumer -> false
        is Action.KeyChord -> modifiers.isNotEmpty()
        is Action.ModifierWheel -> true
        is Action.Sequence -> steps.any { it.usesModifiers() }
    }
