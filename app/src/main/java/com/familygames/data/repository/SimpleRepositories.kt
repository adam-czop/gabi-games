package com.familygames.data.repository

import com.familygames.data.local.db.GameResultDao
import com.familygames.data.local.prefs.SettingsDataStore
import com.familygames.domain.model.AppSettings
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.GameResult
import com.familygames.domain.repository.GameResultRepository
import com.familygames.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GameResultRepositoryImpl(private val dao: GameResultDao) : GameResultRepository {

    override suspend fun save(result: GameResult) {
        dao.insert(result.toEntity())
    }

    override fun observeBestStars(game: GameKind): Flow<Map<String, Int>> =
        dao.bestStarsByLevel(game.name).map { rows -> rows.associate { it.levelKey to it.best } }

    override fun observeSince(fromEpochMs: Long): Flow<List<GameResult>> =
        dao.observeSince(fromEpochMs).map { rows -> rows.map { it.toDomain() } }

    override suspend fun purgeOlderThan(cutoffEpochMs: Long): Int = dao.deleteOlderThan(cutoffEpochMs)

    override suspend fun clear() = dao.deleteAll()
}

class SettingsRepositoryImpl(private val store: SettingsDataStore) : SettingsRepository {
    override val settings: Flow<AppSettings> = store.settings

    override suspend fun update(transform: (AppSettings) -> AppSettings) = store.update(transform)
}
