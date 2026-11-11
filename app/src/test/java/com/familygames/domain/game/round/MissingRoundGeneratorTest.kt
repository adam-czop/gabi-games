package com.familygames.domain.game.round

import com.familygames.domain.game.memory.testPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MissingRoundGeneratorTest {
    private val pool = (1L..8L).map(::testPhoto)

    @Test
    fun `the right option is the missing photo, the others were not on the stage`() {
        repeat(40) { seed ->
            MissingLevel.entries.forEach { level ->
                val rounds = MissingRoundGenerator(Random(seed)).generate(pool, level)
                assertEquals(level.rounds, rounds.size)
                rounds.forEach { (task, round) ->
                    assertEquals(level.shown, task.shown.size)
                    assertEquals(level.shown, task.shown.map { it.id }.distinct().size)
                    assertEquals(task.shown[task.gap], round.correct)
                    assertEquals(MissingLevel.OPTIONS, round.options.size)
                    round.options.filterIndexed { i, _ -> i != round.correctIndex }.forEach { assertFalse(it in task.shown) }
                }
            }
        }
    }

    @Test
    fun `the gap and the answer never sit in one place three times running`() {
        repeat(40) { seed ->
            val rounds = MissingRoundGenerator(Random(seed)).generate(pool, MissingLevel.THREE)
            rounds.map { it.first.gap }.windowed(3).forEach { assertTrue(it.distinct().size > 1) }
            rounds.map { it.second.correctIndex }.windowed(3).forEach { assertTrue(it.distinct().size > 1) }
        }
    }

    @Test
    fun `too few photos give no game`() {
        assertTrue(MissingRoundGenerator(Random(1)).generate(pool.take(5), MissingLevel.FOUR).isEmpty())
        assertEquals(5, MissingRoundGenerator(Random(1)).generate(pool.take(5), MissingLevel.THREE).size)
    }
}
