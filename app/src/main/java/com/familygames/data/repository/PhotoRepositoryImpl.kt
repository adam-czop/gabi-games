package com.familygames.data.repository

import com.familygames.data.local.db.PhotoDao
import com.familygames.data.local.db.PhotoEntity
import com.familygames.data.local.files.MediaStorage
import com.familygames.domain.model.NewPhoto
import com.familygames.domain.model.Photo
import com.familygames.domain.repository.PhotoRepository
import com.familygames.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PhotoRepositoryImpl(
    private val photoDao: PhotoDao,
    private val storage: MediaStorage,
    private val clock: Clock,
) : PhotoRepository {

    override fun observeAll(): Flow<List<Photo>> = photoDao.observeAll().mapDomain()

    override fun observeByCategory(categoryId: Long): Flow<List<Photo>> = photoDao.observeByCategory(categoryId).mapDomain()

    override fun observeForPuzzle(): Flow<List<Photo>> = photoDao.observeForPuzzle().mapDomain()

    override fun observeMemoryCount(categoryId: Long): Flow<Int> = photoDao.memoryPhotoCount(categoryId)

    override suspend fun getForMemory(categoryId: Long): List<Photo> = photoDao.getForMemory(categoryId).map { it.toDomain(storage) }

    override suspend fun get(id: Long): Photo? = photoDao.get(id)?.toDomain(storage)

    override suspend fun add(photo: NewPhoto): Long = photoDao.insert(
        PhotoEntity(
            categoryId = photo.categoryId,
            filePath = photo.filePath,
            thumbPath = photo.thumbPath,
            aspect = photo.aspect,
            label = photo.label.clean(LABEL_MAX),
            useInMemory = photo.useInMemory,
            useInPuzzle = photo.useInPuzzle,
            sortOrder = photoDao.nextSortOrder(photo.categoryId),
            createdAt = clock.now(),
            promptText = photo.promptText.clean(PROMPT_MAX),
            countable = photo.countable,
        ),
    )

    override suspend fun updateDetails(id: Long, label: String?, promptText: String?, categoryId: Long, useInMemory: Boolean, useInPuzzle: Boolean) {
        val current = photoDao.get(id) ?: return
        photoDao.update(
            current.copy(
                label = label.clean(LABEL_MAX),
                promptText = promptText.clean(PROMPT_MAX),
                categoryId = categoryId,
                useInMemory = useInMemory,
                useInPuzzle = useInPuzzle,
            ),
        )
    }

    override suspend fun setCountable(id: Long, countable: Boolean) {
        val current = photoDao.get(id) ?: return
        photoDao.update(current.copy(countable = countable))
    }

    override suspend fun moveTo(ids: Collection<Long>, categoryId: Long) {
        if (ids.isNotEmpty()) photoDao.moveTo(ids, categoryId)
    }

    override suspend fun delete(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        val deletable = photoDao.getByIds(ids).filterNot { it.filePath.startsWith(MediaStorage.ASSET_PREFIX) }
        if (deletable.isEmpty()) return 0
        photoDao.deleteByIds(deletable.map { it.id }) // DB first, then files (docs/02 §3)
        storage.delete(*deletable.flatMap { listOf(it.filePath, it.thumbPath) }.toTypedArray())
        return deletable.size
    }

    private fun String?.clean(max: Int): String? = this?.trim()?.take(max)?.ifEmpty { null }

    private fun Flow<List<PhotoEntity>>.mapDomain(): Flow<List<Photo>> = map { list -> list.map { it.toDomain(storage) } }

    companion object {
        const val LABEL_MAX = 24
        const val PROMPT_MAX = 80
    }
}
