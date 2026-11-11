package com.familygames.data.repository

import com.familygames.data.local.db.CategoryEntity
import com.familygames.data.local.db.GameResultEntity
import com.familygames.data.local.db.PhotoEntity
import com.familygames.data.local.files.MediaStorage
import com.familygames.domain.model.Category
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.GameResult
import com.familygames.domain.model.Photo

internal fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    coverPhotoId = coverPhotoId,
    sortOrder = sortOrder,
    isBuiltIn = isBuiltIn,
    isHidden = isHidden,
    createdAt = createdAt,
)

internal fun PhotoEntity.toDomain(storage: MediaStorage) = Photo(
    id = id,
    categoryId = categoryId,
    image = storage.toSource(filePath),
    thumb = storage.toSource(thumbPath),
    aspect = aspect,
    label = label,
    useInMemory = useInMemory,
    useInPuzzle = useInPuzzle,
    sortOrder = sortOrder,
    createdAt = createdAt,
    promptText = promptText,
    countable = countable,
)

internal fun GameResult.toEntity() = GameResultEntity(
    gameId = game.name,
    levelKey = levelKey,
    categoryId = categoryId,
    photoId = photoId,
    stars = stars,
    moves = moves,
    mistakes = mistakes,
    hintsUsed = hintsUsed,
    durationMs = durationMs,
    finishedAt = finishedAt,
)

internal fun GameResultEntity.toDomain() = GameResult(
    game = GameKind.valueOf(gameId),
    levelKey = levelKey,
    categoryId = categoryId,
    photoId = photoId,
    stars = stars,
    moves = moves,
    mistakes = mistakes,
    hintsUsed = hintsUsed,
    durationMs = durationMs,
    finishedAt = finishedAt,
)
