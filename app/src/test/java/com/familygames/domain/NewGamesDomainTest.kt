package com.familygames.domain

import com.familygames.domain.game.board.DropOutcome
import com.familygames.domain.game.board.FreshPicker
import com.familygames.domain.game.board.ScratchGrid
import com.familygames.domain.game.board.SequenceBoard
import com.familygames.domain.game.board.SortingBoard
import com.familygames.domain.game.board.SortingLevel
import com.familygames.domain.game.board.UndoStack
import com.familygames.domain.game.board.Xylophone
import com.familygames.domain.game.coloring.ColoringPage
import com.familygames.domain.game.coloring.ColoringPages
import com.familygames.domain.game.round.CountingLevel
import com.familygames.domain.game.round.CountingRoundGenerator
import com.familygames.domain.game.round.OddLevel
import com.familygames.domain.game.round.OddOneOutRoundGenerator
import com.familygames.domain.game.round.PositionPicker
import com.familygames.domain.game.round.ScatterLayout
import com.familygames.domain.game.round.WhereIsLevel
import com.familygames.domain.game.round.WhereIsRoundGenerator
import com.familygames.domain.game.memory.testPhoto
import com.familygames.domain.model.Photo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

private fun photo(id: Long, category: Long = 1, label: String? = "P$id", countable: Boolean = false): Photo =
    testPhoto(id).copy(categoryId = category, label = label, countable = countable)

class WhereIsRoundGeneratorTest {
    private val pool = (1L..10L).map { photo(it) }

    @Test
    fun `targets are unique, right answer always present, options from the pool`() {
        repeat(20) { seed ->
            WhereIsLevel.entries.forEach { level ->
                val rounds = WhereIsRoundGenerator(Random(seed)).generate(pool, level)
                assertEquals(level.rounds, rounds.size)
                assertEquals(rounds.size, rounds.map { it.correct.id }.distinct().size)
                rounds.forEach { r ->
                    assertEquals(level.options, r.options.size)
                    assertEquals(level.options, r.options.map { it.id }.distinct().size)
                    assertTrue(r.options.all { it in pool })
                }
            }
        }
    }

    @Test
    fun `right answer is never in the same place three times in a row`() {
        repeat(50) { seed ->
            val positions = WhereIsRoundGenerator(Random(seed)).generate(pool, WhereIsLevel.FOUR).map { it.correctIndex }
            positions.windowed(3).forEach { assertFalse("seed $seed: $positions", it.distinct().size == 1) }
        }
    }

    @Test
    fun `only photos with a label or a recorded question are asked about`() {
        val mixed = (1L..6L).map { photo(it, label = if (it <= 2) "P$it" else null) }
        val rounds = WhereIsRoundGenerator(Random(3)).generate(mixed, WhereIsLevel.TWO)
        assertEquals(2, rounds.size)
        assertTrue(rounds.all { it.correct.id <= 2 })
    }

    @Test
    fun `distractors prefer other labels`() {
        val twins = listOf(photo(1, label = "Babcia"), photo(2, label = "Babcia"), photo(3, label = "Dziadek"), photo(4, label = "Mama"))
        repeat(10) { seed ->
            WhereIsRoundGenerator(Random(seed)).generate(twins, WhereIsLevel.TWO).forEach { r ->
                assertEquals(r.options.size, r.options.map { it.label }.distinct().size)
            }
        }
    }

    @Test
    fun `too few photos give no rounds`() {
        assertTrue(WhereIsRoundGenerator(Random(1)).generate(pool.take(2), WhereIsLevel.THREE).isEmpty())
    }

    @Test
    fun `position picker allows two in a row but not three`() {
        val picker = PositionPicker(Random(1))
        val picks = List(300) { picker.next(2) }
        picks.windowed(3).forEach { assertTrue(it.distinct().size > 1) }
    }
}

class OddOneOutRoundGeneratorTest {
    private val groups = mapOf(
        1L to (1L..5L).map { photo(it, 1) },
        2L to (6L..10L).map { photo(it, 2) },
        3L to (11L..13L).map { photo(it, 3) },
    )

    @Test
    fun `one photo from another category, the rest from one category`() {
        repeat(20) { seed ->
            OddLevel.entries.forEach { level ->
                val rounds = OddOneOutRoundGenerator(Random(seed)).generate(groups, level)
                assertEquals(level.rounds, rounds.size)
                rounds.forEach { r ->
                    val odd = r.correct
                    val rest = r.options - odd
                    assertEquals(level.options - 1, rest.size)
                    assertEquals(1, rest.map { it.categoryId }.distinct().size)
                    assertTrue(odd.categoryId != rest.first().categoryId)
                }
            }
        }
    }

    @Test
    fun `the same pair of categories is not used twice in a row`() {
        repeat(20) { seed ->
            val pairs = OddOneOutRoundGenerator(Random(seed)).generate(groups, OddLevel.FOUR).map { r -> r.options.map { it.categoryId }.toSet() }
            pairs.zipWithNext().forEach { (a, b) -> assertTrue(a != b) }
        }
    }

    @Test
    fun `needs two categories`() {
        assertTrue(OddOneOutRoundGenerator(Random(1)).generate(mapOf(1L to groups.getValue(1L)), OddLevel.THREE).isEmpty())
    }
}

class CountingTest {
    @Test
    fun `counts stay within the level and never repeat twice in a row`() {
        val countable = (1L..3L).map { photo(it, countable = true) }
        repeat(20) { seed ->
            CountingLevel.entries.forEach { level ->
                val tasks = CountingRoundGenerator(Random(seed)).generateTasks(countable, level)
                assertEquals(level.rounds, tasks.size)
                assertTrue(tasks.all { it.count in 1..level.max })
                tasks.zipWithNext().forEach { (a, b) -> assertTrue(a.count != b.count) }
            }
        }
    }

    @Test
    fun `answer index matches the count`() {
        val rounds = CountingRoundGenerator(Random(4)).generate(listOf(photo(1, countable = true)), CountingLevel.TO_5)
        rounds.forEach { assertEquals(it.correctIndex + 1, it.correct) }
    }

    @Test
    fun `scattered tiles never overlap`() {
        repeat(30) { seed ->
            val spots = ScatterLayout.place(5, 1040f, 330f, 140f, 16f, Random(seed))
            assertEquals(5, spots.size)
            spots.forEach { assertTrue(it.x in 0f..900f && it.y in 0f..190f) }
            for (i in spots.indices) for (j in i + 1 until spots.size) {
                assertTrue(hypot(spots[i].x - spots[j].x, spots[i].y - spots[j].y) >= 140f)
            }
        }
    }
}

class SortingBoardTest {
    private val groups = mapOf(1L to (1L..4L).map { photo(it, 1) }, 2L to (5L..8L).map { photo(it, 2) }, 3L to listOf(photo(9, 3)))

    @Test
    fun `deal uses categories with at least three photos`() {
        val board = SortingBoard.deal(groups, SortingLevel.TWO, Random(1))!!
        assertEquals(setOf(1L, 2L), board.baskets.toSet())
        assertEquals(6, board.trayItems.size)
        assertNull(SortingBoard.deal(groups, SortingLevel.THREE, Random(1)))
    }

    @Test
    fun `wrong basket counts a mistake and keeps the photo, right basket wins at the end`() {
        val board = SortingBoard.deal(groups, SortingLevel.TWO, Random(2))!!
        val first = board.trayItems.first()
        val wrong = board.baskets.first { it != first.basket }
        assertEquals(DropOutcome.Wrong, board.drop(first.photo.id, wrong))
        assertEquals(1, board.mistakes)
        assertEquals(6, board.trayItems.size)
        var last: DropOutcome = DropOutcome.Ignored
        board.trayItems.forEach { last = board.drop(it.photo.id, it.basket) }
        assertEquals(DropOutcome.Correct(won = true), last)
        assertEquals(mapOf(1L to 3, 2L to 3), board.sorted)
    }
}

class SequenceBoardTest {
    private val steps = (1L..4L).map { photo(it) }

    @Test
    fun `easy mode accepts only the first free slot`() {
        val board = SequenceBoard(steps, easy = true)
        assertEquals(DropOutcome.Ignored, board.drop(2, 1))
        assertEquals(DropOutcome.Wrong, board.drop(2, 0))
        assertEquals(DropOutcome.Correct(won = false), board.drop(1, 0))
        assertEquals(1, board.firstFree)
        assertEquals(1, board.mistakes)
    }

    @Test
    fun `free mode fills any slot and wins when all are right`() {
        val board = SequenceBoard(steps, easy = false)
        assertEquals(DropOutcome.Correct(won = false), board.drop(3, 2))
        assertEquals(DropOutcome.Ignored, board.drop(3, 2))
        board.drop(1, 0)
        board.drop(4, 3)
        assertEquals(DropOutcome.Correct(won = true), board.drop(2, 1))
    }
}

class FreePlayLogicTest {
    @Test
    fun `fresh picker never repeats one of the last five`() {
        val picker = FreshPicker<Int>(Random(1))
        val pool = (1..8).toList()
        val picks = List(200) { picker.next(pool)!! }
        picks.windowed(6).forEach { assertEquals(6, it.distinct().size) }
    }

    @Test
    fun `fresh picker works with a tiny pool`() {
        val picker = FreshPicker<Int>(Random(1))
        val picks = List(20) { picker.next(listOf(1, 2))!! }
        picks.zipWithNext().forEach { (a, b) -> assertTrue(a != b) }
        assertEquals(1, FreshPicker<Int>(Random(1)).next(listOf(1)))
        assertNull(FreshPicker<Int>(Random(1)).next(emptyList()))
    }

    @Test
    fun `scratch grid reaches the reveal threshold only when most of it is wiped`() {
        val grid = ScratchGrid()
        grid.scratch(0.5f, 0.5f, 0.1f)
        assertTrue(grid.fraction < ScratchGrid.REVEAL_AT)
        var y = 0f
        while (y <= 1f) {
            var x = 0f
            while (x <= 1f) {
                grid.scratch(x, y, 0.06f)
                x += 0.05f
            }
            y += 0.05f
        }
        assertTrue(grid.fraction >= ScratchGrid.REVEAL_AT)
        val before = grid.clearedCount
        grid.scratch(0.5f, 0.5f, 0.1f)
        assertEquals(before, grid.clearedCount)
    }

    @Test
    fun `melody uses only existing bars`() {
        assertTrue(Xylophone.MELODY.all { it in 0 until Xylophone.BARS })
        assertEquals(Xylophone.BARS, Xylophone.NOTE_NAMES.size)
    }

    @Test
    fun `undo keeps at most ten steps`() {
        val stack = UndoStack<Int>(10)
        repeat(15) { stack.push(it) }
        assertEquals(10, stack.size)
        assertEquals(14, stack.pop())
        repeat(9) { assertNotNull(stack.pop()) }
        assertNull(stack.pop())
    }
}

class ColoringPagesTest {
    @Test
    fun `twelve pages, every region closed and inside the sheet`() {
        assertEquals(12, ColoringPages.all.size)
        assertEquals(12, ColoringPages.all.map { it.key }.distinct().size)
        ColoringPages.all.forEach { page ->
            assertTrue(page.key, page.regions.size >= 3)
            page.regions.forEach { r ->
                assertTrue(r.xs.size >= 3)
                assertTrue(page.key, r.xs.all { it in 0f..ColoringPage.WIDTH } && r.ys.all { it in 0f..ColoringPage.HEIGHT })
            }
        }
    }

    @Test
    fun `tap picks the front-most region and ignores the background`() {
        val house = ColoringPages.all.first { it.key == "house" }
        assertEquals(3, house.regionAt(500f, 570f)) // door in front of the wall
        assertEquals(1, house.regionAt(300f, 600f)) // wall
        assertNull(house.regionAt(20f, 20f))
    }
}
