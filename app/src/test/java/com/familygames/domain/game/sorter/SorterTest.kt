package com.familygames.domain.game.sorter

import com.familygames.domain.game.board.DropOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SorterTest {
    @Test
    fun `rounds have distinct holes from the level, matching blocks in another order and different colours`() {
        repeat(40) { seed ->
            SorterLevel.entries.forEach { level ->
                val rounds = SorterDealer(Random(seed)).deal(level)
                assertEquals(level.rounds, rounds.size)
                rounds.forEach { r ->
                    assertEquals(level.holes, r.holes.distinct().size)
                    assertTrue(r.holes.all { it in level.shapes })
                    assertEquals(r.holes.toSet(), r.tray.map { it.shape }.toSet())
                    assertTrue(r.tray.indices.none { r.tray[it].shape == r.holes[it] })
                    assertEquals(r.tray.size, r.tray.map { it.color }.distinct().size)
                }
            }
        }
    }

    @Test
    fun `two rounds in a row differ when the level has shapes to spare`() {
        repeat(40) { seed ->
            val rounds = SorterDealer(Random(seed)).deal(SorterLevel.TWO)
            rounds.zipWithNext().forEach { (a, b) -> assertNotEquals(a.holes.toSet(), b.holes.toSet()) }
        }
    }

    @Test
    fun `a hole takes only its own block, once`() {
        val board = SorterBoard(listOf(BlockShape.CIRCLE, BlockShape.STAR))
        assertEquals(DropOutcome.Wrong, board.drop(BlockShape.STAR, 0))
        assertEquals(DropOutcome.Correct(won = false), board.drop(BlockShape.CIRCLE, 0))
        assertEquals(DropOutcome.Ignored, board.drop(BlockShape.CIRCLE, 0))
        assertEquals(DropOutcome.Ignored, board.drop(BlockShape.STAR, 5))
        assertEquals(DropOutcome.Correct(won = true), board.drop(BlockShape.STAR, 1))
        assertEquals(1, board.mistakes)
        assertEquals(1, board.holeOf(BlockShape.STAR))
    }
}
