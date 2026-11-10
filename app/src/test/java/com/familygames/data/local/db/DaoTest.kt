package com.familygames.data.local.db

import com.familygames.data.category
import com.familygames.data.inMemoryDb
import com.familygames.data.photo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DaoTest {
    private lateinit var db: FamilyGamesDatabase
    private val categories get() = db.categoryDao()
    private val photos get() = db.photoDao()
    private val results get() = db.gameResultDao()

    @Before fun setUp() { db = inMemoryDb() }

    @After fun tearDown() = db.close()

    @Test
    fun `memory photo count only counts photos enabled for memory`() = runTest {
        val home = categories.insert(category("Dom"))
        photos.insert(photo(home, "photos/a.jpg"))
        photos.insert(photo(home, "photos/b.jpg"))
        photos.insert(photo(home, "photos/c.jpg", useInMemory = false))

        assertEquals(2, photos.memoryPhotoCount(home).first())
        assertEquals(2, photos.getForMemory(home).size)
    }

    @Test
    fun `deleting a category cascades to its photos only`() = runTest {
        val home = categories.insert(category("Dom"))
        val toys = categories.insert(category("Zabawki", sortOrder = 1))
        photos.insert(photo(home, "photos/a.jpg"))
        photos.insert(photo(toys, "photos/b.jpg"))

        categories.delete(home)

        assertEquals(1, photos.count())
        assertEquals(toys, photos.observeAll().first().single().categoryId)
    }

    @Test
    fun `puzzle list skips hidden categories and disabled photos, newest first`() = runTest {
        val visible = categories.insert(category("Dom"))
        val hidden = categories.insert(category("Ukryta", sortOrder = 1, hidden = true))
        photos.insert(photo(visible, "photos/old.jpg", createdAt = 1))
        photos.insert(photo(visible, "photos/new.jpg", createdAt = 2))
        photos.insert(photo(visible, "photos/off.jpg", useInPuzzle = false, createdAt = 3))
        photos.insert(photo(hidden, "photos/hidden.jpg", createdAt = 4))

        assertEquals(listOf("photos/new.jpg", "photos/old.jpg"), photos.observeForPuzzle().first().map { it.filePath })
    }

    @Test
    fun `category name lookup ignores case and sort order grows`() = runTest {
        assertEquals(0, categories.nextSortOrder())
        categories.insert(category("Nasz dom"))
        assertEquals("Nasz dom", categories.findByName("NASZ DOM")?.name)
        assertNull(categories.findByName("Zwierzęta"))
        assertEquals(1, categories.nextSortOrder())
    }

    @Test
    fun `photo path lookups`() = runTest {
        val home = categories.insert(category("Dom"))
        photos.insert(photo(home, "asset://builtin/dom_kanapa.webp"))

        assertTrue(photos.existsByPath("asset://builtin/dom_kanapa.webp"))
        assertFalse(photos.existsByPath("asset://builtin/dom_lozko.webp"))
        assertEquals(home, photos.findCategoryIdByPath("asset://builtin/dom_kanapa.webp"))
    }

    @Test
    fun `best stars per level and retention`() = runTest {
        results.insert(result("MEMORY", "memory_2x2", stars = 2, finishedAt = 100))
        results.insert(result("MEMORY", "memory_2x2", stars = 3, finishedAt = 200))
        results.insert(result("MEMORY", "memory_2x3", stars = 1, finishedAt = 300))
        results.insert(result("PUZZLE_SNAP", "snap_2x2", stars = 3, finishedAt = 400))

        val best = results.bestStarsByLevel("MEMORY").first().associate { it.levelKey to it.best }
        assertEquals(mapOf("memory_2x2" to 3, "memory_2x3" to 1), best)

        assertEquals(2, results.deleteOlderThan(cutoff = 300))
        assertEquals(listOf(400L, 300L), results.observeSince(0).first().map { it.finishedAt })
    }

    private fun result(game: String, level: String, stars: Int, finishedAt: Long) = GameResultEntity(
        gameId = game, levelKey = level, stars = stars, moves = 4, mistakes = 0, hintsUsed = 0, durationMs = 1000, finishedAt = finishedAt,
    )
}
