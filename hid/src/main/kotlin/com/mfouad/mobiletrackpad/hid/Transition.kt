package com.mfouad.mobiletrackpad.hid

/** One queued change of report state, sent in its own tick. */
internal sealed interface Transition {
    data class Buttons(
        val mask: Int,
    ) : Transition

    data class Keys(
        val state: KeyboardState,
    ) : Transition

    data class Consumer(
        val usage: Int,
    ) : Transition

    /** Relative motion sent with the buttons held at that point. */
    data class Motion(
        val delta: Delta,
    ) : Transition
}

/** Report state the queued transitions lead to; actions expand relative to it. */
internal data class TargetState(
    val buttons: Int,
    val keyboard: KeyboardState,
    val consumer: Int,
)

/**
 * Expands an action into transitions. Every action ends in the state it started from, so
 * nothing an action presses is left held.
 */
internal fun Action.expand(base: TargetState): List<Transition> =
    when (this) {
        is Action.MouseClick -> {
            listOf(Transition.Buttons(base.buttons or button.mask), Transition.Buttons(base.buttons))
        }

        is Action.KeyChord -> {
            val mods = KeyboardState(Modifier.maskOf(modifiers))
            buildList {
                if (modifiers.isNotEmpty()) add(Transition.Keys(base.keyboard.plus(mods)))
                add(Transition.Keys(base.keyboard.plus(KeyboardState(mods.modifiers, keys))))
                if (modifiers.isNotEmpty() && keys.isNotEmpty()) add(Transition.Keys(base.keyboard.plus(mods)))
                add(Transition.Keys(base.keyboard))
            }
        }

        is Action.Consumer -> {
            listOf(Transition.Consumer(usage), Transition.Consumer(base.consumer))
        }

        is Action.ModifierWheel -> {
            listOf(
                Transition.Keys(base.keyboard.plus(KeyboardState(Modifier.maskOf(modifiers)))),
                Transition.Motion(Delta(wheel = wheel.toLong(), pan = pan.toLong())),
                Transition.Keys(base.keyboard),
            )
        }

        is Action.Sequence -> {
            steps.flatMap { it.expand(base) }
        }
    }
