package com.familygames.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): CategoryEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Update
    suspend fun updateAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY categoryId, sortOrder, id")
    fun observeAll(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE categoryId = :categoryId ORDER BY sortOrder, id")
    fun observeByCategory(categoryId: Long): Flow<List<PhotoEntity>>

    @Query(
        """
        SELECT p.* FROM photos p JOIN categories c ON c.id = p.categoryId
        WHERE p.useInPuzzle = 1 AND c.isHidden = 0
        ORDER BY p.createdAt DESC, p.id DESC
        """,
    )
    fun observeForPuzzle(): Flow<List<PhotoEntity>>

    @Query("SELECT COUNT(*) FROM photos WHERE categoryId = :categoryId AND useInMemory = 1")
    fun memoryPhotoCount(categoryId: Long): Flow<Int>

    @Query("SELECT * FROM photos WHERE categoryId = :categoryId AND useInMemory = 1 ORDER BY sortOrder, id")
    suspend fun getForMemory(categoryId: Long): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE categoryId = :categoryId")
    suspend fun getByCategory(categoryId: Long): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun get(id: Long): PhotoEntity?

    @Query("SELECT * FROM photos WHERE id IN (:ids)")
    suspend fun getByIds(ids: Collection<Long>): List<PhotoEntity>

    @Query("SELECT categoryId FROM photos WHERE filePath = :filePath LIMIT 1")
    suspend fun findCategoryIdByPath(filePath: String): Long?

    @Query("SELECT EXISTS(SELECT 1 FROM photos WHERE filePath = :filePath)")
    suspend fun existsByPath(filePath: String): Boolean

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM photos WHERE categoryId = :categoryId")
    suspend fun nextSortOrder(categoryId: Long): Int

    @Query("UPDATE photos SET countable = 1 WHERE filePath IN (:paths)")
    suspend fun markCountable(paths: Collection<String>)

    @Query("SELECT COUNT(*) FROM photos")
    suspend fun count(): Int

    @Insert
    suspend fun insert(photo: PhotoEntity): Long

    @Update
    suspend fun update(photo: PhotoEntity)

    @Query("UPDATE photos SET categoryId = :categoryId WHERE id IN (:ids)")
    suspend fun moveTo(ids: Collection<Long>, categoryId: Long)

    @Query("DELETE FROM photos WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: Collection<Long>)
}

@Dao
interface GameResultDao {
    @Insert
    suspend fun insert(result: GameResultEntity): Long

    @Query("SELECT levelKey, MAX(stars) AS best FROM game_results WHERE gameId = :gameId GROUP BY levelKey")
    fun bestStarsByLevel(gameId: String): Flow<List<LevelBest>>

    @Query("SELECT * FROM game_results WHERE finishedAt >= :from ORDER BY finishedAt DESC")
    fun observeSince(from: Long): Flow<List<GameResultEntity>>

    @Query("DELETE FROM game_results WHERE finishedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM game_results")
    suspend fun deleteAll()
}

@Dao
interface SequenceDao {
    @Query("SELECT * FROM sequences ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<SequenceEntity>>

    @Query("SELECT * FROM sequences ORDER BY sortOrder, id")
    suspend fun observeAllOnce(): List<SequenceEntity>

    @Query("SELECT * FROM sequence_steps WHERE sequenceId = :sequenceId ORDER BY position")
    suspend fun steps(sequenceId: Long): List<SequenceStepEntity>

    @Query("SELECT * FROM sequence_steps ORDER BY sequenceId, position")
    fun observeAllSteps(): Flow<List<SequenceStepEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM sequences")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(sequence: SequenceEntity): Long

    @Insert
    suspend fun insertSteps(steps: List<SequenceStepEntity>)

    @Query("DELETE FROM sequence_steps WHERE sequenceId = :sequenceId")
    suspend fun deleteSteps(sequenceId: Long)

    @Query("UPDATE sequences SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM sequences WHERE id = :id")
    suspend fun delete(id: Long)
}
