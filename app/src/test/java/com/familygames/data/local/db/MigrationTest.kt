package com.familygames.data.local.db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** docs/12 M10.4, ADR-030: family photos must survive every schema update. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FamilyGamesDatabase::class.java)

    @Test
    fun `v1 data survives migration to v2`() {
        helper.createDatabase(DB, 1).apply {
            execSQL("INSERT INTO categories (id, name, sortOrder, isBuiltIn, isHidden, createdAt) VALUES (1, 'Rodzina', 0, 0, 0, 0)")
            execSQL(
                "INSERT INTO photos (id, categoryId, filePath, thumbPath, aspect, label, useInMemory, useInPuzzle, sortOrder, createdAt) " +
                    "VALUES (7, 1, 'photos/a.jpg', 'photos/thumbs/a.jpg', 'SQUARE', 'Babcia', 1, 1, 0, 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)

        db.query("SELECT label, promptAudioPath, countable FROM photos WHERE id = 7").use { c ->
            c.moveToFirst()
            assertEquals("Babcia", c.getString(0))
            assertEquals(true, c.isNull(1))
            assertEquals(0, c.getInt(2))
        }
        db.execSQL("INSERT INTO sequences (id, name, sortOrder, createdAt) VALUES (1, 'Poranek', 0, 0)")
        db.execSQL("INSERT INTO sequence_steps (sequenceId, photoId, position) VALUES (1, 7, 0)")
        db.query("SELECT COUNT(*) FROM sequence_steps").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
    }

    @Test
    fun `v2 photos keep their names and lose the recordings in v3`() {
        helper.createDatabase(DB, 2).apply {
            execSQL("INSERT INTO categories (id, name, sortOrder, isBuiltIn, isHidden, createdAt) VALUES (1, 'Rodzina', 0, 0, 0, 0)")
            execSQL(
                "INSERT INTO photos (id, categoryId, filePath, thumbPath, aspect, label, audioPath, useInMemory, useInPuzzle, sortOrder, createdAt, promptAudioPath, countable) " +
                    "VALUES (7, 1, 'photos/a.jpg', 'photos/thumbs/a.jpg', 'SQUARE', 'Babcia', 'audio/a.m4a', 1, 1, 0, 0, 'audio/q.m4a', 1)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 3, true, MIGRATION_2_3)

        db.query("SELECT label, audioPath, promptAudioPath, promptText, countable FROM photos WHERE id = 7").use { c ->
            c.moveToFirst()
            assertEquals("Babcia", c.getString(0))
            assertEquals(true, c.isNull(1))
            assertEquals(true, c.isNull(2))
            assertEquals(true, c.isNull(3))
            assertEquals(1, c.getInt(4))
        }
    }

    /** Room 2.8 compares the driver's name with the resolved path, so give it the full path. */
    private val DB: String = ApplicationProvider.getApplicationContext<Context>().getDatabasePath("migration-test.db").absolutePath
}
