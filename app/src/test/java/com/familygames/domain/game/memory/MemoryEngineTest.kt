package com.familygames.domain.game.memory

import com.familygames.domain.model.Aspect
import com.familygames.domain.model.MemoryLevel
import com.familygames.domain.model.Photo
import com.familygames.domain.model.PhotoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

fun testPhoto(id: Long) = Photo(
    id = id, categoryId = 1, image = PhotoSource.Asset("p$id"), thumb = PhotoSource.Asset("t$id"), aspect = Aspect.SQUARE,
    label = "P$id", audioPath = null, useInMemory = true, useInPuzzle = true, sortOrder = 0, createdAt = 0,
)

class DealMemoryCardsUseCaseTest {
    private val pool = (1L..8L).map(::testPhoto)

    @Test
    fun `every level deals each chosen photo exactly twice`() {
        MemoryLevel.entries.forEach { level ->
            val cards = DealMemoryCardsUseCase(Random(1))(level, pool)
            assertEquals(level.cards, cards.size)
            assertEquals(level.pairs, cards.map { it.pairKey }.distinct().size)
            assertTrue(cards.groupBy { it.pairKey }.values.all { it.size == 2 })
            assertEquals(cards.size, cards.map { it.id }.distinct().size)
        }
    }

    @Test
    fun `same seed gives the same deal`() {
        val a = DealMemoryCardsUseCase(Random(42))(MemoryLevel.L4, pool)
        val b = DealMemoryCardsUseCase(Random(42))(MemoryLevel.L4, pool)
        assertEquals(a, b)
    }

    @Test
    fun `photos not used in the last game are preferred`() {
        val recent = setOf(1L, 2L, 3L, 4L, 5L)
        repeat(20) { seed ->
            val cards = DealMemoryCardsUseCase(Random(seed))(MemoryLevel.L2, pool, recentlyUsed = recent)
            assertEquals(setOf(6L, 7L, 8L), cards.map { it.pairKey }.toSet())
        }
    }

    @Test
    fun `too small pool is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { DealMemoryCardsUseCase(Random(1))(MemoryLevel.L4, pool.take(5)) }
    }

    @Test
    fun `small grids avoid layouts with several pairs side by side`() {
        val dealer = DealMemoryCardsUseCase(Random(3))
        val easy = (0 until 200).count { dealer.horizontalPairs(dealer(MemoryLevel.L1, pool), cols = 2) >= 2 }
        val raw = DealMemoryCardsUseCase(Random(3), avoidEasyLayouts = false)
        val rawEasy = (0 until 200).count { raw.horizontalPairs(raw(MemoryLevel.L1, pool), cols = 2) >= 2 }
        assertTrue("filtered=$easy raw=$rawEasy", easy < rawEasy)
    }
}

class MemoryEngineTest {
    // Fixed layout: pairs (1,1) (2,2) → card ids 2,3 for photo 1 and 4,5 for photo 2.
    private fun engine() = MemoryEngine(
        listOf(1L, 2L).flatMap { p -> listOf(MemoryCard(2 * p, p, testPhoto(p)), MemoryCard(2 * p + 1, p, testPhoto(p))) },
    )

    @Test
    fun `matching pair stays face up and counts a move`() {
        val e = engine()
        assertEquals(TapOutcome.FirstUp, e.tap(2))
        assertEquals(TapOutcome.SecondUp, e.tap(3))
        assertTrue(e.state.inputLocked)
        assertEquals(CheckOutcome.Match(pairKey = 1, won = false), e.check())
        assertEquals(MemoryPhase.Idle, e.state.phase)
        assertEquals(1, e.state.pairsFound)
        assertEquals(1, e.state.moves)
        assertEquals(0, e.state.mistakes)
    }

    @Test
    fun `mismatch locks input until the cards are covered`() {
        val e = engine()
        e.tap(2)
        e.tap(4)
        assertEquals(CheckOutcome.Mismatch, e.check())
        assertEquals(MemoryPhase.Mismatch, e.state.phase)
        assertEquals(TapOutcome.Ignored, e.tap(5)) // a third card can never be revealed
        assertEquals(1, e.state.mistakes)

        e.coverMismatch()
        assertEquals(MemoryPhase.Idle, e.state.phase)
        assertTrue(e.state.cards.none { it.faceUp })
    }

    @Test
    fun `taps on face-up, matched, unknown cards or during checking are ignored`() {
        val e = engine()
        e.tap(2)
        assertEquals(TapOutcome.Ignored, e.tap(2)) // same card again
        assertEquals(TapOutcome.Ignored, e.tap(99))
        e.tap(3)
        assertEquals(TapOutcome.Ignored, e.tap(4)) // Checking
        e.check()
        assertEquals(TapOutcome.Ignored, e.tap(3)) // matched
        assertEquals(1, e.state.moves)
    }

    @Test
    fun `last pair wins and locks the board`() {
        val e = engine()
        e.tap(2); e.tap(3); e.check()
        e.tap(5); e.tap(4)
        assertEquals(CheckOutcome.Match(pairKey = 2, won = true), e.check())
        assertEquals(MemoryPhase.Won, e.state.phase)
        assertTrue(e.state.inputLocked)
        assertEquals(TapOutcome.Ignored, e.tap(2))
        assertEquals(2 to 2, e.state.pairsFound to e.state.totalPairs)
    }

    @Test
    fun `cover outside mismatch does nothing`() {
        val e = engine()
        e.tap(2)
        e.coverMismatch()
        assertEquals(MemoryPhase.OneUp, e.state.phase)
        assertFalse(e.state.cards.first { it.card.id == 2L }.let { !it.faceUp })
    }
}

class MemoryScoringTest {
    @Test
    fun `thresholds`() {
        assertEquals(3, MemoryScoring.stars(pairs = 3, mistakes = 0))
        assertEquals(3, MemoryScoring.stars(pairs = 3, mistakes = 3))
        assertEquals(2, MemoryScoring.stars(pairs = 3, mistakes = 4))
        assertEquals(2, MemoryScoring.stars(pairs = 3, mistakes = 6))
        assertEquals(1, MemoryScoring.stars(pairs = 3, mistakes = 7))
        assertEquals(1, MemoryScoring.stars(pairs = 2, mistakes = 100))
    }
}
