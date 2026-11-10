package com.familygames.data.repository

import androidx.room.withTransaction
import com.familygames.data.local.db.FamilyGamesDatabase
import com.familygames.data.local.db.PhotoDao
import com.familygames.data.local.db.SequenceDao
import com.familygames.data.local.db.SequenceEntity
import com.familygames.data.local.db.SequenceStepEntity
import com.familygames.data.local.files.MediaStorage
import com.familygames.domain.model.PhotoSequence
import com.familygames.domain.repository.SequenceRepository
import com.familygames.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SequenceRepositoryImpl(
    private val db: FamilyGamesDatabase,
    private val sequenceDao: SequenceDao,
    private val photoDao: PhotoDao,
    private val storage: MediaStorage,
    private val clock: Clock,
) : SequenceRepository {

    override fun observeAll(): Flow<List<PhotoSequence>> =
        combine(sequenceDao.observeAll(), sequenceDao.observeAllSteps(), photoDao.observeAll()) { sequences, steps, photos ->
            val byId = photos.associateBy { it.id }
            val stepsBySequence = steps.groupBy { it.sequenceId }
            sequences.map { s ->
                PhotoSequence(
                    id = s.id,
                    name = s.name,
                    steps = stepsBySequence[s.id].orEmpty().sortedBy { it.position }.mapNotNull { byId[it.photoId]?.toDomain(storage) },
                )
            }
        }

    override suspend fun get(id: Long): PhotoSequence? {
        val steps = sequenceDao.steps(id)
        if (steps.isEmpty()) return null
        val photos = photoDao.getByIds(steps.map { it.photoId }).associateBy { it.id }
        val name = sequenceDao.observeAllOnce().firstOrNull { it.id == id }?.name ?: return null
        return PhotoSequence(id, name, steps.mapNotNull { photos[it.photoId]?.toDomain(storage) })
    }

    override suspend fun save(id: Long?, name: String, photoIds: List<Long>): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= NAME_MAX) { "Bad name" }
        require(photoIds.size in PhotoSequence.MIN_STEPS..PhotoSequence.MAX_STEPS && photoIds.distinct().size == photoIds.size) { "Bad steps" }
        return db.withTransaction {
            val sequenceId = if (id == null) {
                sequenceDao.insert(SequenceEntity(name = trimmed, sortOrder = sequenceDao.nextSortOrder(), createdAt = clock.now()))
            } else {
                sequenceDao.rename(id, trimmed)
                sequenceDao.deleteSteps(id)
                id
            }
            sequenceDao.insertSteps(photoIds.mapIndexed { i, p -> SequenceStepEntity(sequenceId = sequenceId, photoId = p, position = i) })
            sequenceId
        }
    }

    override suspend fun delete(id: Long) = sequenceDao.delete(id)

    companion object {
        const val NAME_MAX = 24
    }
}
