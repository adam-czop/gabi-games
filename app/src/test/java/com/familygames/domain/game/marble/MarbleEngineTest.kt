package com.familygames.domain.game.marble

import com.familygames.domain.game.maze.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MarbleEngineTest {
    private val line = MarblePath(listOf(Point(0f, 200f), Point(1200f, 200f)))

    /** An engine with the chain already on the board: [colors] from the rear, the rear marble at [rear]. */
    private fun engine(colors: List<Int>, level: MarbleLevel = MarbleLevel.L2, rear: Float = 200f, seed: Int = 1): MarbleEngine =
        MarbleEngine(line, level, Random(seed), colors).also { e ->
            e.segments.single().rear = rear
            e.step(0f)
        }

    /** An engine whose cannon holds [color] (seeds tried in turn; a miss gives the same marble back). */
    private fun holding(color: Int, colors: List<Int>, level: MarbleLevel = MarbleLevel.L2): MarbleEngine {
        for (seed in 1..500) {
            val e = engine(colors, level, seed = seed)
            if (e.current == color) return e
            if (e.next == color) return e.also { it.swap() }
        }
        error("no seed gives colour $color")
    }

    private fun MarbleEngine.run(seconds: Float): List<MarbleEvent> = (1..(seconds * 60).toInt()).flatMap { step(1f / 60) }

    /** Shoots the cannon's marble straight at the marble [index] of the first segment. */
    private fun MarbleEngine.shootAt(index: Int): List<MarbleEvent> {
        shoot(path.pointAt(segments.first().posOf(index)))
        return run(1f)
    }

    @Test
    fun `starting chains never hold a run that already pops, and use only the level colours`() {
        repeat(50) { seed ->
            MarbleLevel.entries.forEach { level ->
                val chain = MarbleEngine.deal(level, Random(seed))
                assertEquals(level.balls, chain.size)
                assertTrue(chain.all { it in 0 until level.colors })
                var run = 1
                for (i in 1 until chain.size) {
                    run = if (chain[i] == chain[i - 1]) run + 1 else 1
                    assertTrue("seed $seed $level $chain", run < level.match)
                }
            }
        }
    }

    @Test
    fun `a marble next to its colour pops the run, a different one just joins the chain`() {
        val e = holding(1, listOf(0, 0, 1, 1, 2, 2))
        val events = e.shootAt(2)
        assertTrue(events.any { it is MarbleEvent.Popped && it.count == 3 })
        assertEquals(4, e.remaining)
        assertEquals(0, e.wasted)
    }

    @Test
    fun `a miss gives the marble back and is not counted`() {
        val e = engine(listOf(0, 1, 0, 1))
        val before = e.current
        e.shoot(Point(600f, 2000f)) // straight down, away from the chain
        val events = e.run(2f)
        assertTrue(MarbleEvent.Missed in events)
        assertEquals(before, e.current)
        assertEquals(4, e.remaining)
        assertEquals(0, e.wasted)
    }

    @Test
    fun `when a pop leaves the same colour on both sides, the gap closes and pops again`() {
        // 0 0 | 1 1 | 0: popping the 1s brings 0 0 and 0 together.
        val e = holding(1, listOf(0, 0, 1, 1, 0))
        val popped = (e.shootAt(2) + e.run(3f)).filterIsInstance<MarbleEvent.Popped>()
        assertEquals(listOf(1, 2), popped.map { it.combo })
        assertTrue(e.won)
    }

    @Test
    fun `the chain stops before the end, so the game cannot be lost`() {
        val e = MarbleEngine(MarbleLevel.L1.path(), MarbleLevel.L1, Random(3))
        e.run(600f)
        val front = e.segments.last()
        assertTrue(front.front <= e.stopAt + 0.01f)
        assertFalse(e.won)
        assertEquals(MarbleLevel.L1.balls, e.remaining)
    }

    @Test
    fun `the cannon only holds colours that are still on the board`() {
        repeat(20) { seed ->
            val e = engine(listOf(0, 2, 0, 2, 3, 3), level = MarbleLevel.L4, seed = seed)
            assertTrue(e.current in setOf(0, 2, 3) && e.next in setOf(0, 2, 3))
        }
    }

    @Test
    fun `clearing the board wins`() {
        val e = holding(0, listOf(0, 1), level = MarbleLevel.L1)
        val events = e.shootAt(0) + e.shootAt(0)
        assertTrue(e.won)
        assertTrue(MarbleEvent.Won in events)
    }

    @Test
    fun `stars count only marbles that popped nothing`() {
        assertEquals(3, Marbles.stars(balls = 24, wasted = 6))
        assertEquals(2, Marbles.stars(balls = 24, wasted = 12))
        assertEquals(1, Marbles.stars(balls = 24, wasted = 13))
    }

    @Test
    fun `the cannon stays on the board`() {
        assertTrue(Marbles.CANNON.y + 62f <= Marbles.BOARD_H)
    }

    @Test
    fun `level paths fit the board and leave room for the cannon`() {
        MarbleLevel.entries.forEach { level ->
            val path = level.path()
            assertTrue("$level is too short", path.length > level.balls * Marbles.DIAMETER * 1.4f)
            path.points.forEach { p ->
                assertTrue("$level $p", p.x in 0f..Marbles.BOARD_W && p.y in Marbles.DIAMETER / 2..Marbles.BOARD_H - Marbles.DIAMETER / 2)
                assertTrue("$level $p near the cannon", p.distanceTo(Marbles.CANNON) > 110f)
            }
        }
    }
}
