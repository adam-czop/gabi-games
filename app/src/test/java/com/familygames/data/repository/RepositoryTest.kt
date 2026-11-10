package com.familygames.data.repository

import android.graphics.Bitmap
import com.familygames.data.FakeClock
import com.familygames.data.inMemoryDb
import com.familygames.data.local.db.FamilyGamesDatabase
import com.familygames.data.local.files.MediaStorage
import com.familygames.domain.model.Aspect
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.GameResult
import com.familygames.domain.model.NewPhoto
import com.familygames.domain.model.PhotoSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: FamilyGamesDatabase
    private lateinit var storage: MediaStorage
    private lateinit var categories: CategoryRepositoryImpl
    private lateinit var photos: PhotoRepositoryImpl
    private val clock = FakeClock()

    @Before
    fun setUp() {
        db = inMemoryDb()
        storage = MediaStorage(tmp.newFolder("files"), tmp.newFolder("cache"))
        categories = CategoryRepositoryImpl(db, db.categoryDao(), db.photoDao(), storage, clock)
        photos = PhotoRepositoryImpl(db.photoDao(), storage, clock)
    }

    @After fun tearDown() = db.close()

    private suspend fun addStoredPhoto(categoryId: Long, label: String? = null): Long {
        val stored = storage.savePhoto(Bitmap.createBitmap(200, 150, Bitmap.Config.ARGB_8888))
        return photos.add(NewPhoto(categoryId, stored.filePath, stored.thumbPath, Aspect.FOUR_THREE, label = label))
    }

    @Test
    fun `category names are trimmed, limited and unique`() = runTest {
        val id = categories.create("  Rodzina ")
        assertEquals("Rodzina", categories.get(id)?.name)
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { categories.create("rodzina") } }
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { categories.create(" ") } }
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { categories.create("x".repeat(25)) } }
    }

    @Test
    fun `deleting a user category removes its photo files`() = runTest {
        val family = categories.create("Rodzina")
        val photoId = addStoredPhoto(family)
        val file = (photos.get(photoId)!!.image as PhotoSource.LocalFile).path

        categories.delete(family)

        assertNull(categories.get(family))
        assertNull(photos.get(photoId))
        assertFalse(java.io.File(file).exists())
    }

    @Test
    fun `built-in photos and categories are protected`() = runTest {
        val builtIn = db.categoryDao().insert(com.familygames.data.category("Zwierzęta", builtIn = true))
        val kot = db.photoDao().insert(com.familygames.data.photo(builtIn, "asset://builtin/zwierzeta_kot.webp"))
        val own = addStoredPhoto(builtIn)

        assertEquals(1, photos.delete(listOf(kot, own)))
        assertTrue(photos.get(kot)!!.isBuiltIn)
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { categories.delete(builtIn) } }
    }

    @Test
    fun `summaries count photos per game and pick the cover`() = runTest {
        val family = categories.create("Rodzina")
        val first = addStoredPhoto(family)
        val second = addStoredPhoto(family)
        photos.updateDetails(second, label = "  Babcia  ", categoryId = family, useInMemory = false, useInPuzzle = true)

        var summary = categories.observeSummaries().first().single()
        assertEquals(2, summary.photoCount)
        assertEquals(1, summary.memoryCount)
        assertEquals(2, summary.puzzleCount)
        assertEquals(photos.get(first)!!.thumb, summary.cover)
        assertEquals("Babcia", photos.get(second)!!.label)

        categories.setCover(family, second)
        summary = categories.observeSummaries().first().single()
        assertEquals(photos.get(second)!!.thumb, summary.cover)
    }

    @Test
    fun `categories can be reordered`() = runTest {
        val a = categories.create("A")
        val b = categories.create("B")
        val c = categories.create("C")

        categories.move(c, -1)
        assertEquals(listOf(a, c, b), categories.observeCategories().first().map { it.id })
        categories.move(a, -1) // already first: no change
        assertEquals(listOf(a, c, b), categories.observeCategories().first().map { it.id })
    }

    @Test
    fun `replacing a recording deletes the old file`() = runTest {
        val family = categories.create("Rodzina")
        val id = addStoredPhoto(family)
        val first = storage.commitAudio(storage.newRecordingFile().apply { writeText("1") })
        photos.setAudio(id, first)
        val second = storage.commitAudio(storage.newRecordingFile().apply { writeText("2") })

        photos.setAudio(id, second)

        assertFalse(storage.resolve(first).exists())
        assertEquals(storage.resolve(second).absolutePath, photos.get(id)!!.audioPath)
    }

    @Test
    fun `game results report best stars per level`() = runTest {
        val results = GameResultRepositoryImpl(db.gameResultDao())
        results.save(GameResult(GameKind.MEMORY, "memory_2x2", categoryId = 1, stars = 2, moves = 3, mistakes = 1, hintsUsed = 0, durationMs = 5, finishedAt = 1))
        results.save(GameResult(GameKind.MEMORY, "memory_2x2", categoryId = 1, stars = 3, moves = 2, mistakes = 0, hintsUsed = 0, durationMs = 5, finishedAt = 2))

        assertEquals(mapOf("memory_2x2" to 3), results.observeBestStars(GameKind.MEMORY).first())
        assertEquals(emptyMap<String, Int>(), results.observeBestStars(GameKind.PUZZLE_SWAP).first())
    }
}
