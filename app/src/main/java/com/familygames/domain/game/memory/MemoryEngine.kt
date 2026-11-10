package com.familygames.domain.game.memory

import com.familygames.domain.model.MemoryLevel
import com.familygames.domain.model.Photo
import kotlin.random.Random

/** One card on the table; two cards share a [pairKey] (the photo id). */
data class MemoryCard(val id: Long, val pairKey: Long, val photo: Photo)

data class CardState(val card: MemoryCard, val faceUp: Boolean = false, val matched: Boolean = false)

enum class MemoryPhase { Idle, OneUp, Checking, Mismatch, Won }

data class MemoryGameState(
    val cards: List<CardState>,
    val phase: MemoryPhase = MemoryPhase.Idle,
    val moves: Int = 0,
    val mistakes: Int = 0,
) {
    val totalPairs: Int get() = cards.size / 2
    val pairsFound: Int get() = cards.count { it.matched } / 2

    /** No taps accepted while a pair is checked, a mismatch is shown or the game is over (docs/04 §3). */
    val inputLocked: Boolean get() = phase == MemoryPhase.Checking || phase == MemoryPhase.Mismatch || phase == MemoryPhase.Won
}

sealed interface TapOutcome {
    data object Ignored : TapOutcome
    data object FirstUp : TapOutcome

    /** Two cards are up: the caller waits for the flip and then calls [MemoryEngine.check]. */
    data object SecondUp : TapOutcome
}

sealed interface CheckOutcome {
    data class Match(val pairKey: Long, val won: Boolean) : CheckOutcome
    data object Mismatch : CheckOutcome
}

/** Memory state machine (docs/04 §3): Idle → OneUp → Checking → Idle | Mismatch → Idle | Won. No Android, no timing. */
class MemoryEngine(cards: List<MemoryCard>) {

    var state: MemoryGameState = MemoryGameState(cards.map { CardState(it) })
        private set

    fun tap(cardId: Long): TapOutcome {
        val s = state
        if (s.inputLocked) return TapOutcome.Ignored
        val card = s.cards.firstOrNull { it.card.id == cardId } ?: return TapOutcome.Ignored
        if (card.faceUp || card.matched) return TapOutcome.Ignored // also covers tapping the same card twice
        val cards = s.cards.map { if (it.card.id == cardId) it.copy(faceUp = true) else it }
        return when (s.phase) {
            MemoryPhase.Idle -> {
                state = s.copy(cards = cards, phase = MemoryPhase.OneUp)
                TapOutcome.FirstUp
            }
            MemoryPhase.OneUp -> {
                state = s.copy(cards = cards, phase = MemoryPhase.Checking, moves = s.moves + 1)
                TapOutcome.SecondUp
            }
            else -> TapOutcome.Ignored
        }
    }

    /** Resolves the two face-up cards. Only valid in [MemoryPhase.Checking]. */
    fun check(): CheckOutcome {
        val s = state
        check(s.phase == MemoryPhase.Checking) { "check() outside Checking: ${s.phase}" }
        val up = s.cards.filter { it.faceUp && !it.matched }
        return if (up.size == 2 && up[0].card.pairKey == up[1].card.pairKey) {
            val cards = s.cards.map { if (it.faceUp && !it.matched) it.copy(matched = true) else it }
            val won = cards.all { it.matched }
            state = s.copy(cards = cards, phase = if (won) MemoryPhase.Won else MemoryPhase.Idle)
            CheckOutcome.Match(up[0].card.pairKey, won)
        } else {
            state = s.copy(phase = MemoryPhase.Mismatch, mistakes = s.mistakes + 1)
            CheckOutcome.Mismatch
        }
    }

    /** Turns the mismatched pair face down again (after the gentle highlight). */
    fun coverMismatch() {
        val s = state
        if (s.phase != MemoryPhase.Mismatch) return
        state = s.copy(cards = s.cards.map { if (it.faceUp && !it.matched) it.copy(faceUp = false) else it }, phase = MemoryPhase.Idle)
    }
}

/** docs/04 §2: picks photos (fresh ones first), duplicates them and shuffles deterministically for a given [random]. */
class DealMemoryCardsUseCase(
    private val random: Random,
    private val avoidEasyLayouts: Boolean = true,
) {
    operator fun invoke(level: MemoryLevel, pool: List<Photo>, recentlyUsed: Set<Long> = emptySet()): List<MemoryCard> {
        require(pool.size >= level.pairs) { "Need ${level.pairs} photos, have ${pool.size}" }
        val fresh = pool.filterNot { it.id in recentlyUsed }.shuffled(random)
        val stale = pool.filter { it.id in recentlyUsed }.shuffled(random)
        val chosen = (fresh + stale).take(level.pairs)
        val cards = chosen.flatMap { p ->
            listOf(MemoryCard(id = 2 * p.id, pairKey = p.id, photo = p), MemoryCard(id = 2 * p.id + 1, pairKey = p.id, photo = p))
        }
        var dealt = cards.shuffled(random)
        // Anti-boredom for the smallest grids: re-shuffle layouts where ≥ 2 pairs lie side by side (max 5 tries).
        if (avoidEasyLayouts && (level == MemoryLevel.L1 || level == MemoryLevel.L2)) {
            var tries = 0
            while (tries++ < 5 && horizontalPairs(dealt, level.cols) >= 2) dealt = cards.shuffled(random)
        }
        return dealt
    }

    internal fun horizontalPairs(cards: List<MemoryCard>, cols: Int): Int =
        cards.indices.count { i -> (i + 1) % cols != 0 && i + 1 < cards.size && cards[i].pairKey == cards[i + 1].pairKey }
}

/** docs/04 §6: the child always wins at least one star. */
object MemoryScoring {
    fun stars(pairs: Int, mistakes: Int): Int = when {
        mistakes <= pairs -> 3
        mistakes <= 2 * pairs -> 2
        else -> 1
    }
}

/** docs/04 §4. */
object MemoryTimings {
    const val FLIP_MS = 350
    const val CHECK_DELAY_MS = 400L
    const val MISMATCH_MS = 1000L
    const val MISMATCH_SMALL_GRID_MS = 1300L
    const val MATCH_TO_VOICE_MS = 300L
    const val WIN_DELAY_MS = 600L

    fun mismatchFor(level: MemoryLevel): Long =
        if (level == MemoryLevel.L1 || level == MemoryLevel.L2) MISMATCH_SMALL_GRID_MS else MISMATCH_MS
}
