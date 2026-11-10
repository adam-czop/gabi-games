package com.familygames.presentation.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.domain.model.GameId
import com.familygames.domain.model.PhotoSequence
import com.familygames.domain.repository.SequenceRepository
import com.familygames.domain.repository.SettingsRepository
import com.familygames.presentation.games.GameEntry
import com.familygames.presentation.games.GameRegistry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainMenuUiState(
    val soundEnabled: Boolean = true,
    val games: List<GameEntry> = emptyList(),
    val loading: Boolean = true,
)

sealed interface MainMenuEvent {
    data object ToggleSound : MainMenuEvent
}

class MainMenuViewModel(private val settings: SettingsRepository, sequences: SequenceRepository) : ViewModel() {

    val uiState: StateFlow<MainMenuUiState> = combine(settings.settings, sequences.observeAll()) { prefs, seqs ->
        // The sequence game needs at least one sequence made by the parent (docs/12 M16).
        val unavailable = if (seqs.none { it.steps.size >= PhotoSequence.MIN_STEPS }) setOf(GameId.SEQUENCE) else emptySet()
        MainMenuUiState(soundEnabled = prefs.soundEnabled, games = GameRegistry.visible(prefs, unavailable), loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainMenuUiState())

    fun onEvent(event: MainMenuEvent) {
        when (event) {
            MainMenuEvent.ToggleSound -> viewModelScope.launch { settings.update { it.copy(soundEnabled = !it.soundEnabled) } }
        }
    }
}
