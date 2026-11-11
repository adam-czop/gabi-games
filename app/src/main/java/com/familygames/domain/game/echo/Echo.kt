package com.familygames.domain.game.echo

import kotlin.random.Random

/**
 * Levels of "Powtórz za mną" (docs/12 M27): which xylophone bars are used (indices of the 8 bars, so the pitch and
 * the bar height stay as in Cymbałki) and how long the tune grows.
 */
enum class EchoLevel(val key: String, val bars: List<Int>, val target: Int) {
    THREE("echo_3", listOf(0, 2, 4), 3),
    FOUR("echo_4", listOf(0, 2, 4, 5), 4),
    FIVE("echo_5", listOf(0, 2, 3, 4, 5), 5),
    ;

    companion object {
        fun fromKey(key: String): EchoLevel = entries.firstOrNull { it.key == key } ?: THREE
    }
}

sealed interface EchoOutcome {
    /** Not the child's turn, or a bar outside the level. */
    data object Ignored : EchoOutcome
    /** A right bar; [tuneDone] when the whole tune was repeated. */
    data class Good(val tuneDone: Boolean) : EchoOutcome
    /** A wrong bar: the tune is played again, from the start, and stays as long as it was. */
    data object Wrong : EchoOutcome
    data object Won : EchoOutcome
}

/**
 * The tune grows by one note after every right repetition until it is [EchoLevel.target] notes long. Notes are
 * positions in [EchoLevel.bars]; the same note never comes three times in a row (too hard to count for a child).
 */
class EchoGame(val level: EchoLevel, private val random: Random) {
    private val notes = mutableListOf<Int>()
    private var position = 0
    var listening = false
        private set
    var mistakes = 0
        private set
    var won = false
        private set

    val tune: List<Int> get() = notes.toList()

    /** The first one-note tune; call [heard] when it has been played. */
    fun start(): List<Int> {
        notes.clear()
        mistakes = 0
        won = false
        grow()
        return tune
    }

    /** The tune has been played: the child's turn. */
    fun heard() {
        listening = true
        position = 0
    }

    fun tap(pos: Int): EchoOutcome {
        if (!listening || won || pos !in level.bars.indices) return EchoOutcome.Ignored
        if (notes[position] != pos) {
            mistakes++
            listening = false
            return EchoOutcome.Wrong
        }
        position++
        if (position < notes.size) return EchoOutcome.Good(tuneDone = false)
        listening = false
        if (notes.size >= level.target) {
            won = true
            return EchoOutcome.Won
        }
        return EchoOutcome.Good(tuneDone = true)
    }

    /** After a repeated tune: one note longer. */
    fun extend(): List<Int> {
        grow()
        return tune
    }

    private fun grow() {
        val banned = if (notes.size >= 2 && notes[notes.size - 1] == notes[notes.size - 2]) notes.last() else null
        val choices = level.bars.indices.filter { it != banned }
        notes += choices[random.nextInt(choices.size)]
    }
}
