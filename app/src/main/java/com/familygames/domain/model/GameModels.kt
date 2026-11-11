package com.familygames.domain.model

import kotlinx.serialization.Serializable

/** Games known to the app (docs/12 §0). Only registered ones (GameRegistry) appear in the menu. */
@Serializable
enum class GameId {
    MEMORY, PUZZLE, WHERE_IS, PEEKABOO, SCRATCH, SORTING, ODD_ONE_OUT, SOUND_MEMO, BUBBLES, XYLOPHONE, SEQUENCE, COUNTING, COLORING, MAZE, SHADOWS, MARBLES, FIND,
}

/** S17 age switch (docs/12 M10.2): 1–2,5 years or 2,5–5 years. */
enum class AgeBand(val fromYears: Float, val toYears: Float) {
    TODDLER(1f, 2.5f),
    PRESCHOOL(2.5f, 5f),
    ;

    companion object {
        fun fromKey(key: String): AgeBand = entries.firstOrNull { it.name == key } ?: PRESCHOOL
    }
}

/** Task games end with a win and stars; free play has no goal and is ended with the Home button. */
enum class PlayStyle { TASK, FREE_PLAY }

/** Age range a game suits, e.g. 1,5–3 years; it fits a band when the ranges touch or overlap. */
data class AgeRange(val fromYears: Float, val toYears: Float) {
    fun fits(band: AgeBand): Boolean = fromYears <= band.toYears && toYears >= band.fromYears
}

/** Menu rule (docs/12 M10.2): hidden games never show; forced ones always do; others follow the age band. */
object GameVisibility {
    fun isVisible(id: GameId, age: AgeRange, band: AgeBand, hidden: Set<String>, forced: Set<String>): Boolean = when {
        id.name in hidden -> false
        id.name in forced -> true
        else -> age.fits(band)
    }

    /** The S17 switch: turning on a game outside the age band forces it, turning off any game hides it. */
    fun toggle(id: GameId, on: Boolean, age: AgeRange, band: AgeBand, hidden: Set<String>, forced: Set<String>): Pair<Set<String>, Set<String>> =
        if (on) {
            (hidden - id.name) to (if (age.fits(band)) forced - id.name else forced + id.name)
        } else {
            (hidden + id.name) to (forced - id.name)
        }
}

/** Memory grids by age (spec 4.2, docs/02 §5). */
@Serializable
enum class MemoryLevel(val rows: Int, val cols: Int, val key: String) {
    L1(2, 2, "memory_2x2"),
    L2(2, 3, "memory_2x3"),
    L3(2, 4, "memory_2x4"),
    L4(3, 4, "memory_3x4"),
    ;

    val cards: Int get() = rows * cols
    val pairs: Int get() = cards / 2

    companion object {
        fun fromKey(key: String): MemoryLevel? = entries.firstOrNull { it.key == key }
    }
}
