package com.familygames.domain.game.board

import com.familygames.domain.model.Photo
import kotlin.random.Random

sealed interface DropOutcome {
    data object Ignored : DropOutcome
    data class Correct(val won: Boolean) : DropOutcome
    data object Wrong : DropOutcome
}

/** M13a · "Do koszyczków" levels: baskets and photos per basket. */
enum class SortingLevel(val key: String, val baskets: Int, val perBasket: Int = 3) {
    TWO("sort_2", 2),
    THREE("sort_3", 3),
    ;

    val items: Int get() = baskets * perBasket

    companion object {
        fun fromKey(key: String): SortingLevel = entries.firstOrNull { it.key == key } ?: TWO
    }
}

data class SortItem(val photo: Photo, val basket: Long)

/** Tray + baskets: an item dropped on its own category's basket disappears from the tray. */
class SortingBoard(val baskets: List<Long>, items: List<SortItem>) {
    private val tray = items.toMutableList()
    var mistakes = 0
        private set
    val total: Int = items.size
    private val sortedCount = mutableMapOf<Long, Int>()
    val trayItems: List<SortItem> get() = tray.toList()
    val sorted: Map<Long, Int> get() = baskets.associateWith { sortedCount[it] ?: 0 }

    fun drop(photoId: Long, basket: Long): DropOutcome {
        val item = tray.firstOrNull { it.photo.id == photoId } ?: return DropOutcome.Ignored
        if (basket !in baskets) return DropOutcome.Ignored
        if (item.basket != basket) {
            mistakes++
            return DropOutcome.Wrong
        }
        tray.remove(item)
        sortedCount[basket] = (sortedCount[basket] ?: 0) + 1
        return DropOutcome.Correct(won = tray.isEmpty())
    }

    companion object {
        /** Picks [SortingLevel.baskets] categories with enough photos and deals the tray; null when not possible. */
        fun deal(groups: Map<Long, List<Photo>>, level: SortingLevel, random: Random): SortingBoard? {
            val eligible = groups.filterValues { it.size >= level.perBasket }.keys.shuffled(random).take(level.baskets)
            if (eligible.size < level.baskets) return null
            val items = eligible.flatMap { c -> groups.getValue(c).shuffled(random).take(level.perBasket).map { SortItem(it, c) } }
            return SortingBoard(eligible, items.shuffled(random))
        }
    }
}

/**
 * M16 · "Po kolei": photos go into numbered slots. In easy mode only the first free slot accepts a drop,
 * otherwise any slot does, but only the right photo stays there.
 */
class SequenceBoard(val steps: List<Photo>, val easy: Boolean) {
    private val filled = arrayOfNulls<Photo>(steps.size)
    var mistakes = 0
        private set

    val slots: List<Photo?> get() = filled.toList()
    val firstFree: Int get() = filled.indexOfFirst { it == null }

    fun isActive(slot: Int): Boolean = filled.getOrNull(slot) == null && (!easy || slot == firstFree)

    fun drop(photoId: Long, slot: Int): DropOutcome {
        if (slot !in steps.indices || filled.any { it?.id == photoId } || !isActive(slot)) return DropOutcome.Ignored
        if (steps[slot].id != photoId) {
            mistakes++
            return DropOutcome.Wrong
        }
        filled[slot] = steps[slot]
        return DropOutcome.Correct(won = filled.all { it != null })
    }
}

/** Random photo that is none of the last [memory] ones (docs/12 M12a: "inne niż ostatnie 5"). */
class FreshPicker<T>(private val random: Random, private val memory: Int = 5) {
    private val recent = ArrayDeque<T>()

    fun next(pool: List<T>): T? {
        if (pool.isEmpty()) return null
        val keep = memory.coerceAtMost(pool.size - 1)
        while (recent.size > keep) recent.removeFirst()
        val pick = pool.filter { it !in recent }.ifEmpty { pool }.random(random)
        recent.addLast(pick)
        while (recent.size > keep) recent.removeFirst()
        return pick
    }
}

/** M12b · coverage counted on a coarse grid of cells, not pixels (docs/12). */
class ScratchGrid(val cols: Int = 32, val rows: Int = 24) {
    private val cleared = BooleanArray(cols * rows)
    var clearedCount = 0
        private set

    val fraction: Float get() = clearedCount / (cols * rows).toFloat()

    /** Clears the cells whose centre is within [radius] of ([x], [y]); coordinates are 0..1 of the picture. */
    fun scratch(x: Float, y: Float, radius: Float) {
        val rx = radius * cols
        val ry = radius * rows
        val cx = x * cols
        val cy = y * rows
        for (r in (cy - ry).toInt().coerceAtLeast(0)..(cy + ry).toInt().coerceAtMost(rows - 1)) {
            for (c in (cx - rx).toInt().coerceAtLeast(0)..(cx + rx).toInt().coerceAtMost(cols - 1)) {
                val dx = (c + 0.5f - cx) / rx
                val dy = (r + 0.5f - cy) / ry
                if (dx * dx + dy * dy <= 1f && !cleared[r * cols + c]) {
                    cleared[r * cols + c] = true
                    clearedCount++
                }
            }
        }
    }

    companion object {
        const val REVEAL_AT = 0.7f
    }
}

/** M15b · bars of the xylophone and the "Wlazł kotek na płotek" melody (folk tune, public domain; G4). */
object Xylophone {
    /** C D E F G A C' D' – ADR-024: F is needed for the melody, B is left out so no two bars clash badly. */
    val NOTE_NAMES = listOf("c", "d", "e", "f", "g", "a", "c2", "d2")
    const val BARS = 8

    /** Bar index per note; "Wlazł kotek na płotek i mruga, ładna to piosenka niedługa". */
    val MELODY = listOf(4, 2, 2, 3, 1, 1, 0, 2, 4, 4, 2, 2, 3, 1, 1, 0, 2, 0)

    /** Beat lengths: the last note of each line is held longer. */
    fun beatMs(index: Int): Long = if (index == 8 || index == MELODY.lastIndex) 900L else 450L
}

/** M18 · undo history of a colouring page: at most [limit] steps back (docs/12). */
class UndoStack<T>(private val limit: Int = 10) {
    private val items = ArrayDeque<T>()

    val size: Int get() = items.size

    fun push(item: T) {
        items.addLast(item)
        while (items.size > limit) items.removeFirst()
    }

    fun pop(): T? = items.removeLastOrNull()

    fun clear() = items.clear()
}
