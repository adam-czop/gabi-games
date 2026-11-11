package com.familygames.presentation.navigation

import com.familygames.domain.model.GameId
import com.familygames.domain.model.MemoryLevel
import kotlinx.serialization.Serializable

// Type-safe routes (docs/01 §4).

@Serializable object MenuRoute

@Serializable object MemoryLevelsRoute

@Serializable data class MemoryPlayRoute(val level: MemoryLevel, val categoryId: Long)

@Serializable object PuzzleSetupRoute

@Serializable data class SnapPuzzleRoute(val photoId: Long, val rows: Int, val cols: Int)

@Serializable data class SwapPuzzleRoute(val photoId: Long, val size: Int)

/** Nested parent graph — entered only after a successful parent gate (S08). */
@Serializable object ParentGraph

@Serializable object ParentPhotosRoute

/** Edit one photo ([photoId]) or add [pendingUris] from the picker/camera into [categoryId]. */
@Serializable data class ParentPhotoEditRoute(
    val photoId: Long? = null,
    val pendingUris: List<String> = emptyList(),
    val categoryId: Long? = null,
)

@Serializable object ParentCategoriesRoute

@Serializable object ParentProgressRoute

@Serializable object ParentSettingsRoute

@Serializable object FirstRunRoute

// docs/12 · new games

/** Generic level picker (S02 style) for the round-based games. */
@Serializable data class GameLevelsRoute(val game: GameId)

@Serializable data class WhereIsRoute(val level: String, val categoryId: Long)

@Serializable object PeekabooRoute

@Serializable object ScratchRoute

@Serializable data class SortingRoute(val level: String)

@Serializable data class OddOneOutRoute(val level: String)

@Serializable data class SoundMemoRoute(val level: MemoryLevel)

@Serializable object BubblesRoute

@Serializable object XylophoneRoute

@Serializable object SequencePickRoute

@Serializable data class SequencePlayRoute(val sequenceId: Long)

@Serializable data class CountingRoute(val level: String)

@Serializable object ColoringRoute

@Serializable data class MazeRoute(val level: String)

@Serializable data class ShadowsRoute(val level: String)

@Serializable data class MarblesRoute(val level: String)

@Serializable data class FindRoute(val level: String)

@Serializable data class MissingRoute(val level: String)

@Serializable data class HalvesRoute(val level: String)

@Serializable data class SorterRoute(val level: String)

@Serializable data class EchoRoute(val level: String)

@Serializable object GardenRoute

@Serializable object ParentGamesRoute

@Serializable object ParentSequencesRoute
