package com.familygames.presentation.parent.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.domain.model.AgeBand
import com.familygames.domain.model.GameId
import com.familygames.domain.model.GameVisibility
import com.familygames.domain.model.PhotoSequence
import com.familygames.domain.repository.SequenceRepository
import com.familygames.domain.repository.SettingsRepository
import com.familygames.presentation.common.VoicePrompts
import com.familygames.presentation.common.phrase
import com.familygames.presentation.games.GameEntry
import com.familygames.presentation.games.GameRegistry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GameRowUi(val entry: GameEntry, val on: Boolean, val needsSequence: Boolean)

data class GamesUiState(
    val ageBand: AgeBand = AgeBand.PRESCHOOL,
    val games: List<GameRowUi> = emptyList(),
    val ttsAvailable: Boolean = false,
)

sealed interface GamesEvent {
    data class SetAge(val band: AgeBand) : GamesEvent
    data class Toggle(val id: GameId, val on: Boolean) : GamesEvent
    /** Says a sample sentence, so the parent hears the synthesizer (ADR-030). */
    data class Listen(val text: String) : GamesEvent
}

/** S17 · which games the child sees, the age band and the state of the synthesizer that voices the games. */
class GamesViewModel(
    private val settings: SettingsRepository,
    sequences: SequenceRepository,
    private val prompts: VoicePrompts,
) : ViewModel() {

    val uiState: StateFlow<GamesUiState> = combine(settings.settings, sequences.observeAll(), prompts.ttsAvailable) { prefs, seqs, tts ->
        val noSequence = seqs.none { it.steps.size >= PhotoSequence.MIN_STEPS }
        GamesUiState(
            ageBand = prefs.childAgeBand,
            games = GameRegistry.games.map { g ->
                GameRowUi(
                    entry = g,
                    on = GameVisibility.isVisible(g.id, g.age, prefs.childAgeBand, prefs.hiddenGames, prefs.forcedGames),
                    needsSequence = g.id == GameId.SEQUENCE && noSequence,
                )
            },
            ttsAvailable = tts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GamesUiState())

    fun onEvent(event: GamesEvent) {
        when (event) {
            is GamesEvent.SetAge -> viewModelScope.launch { settings.update { it.copy(childAgeBand = event.band) } }
            is GamesEvent.Toggle -> viewModelScope.launch {
                val entry = GameRegistry.entry(event.id)
                settings.update { s ->
                    val (hidden, forced) = GameVisibility.toggle(event.id, event.on, entry.age, s.childAgeBand, s.hiddenGames, s.forcedGames)
                    s.copy(hiddenGames = hidden, forcedGames = forced)
                }
            }
            is GamesEvent.Listen -> viewModelScope.launch { prompts.play(prompts.phrase(event.text)) }
        }
    }
}
