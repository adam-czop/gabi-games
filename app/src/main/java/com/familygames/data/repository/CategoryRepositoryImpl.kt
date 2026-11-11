package com.familygames.data.repository

import androidx.room.withTransaction
import com.familygames.data.local.db.CategoryDao
import com.familygames.data.local.db.CategoryEntity
import com.familygames.data.local.db.FamilyGamesDatabase
import com.familygames.data.local.db.PhotoDao
import com.familygames.data.local.files.MediaStorage
import com.familygames.domain.model.Category
import com.familygames.domain.model.CategorySummary
import com.familygames.domain.repository.CategoryRepository
import com.familygames.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class CategoryRepositoryImpl(
    private val db: FamilyGamesDatabase,
    private val categoryDao: CategoryDao,
    private val photoDao: PhotoDao,
    private val storage: MediaStorage,
    private val clock: Clock,
) : CategoryRepository {

    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    // A few hundred photos at most: combining in memory is simpler than per-category SQL and stays consistent.
    override fun observeSummaries(): Flow<List<CategorySummary>> =
        combine(categoryDao.observeAll(), photoDao.observeAll()) { categories, photos ->
            val byCategory = photos.groupBy { it.categoryId }
            categories.map { category ->
                val own = byCategory[category.id].orEmpty()
                val coverPhoto = own.firstOrNull { it.id == category.coverPhotoId }
                    ?: own.firstOrNull { it.useInMemory }
                    ?: own.firstOrNull()
                CategorySummary(
                    category = category.toDomain(),
                    photoCount = own.size,
                    memoryCount = own.count { it.useInMemory },
                    puzzleCount = own.count { it.useInPuzzle },
                    cover = coverPhoto?.let { storage.toSource(it.thumbPath) },
                )
            }
        }

    override suspend fun get(id: Long): Category? = categoryDao.get(id)?.toDomain()

    override suspend fun create(name: String): Long = db.withTransaction {
        val clean = validName(name)
        require(categoryDao.findByName(clean) == null) { "Category '$clean' already exists" }
        categoryDao.insert(
            CategoryEntity(
                name = clean,
                sortOrder = categoryDao.nextSortOrder(),
                createdAt = clock.now(),
            ),
        )
    }

    override suspend fun rename(id: Long, name: String) = db.withTransaction {
        val clean = validName(name)
        val current = categoryDao.get(id) ?: return@withTransaction
        val clash = categoryDao.findByName(clean)
        require(clash == null || clash.id == id) { "Category '$clean' already exists" }
        categoryDao.update(current.copy(name = clean))
    }

    override suspend fun setHidden(id: Long, hidden: Boolean) {
        categoryDao.get(id)?.let { categoryDao.update(it.copy(isHidden = hidden)) }
    }

    override suspend fun setCover(id: Long, photoId: Long?) {
        categoryDao.get(id)?.let { categoryDao.update(it.copy(coverPhotoId = photoId)) }
    }

    override suspend fun move(id: Long, direction: Int) = db.withTransaction {
        val ordered = categoryDao.getAll().toMutableList()
        val from = ordered.indexOfFirst { it.id == id }
        val to = from + direction.coerceIn(-1, 1)
        if (from < 0 || to !in ordered.indices || to == from) return@withTransaction
        ordered.add(to, ordered.removeAt(from))
        categoryDao.updateAll(ordered.mapIndexed { index, c -> c.copy(sortOrder = index) })
    }

    override suspend fun delete(id: Long) {
        val category = categoryDao.get(id) ?: return
        require(!category.isBuiltIn) { "Built-in categories can only be hidden" }
        val photos = photoDao.getByCategory(id)
        categoryDao.delete(id) // photos go with it (ON DELETE CASCADE); DB first, then files (docs/02 §3)
        storage.delete(*photos.flatMap { listOf(it.filePath, it.thumbPath) }.toTypedArray())
    }

    private fun validName(name: String): String {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Category name is blank" }
        require(clean.length <= Category.MAX_NAME_LENGTH) { "Category name is longer than ${Category.MAX_NAME_LENGTH}" }
        return clean
    }
}
