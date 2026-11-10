package com.familygames.domain.game.round

/** One round: the options shown and which of them is right (by index). */
data class Round<T>(val options: List<T>, val correctIndex: Int) {
    init {
        require(correctIndex in options.indices) { "correctIndex $correctIndex outside ${options.size} options" }
    }

    val correct: T get() = options[correctIndex]
}

enum class RoundPhase { Answering, Feedback, Won }

sealed interface AnswerOutcome {
    data object Ignored : AnswerOutcome
    data class Correct(val lastRound: Boolean) : AnswerOutcome
    /** [mistakesInRound] lets games show a hint after 2 misses (docs/12 M11). */
    data class Wrong(val mistakesInRound: Int) : AnswerOutcome
}

/**
 * Generic engine for round-based task games (docs/12 M10.5): N rounds, one right answer each, mistakes counted,
 * input locked while a right answer is being celebrated, Won after the last round. No timing, no Android.
 */
class RoundEngine<T>(private val rounds: List<Round<T>>) {
    init {
        require(rounds.isNotEmpty()) { "At least one round" }
    }

    var roundIndex = 0
        private set
    var mistakes = 0
        private set
    var mistakesInRound = 0
        private set
    var phase = RoundPhase.Answering
        private set

    val totalRounds: Int get() = rounds.size
    val current: Round<T> get() = rounds[roundIndex]

    fun answer(index: Int): AnswerOutcome {
        if (phase != RoundPhase.Answering || index !in current.options.indices) return AnswerOutcome.Ignored
        return if (index == current.correctIndex) {
            val last = roundIndex == rounds.lastIndex
            phase = if (last) RoundPhase.Won else RoundPhase.Feedback
            AnswerOutcome.Correct(last)
        } else {
            mistakes++
            mistakesInRound++
            AnswerOutcome.Wrong(mistakesInRound)
        }
    }

    /** After the "correct" feedback, move on. */
    fun next() {
        if (phase != RoundPhase.Feedback) return
        roundIndex++
        mistakesInRound = 0
        phase = RoundPhase.Answering
    }
}

/** docs/12 M10.5: 3★ without mistakes, 2★ up to half the rounds, 1★ otherwise. */
object RoundScoring {
    fun stars(rounds: Int, mistakes: Int): Int = when {
        mistakes == 0 -> 3
        mistakes <= rounds / 2 -> 2
        else -> 1
    }
}
