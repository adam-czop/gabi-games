package com.familygames.data

import com.familygames.data.local.files.MediaStorage
import com.familygames.data.seed.DefaultContentSeeder
import com.familygames.data.updater.UpdateManager
import com.familygames.data.updater.UpdateScheduler
import com.familygames.domain.repository.GameResultRepository
import com.familygames.util.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit

/** Start-up housekeeping run once per process, off the main thread. */
class AppInitializer(
    private val seeder: DefaultContentSeeder,
    private val results: GameResultRepository,
    private val updates: UpdateManager,
    private val scheduler: UpdateScheduler,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val storage: MediaStorage,
) {
    fun start(): Job = scope.launch {
        runCatching { seeder.seedIfNeeded() }
            .onSuccess { seeded -> if (seeded) Timber.i("Built-in content seeded") }
            .onFailure { Timber.e(it, "Seeding built-in content failed") }
        // ADR-030: recordings of v1–v2 are no longer used; the database dropped them in migration 2→3.
        runCatching { storage.deleteLegacyAudio() }
            .onSuccess { removed -> if (removed) Timber.i("Legacy voice recordings removed") }
            .onFailure { Timber.e(it, "Removing legacy recordings failed") }
        // Retention: results older than a year are not needed for S02 stars or S14 (docs/02 §1).
        runCatching { results.purgeOlderThan(clock.now() - RETENTION_MS) }
            .onFailure { Timber.e(it, "Purging old results failed") }
        runCatching {
            updates.cleanCache()
            scheduler.schedule()
            updates.checkOnStartIfDue()
        }.onFailure { Timber.e(it, "Update start-up failed") }
    }

    companion object {
        val RETENTION_MS = TimeUnit.DAYS.toMillis(365)
    }
}
