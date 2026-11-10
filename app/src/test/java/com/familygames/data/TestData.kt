package com.familygames.data

import android.content.Context
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import okio.FileSystem
import androidx.room.Room
import com.familygames.data.local.prefs.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toOkioPath
import java.io.File
import androidx.test.core.app.ApplicationProvider
import com.familygames.data.local.db.CategoryEntity
import com.familygames.data.local.db.FamilyGamesDatabase
import com.familygames.data.local.db.PhotoEntity
import com.familygames.domain.model.Aspect
import com.familygames.util.Clock

val testContext: Context get() = ApplicationProvider.getApplicationContext()

/**
 * DataStore backed by Okio storage: its atomic move also replaces files on Windows, where the default
 * File storage cannot rename over an existing file (works on Android/Linux, fails on a Windows dev box).
 */
fun testSettingsStore(file: File, scope: CoroutineScope): SettingsDataStore =
    SettingsDataStore(
        PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.toOkioPath() },
            scope = scope,
        ),
    )

fun inMemoryDb(): FamilyGamesDatabase =
    Room.inMemoryDatabaseBuilder(testContext, FamilyGamesDatabase::class.java)
        .allowMainThreadQueries()
        .build()

class FakeClock(var time: Long = 1_700_000_000_000L) : Clock {
    override fun now(): Long = time
}

fun category(name: String, sortOrder: Int = 0, builtIn: Boolean = false, hidden: Boolean = false) =
    CategoryEntity(name = name, sortOrder = sortOrder, isBuiltIn = builtIn, isHidden = hidden, createdAt = 0)

fun photo(
    categoryId: Long,
    path: String,
    useInMemory: Boolean = true,
    useInPuzzle: Boolean = true,
    createdAt: Long = 0,
    audio: String? = null,
) = PhotoEntity(
    categoryId = categoryId,
    filePath = path,
    thumbPath = path.replace("photos/", "photos/thumbs/"),
    aspect = Aspect.SQUARE,
    audioPath = audio,
    useInMemory = useInMemory,
    useInPuzzle = useInPuzzle,
    sortOrder = 0,
    createdAt = createdAt,
)
