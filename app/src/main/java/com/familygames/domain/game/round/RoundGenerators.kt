package com.familygames.domain.game.round

import com.familygames.domain.model.Photo
import kotlin.random.Random

/** Picks the right answer's position: random, but never the same place 3 times in a row (docs/12 M11). */
class PositionPicker(private val random: Random) {
    private val last = ArrayDeque<Int>()

    fun next(size: Int): Int {
        val banned = if (last.size >= 2 && last[0] == last[1]) last[0] else null
        val choices = (0 until size).filter { it != banned }
        val pick = choices.random(random)
        last.addFirst(pick)
        while (last.size > 2) last.removeLast()
        return pick
    }
}

/** Distractors from the same pool, without repeats and preferring other labels (docs/12 M10.5). */
internal fun pickDistractors(target: Photo, pool: List<Photo>, count: Int, random: Random): List<Photo> {
    val others = pool.filter { it.id != target.id }.shuffled(random)
    val differentLabel = others.filter { it.label == null || !it.label.equals(target.label, ignoreCase = true) }
        .distinctBy { it.label?.lowercase() ?: "#${it.id}" }
    val picked = differentLabel.take(count).toMutableList()
    if (picked.size < count) picked += others.filter { it !in picked }.take(count - picked.size)
    return picked
}

/** M11 · "Gdzie jest…?" levels: photos per round and rounds per game. */
enum class WhereIsLevel(val key: String, val options: Int, val rounds: Int) {
    TWO("where_2", 2, 5),
    THREE("where_3", 3, 6),
    FOUR("where_4", 4, 8),
    ;

    companion object {
        fun fromKey(key: String): WhereIsLevel = entries.firstOrNull { it.key == key } ?: TWO
    }
}

/** A photo can be asked about when it has a label or a recorded question (docs/12 M11). */
fun Photo.isAskable(): Boolean = !label.isNullOrBlank() || promptAudioPath != null

/** M11 round generator: every target at most once per game, distractors from the same category. */
class WhereIsRoundGenerator(private val random: Random) {
    fun generate(pool: List<Photo>, level: WhereIsLevel): List<Round<Photo>> {
        if (pool.size < level.options) return emptyList()
        val targets = pool.filter { it.isAskable() }.shuffled(random).take(level.rounds)
        val positions = PositionPicker(random)
        return targets.map { target ->
            val distractors = pickDistractors(target, pool, level.options - 1, random)
            val at = positions.next(level.options)
            val options = distractors.toMutableList().apply { add(at, target) }
            Round(options, at)
        }
    }
}

/** M13b · "Co nie pasuje?" levels. */
enum class OddLevel(val key: String, val options: Int, val rounds: Int) {
    THREE("odd_3", 3, 5),
    FOUR("odd_4", 4, 6),
    ;

    companion object {
        fun fromKey(key: String): OddLevel = entries.firstOrNull { it.key == key } ?: THREE
    }
}

/**
 * M13b round generator: (options − 1) photos of category A and one of category B; the same pair of categories
 * is not used in two rounds in a row. [groups] = photos per category (only categories with at least one photo).
 */
class OddOneOutRoundGenerator(private val random: Random) {
    fun generate(groups: Map<Long, List<Photo>>, level: OddLevel): List<Round<Photo>> {
        val majority = groups.filterValues { it.size >= level.options - 1 }.keys.toList()
        val all = groups.filterValues { it.isNotEmpty() }.keys.toList()
        if (majority.isEmpty() || all.size < 2) return emptyList()
        val pairs = majority.flatMap { a -> all.filter { it != a }.map { b -> a to b } }
        val positions = PositionPicker(random)
        var previous: Set<Long>? = null
        return List(level.rounds) {
            val fresh = pairs.filter { setOf(it.first, it.second) != previous }.ifEmpty { pairs }
            val (a, b) = fresh.random(random)
            previous = setOf(a, b)
            val group = groups.getValue(a).shuffled(random).take(level.options - 1)
            val odd = groups.getValue(b).random(random)
            val at = positions.next(level.options)
            Round(group.toMutableList().apply { add(at, odd) }, at)
        }
    }
}

/** M17 · "Ile jest?" levels. */
enum class CountingLevel(val key: String, val max: Int, val rounds: Int = 5) {
    TO_3("count_3", 3),
    TO_5("count_5", 5),
    ;

    companion object {
        fun fromKey(key: String): CountingLevel = entries.firstOrNull { it.key == key } ?: TO_3
    }
}

/** One counting task: [count] copies of [photo]; the answer options are the numbers 1…max. */
data class CountingTask(val photo: Photo, val count: Int)

class CountingRoundGenerator(private val random: Random) {
    /** Options are numbers 1…max, so the right index is count − 1; the count never repeats twice in a row. */
    fun generate(countable: List<Photo>, level: CountingLevel): List<Round<Int>> = generateTasks(countable, level).map { task ->
        Round((1..level.max).toList(), task.count - 1)
    }

    fun generateTasks(countable: List<Photo>, level: CountingLevel): List<CountingTask> {
        if (countable.isEmpty()) return emptyList()
        val photos = countable.shuffled(random)
        var previous = 0
        return List(level.rounds) { i ->
            val count = (1..level.max).filter { it != previous }.random(random)
            previous = count
            CountingTask(photos[i % photos.size], count)
        }
    }
}

/** Positions for [count] tiles of [tile] size in a [width]×[height] area without overlap (rejection sampling). */
object ScatterLayout {
    data class Spot(val x: Float, val y: Float)

    fun place(count: Int, width: Float, height: Float, tile: Float, gap: Float, random: Random): List<Spot> {
        val spots = mutableListOf<Spot>()
        val maxX = (width - tile).coerceAtLeast(0f)
        val maxY = (height - tile).coerceAtLeast(0f)
        val minDistance = tile + gap
        var attempts = 0
        while (spots.size < count && attempts < 2_000) {
            attempts++
            val candidate = Spot(random.nextFloat() * maxX, random.nextFloat() * maxY)
            if (spots.none { kotlin.math.hypot(it.x - candidate.x, it.y - candidate.y) < minDistance }) spots += candidate
        }
        // Too little room for random placement: fall back to a neat row so the game never breaks.
        if (spots.size < count) {
            val step = (width - tile) / (count - 1).coerceAtLeast(1)
            return List(count) { Spot((it * step).coerceAtMost(maxX), maxY / 2) }
        }
        return spots
    }
}
