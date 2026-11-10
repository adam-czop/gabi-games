package com.familygames.data.seed

import com.familygames.data.FakeClock
import com.familygames.data.inMemoryDb
import com.familygames.data.local.db.FamilyGamesDatabase
import com.familygames.data.local.prefs.SettingsDataStore
import com.familygames.data.testContext
import com.familygames.data.testSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DefaultContentSeederTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: FamilyGamesDatabase
    private lateinit var settings: SettingsDataStore
    private lateinit var seeder: DefaultContentSeeder
    private val storeScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

    @Before
    fun setUp() {
        db = inMemoryDb()
        settings = testSettingsStore(tmp.root.resolve("settings.preferences_pb"), storeScope)
        seeder = DefaultContentSeeder(testContext.resources, db, db.categoryDao(), db.photoDao(), settings, FakeClock())
    }

    @After
    fun tearDown() {
        db.close()
        storeScope.cancel()
    }

    @Test
    fun `first start creates 4 built-in categories with 8 illustrations each`() = runTest {
        assertTrue(seeder.seedIfNeeded())

        val categories = db.categoryDao().getAll()
        assertEquals(listOf("Nasz dom", "Zwierzęta", "Zabawki", "Pojazdy"), categories.map { it.name })
        assertTrue(categories.all { it.isBuiltIn && !it.isHidden })
        val photos = db.photoDao().observeAll().first()
        assertEquals(32, photos.size)
        categories.forEach { c -> assertEquals(8, photos.count { it.categoryId == c.id }) }
        assertEquals("Kanapa", photos.first { it.filePath.endsWith("dom_kanapa.webp") }.label)
        assertEquals(BuiltInContent.VERSION, settings.seedVersion())
    }

    @Test
    fun `seeding is idempotent`() = runTest {
        seeder.seedIfNeeded()
        assertFalse(seeder.seedIfNeeded())
        seeder.seed() // forced re-run, as after a seed version bump

        assertEquals(4, db.categoryDao().getAll().size)
        assertEquals(32, db.photoDao().count())
    }

    @Test
    fun `renamed built-in category is found again by its illustrations`() = runTest {
        seeder.seed()
        val home = db.categoryDao().getAll().first()
        db.categoryDao().update(home.copy(name = "Dom babci"))

        seeder.seed()

        assertEquals(4, db.categoryDao().getAll().size)
        assertEquals(32, db.photoDao().count())
    }

    @Test
    fun `every built-in illustration and thumbnail ships in assets`() {
        val assets = testContext.assets
        BuiltInContent.categories.flatMap { it.photos }.forEach { photo ->
            listOf(photo.filePath, photo.thumbPath).forEach { path ->
                assets.open(path.removePrefix("asset://")).use { assertTrue(it.read() >= 0) }
            }
        }
    }
}
