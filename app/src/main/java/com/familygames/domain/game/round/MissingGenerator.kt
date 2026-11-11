package com.familygames.domain.game.round

import com.familygames.domain.model.Photo
import kotlin.random.Random

/** M24 · "Czego brakuje?" levels: photos on the stage. */
enum class MissingLevel(val key: String, val shown: Int, val rounds: Int = 5) {
    THREE("missing_3", 3),
    FOUR("missing_4", 4),
    ;

    /** The stage plus two photos that were not on it. */
    val needed: Int get() = shown + OPTIONS - 1

    companion object {
        const val OPTIONS = 3
        fun fromKey(key: String): MissingLevel = entries.firstOrNull { it.key == key } ?: THREE
    }
}

/** One round: [shown] on the stage, the one at [gap] disappears; the answer options are in the [Round]. */
data class MissingTask(val shown: List<Photo>, val gap: Int)

/**
 * The child remembers the stage, then picks the missing photo out of three: the missing one and two photos that
 * were not on the stage, so every option is a fair choice. The gap never sits in the same place three times running.
 */
class MissingRoundGenerator(private val random: Random) {
    fun generate(pool: List<Photo>, level: MissingLevel): List<Pair<MissingTask, Round<Photo>>> {
        val photos = pool.distinctBy { it.id }
        if (photos.size < level.needed) return emptyList()
        val gaps = PositionPicker(random)
        val answers = PositionPicker(random)
        return List(level.rounds) {
            val picked = photos.shuffled(random)
            val shown = picked.take(level.shown)
            val gap = gaps.next(level.shown)
            val others = picked.drop(level.shown).take(MissingLevel.OPTIONS - 1)
            val at = answers.next(MissingLevel.OPTIONS)
            val options = others.toMutableList().apply { add(at, shown[gap]) }
            MissingTask(shown, gap) to Round(options, at)
        }
    }
}
