package com.familygames.domain.game.shadow

import com.familygames.domain.game.board.DropOutcome
import com.familygames.domain.game.memory.testPhoto
import com.familygames.domain.model.Photo
import com.familygames.domain.model.PhotoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private fun builtIn(id: Long, category: Long): Photo =
    testPhoto(id).copy(categoryId = category, image = PhotoSource.Asset("builtin/c${category}_$id.webp"))

private fun own(id: Long, category: Long): Photo = testPhoto(id).copy(categoryId = category, image = PhotoSource.LocalFile("/p/$id.jpg"))

class ShadowDealerTest {
    private val groups = mapOf(
        1L to (1L..8L).map { builtIn(it, 1) },
        2L to (11L..18L).map { builtIn(it, 2) },
        3L to (21L..24L).map { own(it, 3) },
    )

    @Test
    fun `rounds use only built-in illustrations, the tray holds the same pictures in another order`() {
        repeat(30) { seed ->
            ShadowLevel.entries.forEach { level ->
                val rounds = ShadowDealer(Random(seed)).deal(groups, level)
                assertEquals(level.rounds, rounds.size)
                rounds.forEach { r ->
                    assertEquals(level.items, r.shadows.size)
                    assertEquals(r.shadows.map { it.id }.toSet(), r.tray.map { it.id }.toSet())
                    assertTrue(r.shadows.all { it.isBuiltIn })
                    assertTrue("seed $seed", r.tray.indices.none { r.tray[it].id == r.shadows[it].id })
                    if (level.sameCategory) assertEquals(1, r.shadows.map { it.categoryId }.distinct().size)
                }
            }
        }
    }

    @Test
    fun `easy levels mix categories and pictures do not repeat while there are fresh ones`() {
        repeat(30) { seed ->
            val rounds = ShadowDealer(Random(seed)).deal(groups, ShadowLevel.TWO)
            rounds.forEach { r -> assertEquals(2, r.shadows.map { it.categoryId }.distinct().size) }
            val all = rounds.flatMap { r -> r.shadows.map { it.id } }
            assertEquals(all.size, all.distinct().size)
        }
    }

    @Test
    fun `no rounds without enough illustrations`() {
        val few = mapOf(1L to listOf(builtIn(1, 1), builtIn(2, 1), builtIn(3, 1)), 2L to (4L..9L).map { own(it, 2) })
        assertTrue(ShadowDealer(Random(1)).deal(few, ShadowLevel.FOUR).isEmpty())
        assertFalse(ShadowDealer.canPlay(few, ShadowLevel.FOUR))
        assertTrue(ShadowDealer.canPlay(few, ShadowLevel.THREE))
        assertEquals(3, ShadowDealer(Random(1)).deal(few, ShadowLevel.THREE).size)
    }
}

class ShadowBoardTest {
    private val pictures = (1L..3L).map { builtIn(it, 1) }

    @Test
    fun `each shadow takes only its own picture, wrong drops count`() {
        val board = ShadowBoard(pictures)
        assertEquals(DropOutcome.Wrong, board.drop(2, 0))
        assertEquals(DropOutcome.Correct(won = false), board.drop(1, 0))
        assertEquals(DropOutcome.Ignored, board.drop(1, 0))
        assertEquals(DropOutcome.Ignored, board.drop(1, 2))
        assertEquals(DropOutcome.Ignored, board.drop(3, 7))
        assertEquals(DropOutcome.Correct(won = false), board.drop(2, 1))
        assertEquals(DropOutcome.Correct(won = true), board.drop(3, 2))
        assertEquals(1, board.mistakes)
        assertEquals(listOf(true, true, true), board.filled)
        assertEquals(2, board.slotOf(3))
    }
}

class SilhouetteTest {
    private val bg = 0xFFF1EDE7.toInt()
    private val ink = 0xFF3B3936.toInt()
    private val fill = 0xFFA9C2B4.toInt()
    private val white = 0xFFFFFFFF.toInt()

    /** A 20 × 20 picture: background, a ring of ink with a white middle at (5..14, 5..14). */
    private fun picture(): IntArray = IntArray(400) { i ->
        val x = i % 20
        val y = i / 20
        when {
            x !in 5..14 || y !in 5..14 -> bg
            x == 5 || x == 14 || y == 5 || y == 14 -> ink
            x < 8 -> fill
            else -> white
        }
    }

    @Test
    fun `background reachable from the edge is cut away, the outlined shape stays whole`() {
        val mask = Silhouette.mask(picture(), 20, 20, listOf(bg))
        assertEquals(100, mask.count { it })
        assertTrue(mask[10 * 20 + 10]) // white middle, closed by the outline
        assertFalse(mask[0])
    }

    @Test
    fun `a one pixel gap in the outline does not let the background into a pale fill`() {
        val pale = 0xFFD8D1C7.toInt()
        val pixels = IntArray(400) { i ->
            val x = i % 20
            val y = i / 20
            when {
                x !in 4..15 || y !in 4..15 -> bg
                y in 4..5 && x == 9 -> bg // the gap through the 2 px outline
                x in 4..5 || x in 14..15 || y in 4..5 || y in 14..15 -> ink
                else -> pale
            }
        }
        val mask = Silhouette.mask(pixels, 20, 20, listOf(bg))
        assertTrue(mask[10 * 20 + 10])
        assertTrue(mask.count { it } >= 140)
    }

    @Test
    fun `pale grey scenery and noisy background are background too`() {
        val road = 0xFF9C958B.toInt()
        val noisy = 0xFFF5EBE0.toInt()
        val pixels = IntArray(400) { i -> if (i / 20 == 17) road else if (i % 2 == 0) noisy else bg }
        assertEquals(0, Silhouette.mask(pixels, 20, 20, listOf(bg)).count { it })
    }

    @Test
    fun `backgrounds of the built-in categories are known by the asset name`() {
        assertTrue(Silhouette.backgroundsFor("builtin/thumbs/zwierzeta_kot.webp").isNotEmpty())
        assertTrue(Silhouette.backgroundsFor("builtin/pojazdy_autobus.webp").isNotEmpty())
        assertTrue(Silhouette.backgroundsFor("photos/abc.jpg").isEmpty())
    }
}
