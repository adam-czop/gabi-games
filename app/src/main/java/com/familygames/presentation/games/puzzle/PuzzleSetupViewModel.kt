package com.familygames.presentation.games.puzzle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.domain.game.puzzle.PuzzleDifficulty
import com.familygames.domain.model.PhotoSource
import com.familygames.domain.repository.PhotoRepository
import com.familygames.domain.repository.SettingsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PuzzlePhotoUi(val id: Long, val thumb: PhotoSource, val label: String?)

data class PuzzleSetupUiState(
    val loading: Boolean = true,
    val photos: List<PuzzlePhotoUi> = emptyList(),
    val difficulty: PuzzleDifficulty = PuzzleDifficulty.SNAP_2X2,
)

sealed interface PuzzleSetupEvent {
    data class SelectDifficulty(val difficulty: PuzzleDifficulty) : PuzzleSetupEvent
    data class PhotoTapped(val photoId: Long) : PuzzleSetupEvent
}

data class PlayPuzzle(val photoId: Long, val difficulty: PuzzleDifficulty)

/** S05 · picture and difficulty; the difficulty choice is remembered (docs/03 S05). */
class PuzzleSetupViewModel(photos: PhotoRepository, private val settings: SettingsRepository) : ViewModel() {

    private val effects = Channel<PlayPuzzle>(Channel.BUFFERED)
    val effect: Flow<PlayPuzzle> = effects.receiveAsFlow()

    val uiState: StateFlow<PuzzleSetupUiState> = combine(photos.observeForPuzzle(), settings.settings) { list, prefs ->
        PuzzleSetupUiState(
            loading = false,
            photos = list.map { PuzzlePhotoUi(it.id, it.thumb, it.label) },
            difficulty = PuzzleDifficulty.fromKey(prefs.lastPuzzleDifficulty),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PuzzleSetupUiState())

    fun onEvent(event: PuzzleSetupEvent) {
        when (event) {
            is PuzzleSetupEvent.SelectDifficulty -> viewModelScope.launch {
                settings.update { it.copy(lastPuzzleDifficulty = event.difficulty.key) }
            }
            is PuzzleSetupEvent.PhotoTapped -> effects.trySend(PlayPuzzle(event.photoId, uiState.value.difficulty))
        }
    }
}
