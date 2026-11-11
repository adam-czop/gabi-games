package com.familygames.domain.game.find

import kotlin.random.Random

/** Levels of "Znajdź na obrazku" (docs/12 M23): free exploring, or questions about big / all things. */
enum class FindLevel(val key: String, val rounds: Int, val smallToo: Boolean) {
    EXPLORE("find_explore", rounds = 0, smallToo = true),
    BIG("find_big", rounds = 5, smallToo = false),
    ALL("find_all", rounds = 5, smallToo = true),
    ;

    val isFree: Boolean get() = this == EXPLORE

    companion object {
        fun fromKey(key: String): FindLevel = entries.firstOrNull { it.key == key } ?: BIG
    }
}

sealed interface FindOutcome {
    /** A tap on the scenery, or while the answer is shown. */
    data object Ignored : FindOutcome
    data class Correct(val last: Boolean) : FindOutcome
    /** [hint] once the right thing should pulse (after two misses in the round). */
    data class Wrong(val hint: Boolean) : FindOutcome
}

/** One game of questions on one scene. */
class FindGame(val scene: Scene, val targets: List<SceneObject>) {
    var round = 0
        private set
    var mistakes = 0
        private set
    private var missesInRound = 0
    private var solved = false

    val current: SceneObject get() = targets[round]
    val finished: Boolean get() = solved && round == targets.lastIndex

    fun tap(objectKey: String?): FindOutcome {
        if (objectKey == null || solved) return FindOutcome.Ignored
        if (objectKey != current.key) {
            mistakes++
            missesInRound++
            return FindOutcome.Wrong(hint = missesInRound >= 2)
        }
        solved = true
        return FindOutcome.Correct(last = round == targets.lastIndex)
    }

    /** Moves to the next question after a right answer. */
    fun next(): Boolean {
        if (!solved || round >= targets.lastIndex) return false
        round++
        solved = false
        missesInRound = 0
        return true
    }

    companion object {
        /** Distinct targets in random order; small things only when the level allows them. */
        fun deal(scene: Scene, level: FindLevel, random: Random): FindGame {
            val pool = scene.objects.filter { level.smallToo || it.big }.shuffled(random)
            // On the "all" level at least two small things, so the level is really harder.
            val picked = if (level.smallToo) {
                val small = pool.filter { !it.big }.take(2)
                (small + pool.filter { it !in small }).take(level.rounds).shuffled(random)
            } else {
                pool.take(level.rounds)
            }
            return FindGame(scene, picked)
        }

        /** A different scene than last time, when there is a choice. */
        fun pickScene(random: Random, previous: String?): Scene =
            Scenes.all.filter { it.key != previous }.ifEmpty { Scenes.all }.let { it[random.nextInt(it.size)] }
    }
}
