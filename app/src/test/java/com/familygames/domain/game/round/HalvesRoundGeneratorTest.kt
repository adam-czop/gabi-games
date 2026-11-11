package com.familygames.domain.game.round

import com.familygames.domain.game.memory.testPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HalvesRoundGeneratorTest {
    private val pool = (1L..8L).map(::testPhoto)

    @Test
    fun `each round holds its own half and others, targets do not repeat while there are fresh ones`() {
        repeat(40) { seed ->
            HalvesLevel.entries.forEach { level ->
                val rounds = HalvesRoundGenerator(Random(seed)).generate(pool, level)
                assertEquals(level.rounds, rounds.size)
                rounds.forEach { r ->
                    assertEquals(level.options, r.options.size)
                    assertEquals(level.options, r.options.map { it.id }.distinct().size)
                }
                assertEquals(level.rounds, rounds.map { it.correct.id }.distinct().size)
                rounds.map { it.correctIndex }.windowed(3).forEach { assertTrue(it.distinct().size > 1) }
            }
        }
    }

    @Test
    fun `two photos with one label never compete`() {
        val twins = listOf(testPhoto(1).copy(label = "babcia"), testPhoto(2).copy(label = "babcia"), testPhoto(3).copy(label = "dziadek"))
        repeat(20) { seed ->
            HalvesRoundGenerator(Random(seed)).generate(twins, HalvesLevel.TWO).forEach { r ->
                assertEquals(2, r.options.map { it.label }.distinct().size)
            }
        }
    }

    @Test
    fun `a pool smaller than the options gives no game`() {
        assertTrue(HalvesRoundGenerator(Random(1)).generate(pool.take(2), HalvesLevel.THREE).isEmpty())
    }
}
