package com.familygames.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.familygames.domain.model.Aspect

/** docs/02 §1 · `categories`. */
@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Logical link to photos.id, deliberately without a constraint (deleting a photo keeps the category). */
    val coverPhotoId: Long? = null,
    val sortOrder: Int,
    val isBuiltIn: Boolean = false,
    val isHidden: Boolean = false,
    val createdAt: Long,
)

/** docs/02 §1 · `photos`. Paths are relative to filesDir, or `asset://…` for built-in illustrations. */
@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId"), Index("filePath")],
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val filePath: String,
    val thumbPath: String,
    val aspect: Aspect,
    val label: String? = null,
    /** v1–v2 voice recording; unused and emptied since v3 (ADR-030: the synthesizer reads the text instead). */
    val audioPath: String? = null,
    @ColumnInfo(defaultValue = "1") val useInMemory: Boolean = true,
    @ColumnInfo(defaultValue = "1") val useInPuzzle: Boolean = true,
    val sortOrder: Int,
    val createdAt: Long,
    /** v2 recorded question; unused and emptied since v3 (ADR-030). */
    val promptAudioPath: String? = null,
    /** v2: things that can be counted in "Ile jest?" (docs/12 M17). */
    @ColumnInfo(defaultValue = "0") val countable: Boolean = false,
    /** v3: the whole question as text for the synthesizer ("Ile jest jabłek?"), ADR-030. */
    val promptText: String? = null,
)

/** v2 · "Po kolei" sequences (docs/12 M16). */
@Entity(tableName = "sequences")
data class SequenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
)

@Entity(
    tableName = "sequence_steps",
    foreignKeys = [
        ForeignKey(entity = SequenceEntity::class, parentColumns = ["id"], childColumns = ["sequenceId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PhotoEntity::class, parentColumns = ["id"], childColumns = ["photoId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sequenceId"), Index("photoId")],
)
data class SequenceStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sequenceId: Long,
    val photoId: Long,
    val position: Int,
)

/** docs/02 §1 · `game_results`. */
@Entity(tableName = "game_results", indices = [Index("finishedAt")])
data class GameResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: String,
    val levelKey: String,
    val categoryId: Long? = null,
    val photoId: Long? = null,
    val stars: Int,
    val moves: Int,
    val mistakes: Int,
    val hintsUsed: Int,
    val durationMs: Long,
    val finishedAt: Long,
)

data class LevelBest(val levelKey: String, val best: Int)
