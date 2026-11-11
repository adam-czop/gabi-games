package com.familygames.domain.game.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WeatherTest {
    @Test
    fun `every weather once, the right things and the level's odd ones in the tray`() {
        repeat(30) { seed ->
            WeatherLevel.entries.forEach { level ->
                val rounds = WeatherDealer(Random(seed)).deal(level)
                assertEquals(WeatherKind.entries.toSet(), rounds.map { it.weather }.toSet())
                rounds.forEach { r ->
                    assertEquals(2, r.needed.size)
                    assertEquals(level.wrong, r.tray.count { it.weather != r.weather })
                    assertEquals(r.tray.size, r.tray.distinct().size)
                }
            }
        }
    }

    @Test
    fun `the figure takes what suits the weather, once, and is dressed when both are on`() {
        val dresser = Dresser(WeatherRound(WeatherKind.RAIN, listOf(Garment.BOOTS, Garment.HAT, Garment.UMBRELLA)))
        assertEquals(Dresser.Outcome.Wrong, dresser.put(Garment.HAT))
        assertEquals(Dresser.Outcome.Ignored, dresser.put(Garment.CAP))
        assertEquals(Dresser.Outcome.Worn(dressed = false), dresser.put(Garment.BOOTS))
        assertEquals(Dresser.Outcome.Ignored, dresser.put(Garment.BOOTS))
        assertEquals(Dresser.Outcome.Worn(dressed = true), dresser.put(Garment.UMBRELLA))
        assertEquals(1, dresser.mistakes)
        assertTrue(Garment.UMBRELLA in dresser.wearing)
    }
}
