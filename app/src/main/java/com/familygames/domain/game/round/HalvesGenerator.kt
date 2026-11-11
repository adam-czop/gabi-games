package com.familygames.domain.game.round

import com.familygames.domain.model.Photo
import kotlin.random.Random

/** M25 · "Połówki" levels: right halves to choose from. */
enum class HalvesLevel(val key: String, val options: Int, val rounds: Int = 5) {
    TWO("halves_2", 2),
    THREE("halves_3", 3),
    ;

    companion object {
        fun fromKey(key: String): HalvesLevel = entries.firstOrNull { it.key == key } ?: TWO
    }
}

/**
 * The left half of a photo is shown; the options are right halves — its own and of other photos (other labels first,
 * so two halves of "babcia" never compete). Targets do not repeat in a game; the answer moves around.
 */
class HalvesRoundGenerator(private val random: Random) {
    fun generate(pool: List<Photo>, level: HalvesLevel): List<Round<Photo>> {
        val photos = pool.distinctBy { it.id }
        if (photos.size < level.options) return emptyList()
        val targets = photos.shuffled(random).let { t -> List(level.rounds) { t[it % t.size] } }
        val positions = PositionPicker(random)
        return targets.map { target ->
            val others = pickDistractors(target, photos, level.options - 1, random)
            val at = positions.next(level.options)
            Round(others.toMutableList().apply { add(at, target) }, at)
        }
    }
}
