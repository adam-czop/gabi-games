package com.familygames.domain.game.echo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EchoGameTest {
    /** Repeats the current tune perfectly and returns the last outcome. */
    private fun EchoGame.repeatTune(): EchoOutcome {
        heard()
        var last: EchoOutcome = EchoOutcome.Ignored
        tune.forEach { last = tap(it) }
        return last
    }

    @Test
    fun `the tune grows by one note to the level length, then the game is won`() {
        repeat(20) { seed ->
            EchoLevel.entries.forEach { level ->
                val game = EchoGame(level, Random(seed))
                assertEquals(1, game.start().size)
                for (length in 1 until level.target) {
                    assertEquals(EchoOutcome.Good(tuneDone = true), game.repeatTune())
                    assertEquals(length + 1, game.extend().size)
                }
                assertEquals(EchoOutcome.Won, game.repeatTune())
                assertTrue(game.won)
                assertEquals(0, game.mistakes)
            }
        }
    }

    @Test
    fun `taps are ignored while the tune plays, a wrong note replays the same tune`() {
        val game = EchoGame(EchoLevel.FOUR, Random(3))
        game.start()
        game.repeatTune()
        val tune = game.extend()
        assertEquals(EchoOutcome.Ignored, game.tap(tune[0]))
        game.heard()
        val wrong = (tune[0] + 1) % EchoLevel.FOUR.bars.size
        assertEquals(EchoOutcome.Wrong, game.tap(wrong))
        assertEquals(1, game.mistakes)
        assertEquals(tune, game.tune)
        assertEquals(EchoOutcome.Ignored, game.tap(tune[0]))
        assertEquals(EchoOutcome.Good(tuneDone = true), game.repeatTune())
    }

    @Test
    fun `notes stay inside the level and never come three times in a row`() {
        repeat(50) { seed ->
            val game = EchoGame(EchoLevel.THREE, Random(seed))
            game.start()
            repeat(EchoLevel.THREE.target - 1) {
                game.repeatTune()
                game.extend()
            }
            assertTrue(game.tune.all { it in EchoLevel.THREE.bars.indices })
            game.tune.windowed(3).forEach { assertTrue(it.distinct().size > 1) }
        }
    }
}
