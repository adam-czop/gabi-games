package com.familygames.domain.game.puzzle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SwapBoardTest {
    @Test
    fun `adjacency respects row edges`() {
        val b = SwapBoard.solved(3)
        assertTrue(b.areAdjacent(0, 1))
        assertTrue(b.areAdjacent(0, 3))
        assertFalse(b.areAdjacent(2, 3)) // end of row 0 and start of row 1
        assertFalse(b.areAdjacent(0, 4)) // diagonal
        assertEquals(listOf(1, 3, 5, 7), b.neighbours(4))
        assertEquals(listOf(1, 3), b.neighbours(0))
    }

    @Test
    fun `swap exchanges tiles and solves`() {
        val b = SwapBoard(2, listOf(1, 0, 2, 3))
        assertFalse(b.isSolved())
        assertEquals(2, b.correctCount())
        val solved = b.swap(0, 1)
        assertTrue(solved.isSolved())
        assertThrows(IllegalArgumentException::class.java) { b.swap(0, 3) }
    }

    @Test
    fun `non permutation is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SwapBoard(2, listOf(0, 0, 1, 2)) }
    }
}

class ShuffleSwapBoardTest {
    @Test
    fun `shuffle is never solved and misplaces at least 60 percent`() {
        val shuffle = ShuffleSwapBoardUseCase(Random(7))
        repeat(300) {
            listOf(2 to 3, 3 to 6).forEach { (size, minMisplaced) ->
                val b = shuffle(size)
                assertFalse(b.isSolved())
                assertTrue(size * size - b.correctCount() >= minMisplaced)
            }
        }
    }

    @Test
    fun `same seed same board`() {
        assertEquals(ShuffleSwapBoardUseCase(Random(3))(3), ShuffleSwapBoardUseCase(Random(3))(3))
    }
}

class PuzzleScoringTest {
    @Test
    fun `snap stars depend on hints only`() {
        assertEquals(3, SnapScoring.stars(0))
        assertEquals(2, SnapScoring.stars(1))
        assertEquals(1, SnapScoring.stars(2))
        assertEquals(1, SnapScoring.stars(9))
    }

    @Test
    fun `swap stars thresholds`() {
        assertEquals(3, SwapScoring.stars(moves = 8, misplacedAtStart = 4))
        assertEquals(2, SwapScoring.stars(moves = 9, misplacedAtStart = 4))
        assertEquals(2, SwapScoring.stars(moves = 16, misplacedAtStart = 4))
        assertEquals(1, SwapScoring.stars(moves = 17, misplacedAtStart = 4))
    }

    @Test
    fun `magnet distance`() {
        assertTrue(isWithinSnap(100f, 100f, 120f, 120f, thresholdPx = 40f))
        assertFalse(isWithinSnap(100f, 100f, 130f, 130f, thresholdPx = 40f))
        assertEquals(56f, PuzzleTuning.snapDistanceDp(PuzzleDifficulty.SNAP_2X2))
        assertEquals(40f, PuzzleTuning.snapDistanceDp(PuzzleDifficulty.SNAP_2X3))
    }

    @Test
    fun `difficulty lookup`() {
        assertEquals(PuzzleDifficulty.SNAP_2X3, PuzzleDifficulty.snap(2, 3))
        assertEquals(PuzzleDifficulty.SWAP_3X3, PuzzleDifficulty.swap(3))
        assertEquals(PuzzleDifficulty.SNAP_2X2, PuzzleDifficulty.fromKey("nonsense"))
    }
}
