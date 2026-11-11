package com.familygames.domain.repository

import com.familygames.domain.model.AppSettings
import com.familygames.domain.model.Category
import com.familygames.domain.model.CategorySummary
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.GameResult
import com.familygames.domain.model.NewPhoto
import com.familygames.domain.model.Photo
import com.familygames.domain.model.PhotoSequence
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    /** All categories (hidden too) in display order. */
    fun observeCategories(): Flow<List<Category>>

    /** Categories with photo counts and cover thumbnail, in display order. */
    fun observeSummaries(): Flow<List<CategorySummary>>

    suspend fun get(id: Long): Category?

    /** @throws IllegalArgumentException for a blank, too long or duplicate name. */
    suspend fun create(name: String): Long

    suspend fun rename(id: Long, name: String)

    suspend fun setHidden(id: Long, hidden: Boolean)

    suspend fun setCover(id: Long, photoId: Long?)

    /** Moves the category one place up (-1) or down (+1). */
    suspend fun move(id: Long, direction: Int)

    /** Deletes a user category with its photos and files. Built-in categories can only be hidden. */
    suspend fun delete(id: Long)
}

interface PhotoRepository {
    fun observeAll(): Flow<List<Photo>>

    fun observeByCategory(categoryId: Long): Flow<List<Photo>>

    /** Puzzle pictures from visible categories, newest first (docs/03 S05). */
    fun observeForPuzzle(): Flow<List<Photo>>

    fun observeMemoryCount(categoryId: Long): Flow<Int>

    suspend fun getForMemory(categoryId: Long): List<Photo>

    suspend fun get(id: Long): Photo?

    suspend fun add(photo: NewPhoto): Long

    suspend fun updateDetails(id: Long, label: String?, promptText: String?, categoryId: Long, useInMemory: Boolean, useInPuzzle: Boolean)

    suspend fun moveTo(ids: Collection<Long>, categoryId: Long)

    suspend fun setCountable(id: Long, countable: Boolean)

    /** Deletes user photos with their files; built-in ones are skipped. Returns how many were deleted. */
    suspend fun delete(ids: Collection<Long>): Int
}

interface GameResultRepository {
    suspend fun save(result: GameResult)

    /** Best stars per level key for one game. */
    fun observeBestStars(game: GameKind): Flow<Map<String, Int>>

    fun observeSince(fromEpochMs: Long): Flow<List<GameResult>>

    suspend fun purgeOlderThan(cutoffEpochMs: Long): Int

    suspend fun clear()
}

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}

interface SequenceRepository {
    /** Sequences in display order; steps whose photo was deleted are dropped (CASCADE). */
    fun observeAll(): Flow<List<PhotoSequence>>

    suspend fun get(id: Long): PhotoSequence?

    /** Creates ([id] null) or replaces a sequence. @throws IllegalArgumentException for a blank name or 3–5 steps violated. */
    suspend fun save(id: Long?, name: String, photoIds: List<Long>): Long

    suspend fun delete(id: Long)
}
