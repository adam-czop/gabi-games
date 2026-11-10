package com.familygames.presentation.games.puzzle

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.domain.game.puzzle.PuzzleDifficulty
import com.familygames.domain.game.puzzle.PuzzleTuning
import com.familygames.domain.game.puzzle.ShuffleSwapBoardUseCase
import com.familygames.domain.game.puzzle.SwapBoard
import com.familygames.domain.game.puzzle.SwapScoring
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.GameResult
import com.familygames.domain.repository.GameResultRepository
import com.familygames.domain.repository.PhotoRepository
import com.familygames.presentation.common.Sfx
import com.familygames.util.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SwapUiState(
    val loading: Boolean = true,
    val missingPhoto: Boolean = false,
    val difficulty: PuzzleDifficulty = PuzzleDifficulty.SWAP_2X2,
    val image: ImageBitmap? = null,
    val board: SwapBoard? = null,
    val selected: Int? = null,
    val moves: Int = 0,
    /** Gaps closing after the last swap, just before the win overlay (docs/05 §3). */
    val merged: Boolean = false,
    val won: Boolean = false,
    val stars: Int? = null,
    val round: Int = 0,
)

sealed interface SwapEvent {
    data class TileTapped(val position: Int) : SwapEvent
    data object Restart : SwapEvent
    data object Home : SwapEvent
}

/** Delay of the merge animation before the win overlay. */
private const val MERGE_MS = 300L

/** S07 · Swap puzzle (docs/05 §3): tap a tile, tap a neighbour to swap them. */
class SwapPuzzleViewModel(
    private val photoId: Long,
    size: Int,
    private val photos: PhotoRepository,
    private val images: PuzzleImageLoader,
    private val results: GameResultRepository,
    private val shuffle: ShuffleSwapBoardUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val difficulty = PuzzleDifficulty.swap(size)
    private val state = MutableStateFlow(SwapUiState(difficulty = difficulty))
    val uiState: StateFlow<SwapUiState> = state.asStateFlow()

    private val effects = Channel<PuzzleEffect>(Channel.BUFFERED)
    val effect: Flow<PuzzleEffect> = effects.receiveAsFlow()

    private var startedAt = 0L
    private var misplacedAtStart = 0
    private var winJob: Job? = null

    init {
        viewModelScope.launch {
            val photo = photos.get(photoId)
            val image = photo?.let { images.load(it.image) }
            if (image == null) {
                state.update { it.copy(loading = false, missingPhoto = true) }
            } else {
                state.update { it.copy(image = image) }
                newRound()
            }
        }
    }

    fun onEvent(event: SwapEvent) {
        when (event) {
            is SwapEvent.TileTapped -> tap(event.position)
            SwapEvent.Restart -> newRound()
            SwapEvent.Home -> effects.trySend(PuzzleEffect.NavigateHome)
        }
    }

    private fun newRound() {
        winJob?.cancel()
        val board = shuffle(difficulty.rows)
        misplacedAtStart = board.tiles.size - board.correctCount()
        startedAt = clock.now()
        state.update {
            it.copy(loading = false, board = board, selected = null, moves = 0, merged = false, won = false, stars = null, round = it.round + 1)
        }
    }

    private fun tap(position: Int) {
        val s = state.value
        val board = s.board ?: return
        if (s.won || s.merged || board.isSolved()) return
        val selected = s.selected
        when {
            selected == null -> {
                state.update { it.copy(selected = position) }
                effects.trySend(PuzzleEffect.Sound(Sfx.TAP))
            }
            selected == position -> state.update { it.copy(selected = null) }
            board.areAdjacent(selected, position) -> {
                val next = board.swap(selected, position)
                state.update { it.copy(board = next, selected = null, moves = it.moves + 1) }
                val landed = listOf(selected, position).count { next.isCorrect(it) && !board.isCorrect(it) }
                effects.trySend(PuzzleEffect.Sound(if (landed > 0) Sfx.MATCH else Sfx.FLIP))
                if (next.isSolved()) {
                    winJob = viewModelScope.launch {
                        delay(PuzzleTuning.WIN_DELAY_MS)
                        state.update { it.copy(merged = true) }
                        delay(MERGE_MS)
                        finish()
                    }
                }
            }
            else -> {
                state.update { it.copy(selected = position) }
                effects.trySend(PuzzleEffect.Sound(Sfx.TAP))
            }
        }
    }

    private suspend fun finish() {
        val moves = state.value.moves
        val stars = SwapScoring.stars(moves, misplacedAtStart)
        results.save(
            GameResult(
                game = GameKind.PUZZLE_SWAP,
                levelKey = difficulty.key,
                photoId = photoId,
                stars = stars,
                moves = moves,
                mistakes = 0,
                hintsUsed = 0,
                durationMs = clock.now() - startedAt,
                finishedAt = clock.now(),
            ),
        )
        state.update { it.copy(won = true, stars = stars) }
        effects.send(PuzzleEffect.Sound(Sfx.WIN))
    }
}
