package com.familygames.presentation.games.puzzle

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.domain.game.puzzle.PuzzleDifficulty
import com.familygames.domain.game.puzzle.PuzzleTuning
import com.familygames.domain.game.puzzle.SnapScoring
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
import kotlin.random.Random

data class SnapPieceUi(val id: Int, val row: Int, val col: Int, val placed: Boolean)

data class SnapUiState(
    val loading: Boolean = true,
    val missingPhoto: Boolean = false,
    val difficulty: PuzzleDifficulty = PuzzleDifficulty.SNAP_2X2,
    val image: ImageBitmap? = null,
    /** Tray order (random); placed pieces keep their entry with `placed = true`. */
    val pieces: List<SnapPieceUi> = emptyList(),
    val hintTargetId: Int? = null,
    val ghostBoost: Boolean = false,
    val hintsUsed: Int = 0,
    val wrongDrops: Int = 0,
    val won: Boolean = false,
    val stars: Int? = null,
    val round: Int = 0,
)

sealed interface SnapEvent {
    data class PiecePlaced(val id: Int) : SnapEvent
    data class PieceMissed(val id: Int) : SnapEvent
    data object Hint : SnapEvent
    data object Restart : SnapEvent
    data object Home : SnapEvent
}

sealed interface PuzzleEffect {
    data class Sound(val sfx: Sfx) : PuzzleEffect
    data object NavigateHome : PuzzleEffect
}

/**
 * S06 · Snap puzzle (docs/05 §2). The screen does the dragging and the magnet test; this ViewModel keeps
 * the rules: which pieces are placed, hints with cooldown, scoring and the saved result.
 */
class SnapPuzzleViewModel(
    private val photoId: Long,
    rows: Int,
    cols: Int,
    private val photos: PhotoRepository,
    private val images: PuzzleImageLoader,
    private val results: GameResultRepository,
    private val clock: Clock,
    private val random: Random,
) : ViewModel() {

    private val difficulty = PuzzleDifficulty.snap(rows, cols)
    private val state = MutableStateFlow(SnapUiState(difficulty = difficulty))
    val uiState: StateFlow<SnapUiState> = state.asStateFlow()

    private val effects = Channel<PuzzleEffect>(Channel.BUFFERED)
    val effect: Flow<PuzzleEffect> = effects.receiveAsFlow()

    private var startedAt = 0L
    private var hintBlockedUntil = 0L
    private var hintJob: Job? = null
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

    fun onEvent(event: SnapEvent) {
        when (event) {
            is SnapEvent.PiecePlaced -> place(event.id)
            is SnapEvent.PieceMissed -> if (!state.value.won) state.update { it.copy(wrongDrops = it.wrongDrops + 1) }
            SnapEvent.Hint -> hint()
            SnapEvent.Restart -> newRound()
            SnapEvent.Home -> effects.trySend(PuzzleEffect.NavigateHome)
        }
    }

    private fun newRound() {
        hintJob?.cancel()
        winJob?.cancel()
        val pieces = List(difficulty.pieces) { id -> SnapPieceUi(id, id / difficulty.cols, id % difficulty.cols, placed = false) }.shuffled(random)
        startedAt = clock.now()
        hintBlockedUntil = 0
        state.update {
            it.copy(
                loading = false, pieces = pieces, hintTargetId = null, ghostBoost = false,
                hintsUsed = 0, wrongDrops = 0, won = false, stars = null, round = it.round + 1,
            )
        }
    }

    private fun place(id: Int) {
        val s = state.value
        if (s.won || s.pieces.none { it.id == id && !it.placed }) return
        val pieces = s.pieces.map { if (it.id == id) it.copy(placed = true) else it }
        state.update { it.copy(pieces = pieces, hintTargetId = it.hintTargetId.takeUnless { t -> t == id }) }
        effects.trySend(PuzzleEffect.Sound(Sfx.SNAP))
        if (pieces.all { it.placed }) {
            winJob = viewModelScope.launch {
                delay(PuzzleTuning.WIN_DELAY_MS)
                finish()
            }
        }
    }

    private fun hint() {
        val s = state.value
        val now = clock.now()
        if (s.won || now < hintBlockedUntil) return
        val target = s.pieces.filterNot { it.placed }.randomOrNull(random) ?: return
        hintBlockedUntil = now + PuzzleTuning.HINT_COOLDOWN_MS
        state.update { it.copy(hintTargetId = target.id, ghostBoost = true, hintsUsed = it.hintsUsed + 1) }
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            delay(PuzzleTuning.HINT_DURATION_MS)
            state.update { it.copy(hintTargetId = null, ghostBoost = false) }
        }
    }

    private suspend fun finish() {
        val s = state.value
        val stars = SnapScoring.stars(s.hintsUsed)
        results.save(
            GameResult(
                game = GameKind.PUZZLE_SNAP,
                levelKey = difficulty.key,
                photoId = photoId,
                stars = stars,
                moves = difficulty.pieces + s.wrongDrops,
                mistakes = s.wrongDrops,
                hintsUsed = s.hintsUsed,
                durationMs = clock.now() - startedAt,
                finishedAt = clock.now(),
            ),
        )
        state.update { it.copy(won = true, stars = stars, hintTargetId = null, ghostBoost = false) }
        effects.send(PuzzleEffect.Sound(Sfx.WIN))
    }
}
