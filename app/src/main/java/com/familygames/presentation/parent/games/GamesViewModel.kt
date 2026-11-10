package com.familygames.presentation.parent.games

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.R
import com.familygames.data.local.files.MediaStorage
import com.familygames.data.local.files.VoiceRecorder
import com.familygames.domain.model.AgeBand
import com.familygames.domain.model.GameId
import com.familygames.domain.model.GameVisibility
import com.familygames.domain.model.PhotoSequence
import com.familygames.domain.repository.SequenceRepository
import com.familygames.domain.repository.SettingsRepository
import com.familygames.domain.voice.PromptPrefix
import com.familygames.presentation.common.VoicePlayer
import com.familygames.presentation.common.VoicePrompts
import com.familygames.presentation.games.GameEntry
import com.familygames.presentation.games.GameRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GameRowUi(val entry: GameEntry, val on: Boolean, val needsSequence: Boolean)

data class PrefixUi(val prefix: PromptPrefix, @param:StringRes val label: Int, val recorded: Boolean, val recording: Boolean)

data class GamesUiState(
    val ageBand: AgeBand = AgeBand.PRESCHOOL,
    val games: List<GameRowUi> = emptyList(),
    val ttsAvailable: Boolean = false,
    val prefixes: List<PrefixUi> = emptyList(),
)

sealed interface GamesEvent {
    data class SetAge(val band: AgeBand) : GamesEvent
    data class Toggle(val id: GameId, val on: Boolean) : GamesEvent
    data class RecordPrefix(val prefix: PromptPrefix) : GamesEvent
    data class PlayPrefix(val prefix: PromptPrefix) : GamesEvent
    data class DeletePrefix(val prefix: PromptPrefix) : GamesEvent
}

/** Prefixes the games use, with their S17 labels (docs/12 M10.3). */
private val RECORDABLE = listOf(
    PromptPrefix.WHERE_IS to R.string.prefix_where_is_label,
    PromptPrefix.HOW_MANY to R.string.prefix_how_many_label,
    PromptPrefix.ODD_ONE to R.string.prefix_odd_one_label,
    PromptPrefix.PEEKABOO to R.string.prefix_peekaboo_label,
)

/** S17 · which games the child sees, the age band and the recorded question starters. */
class GamesViewModel(
    private val settings: SettingsRepository,
    sequences: SequenceRepository,
    private val prompts: VoicePrompts,
    private val recorder: VoiceRecorder,
    private val storage: MediaStorage,
    private val voice: VoicePlayer,
) : ViewModel() {

    private val recordingPrefix = MutableStateFlow<PromptPrefix?>(null)
    private var recordJob: Job? = null

    val uiState: StateFlow<GamesUiState> = combine(settings.settings, sequences.observeAll(), prompts.ttsAvailable, recordingPrefix) { prefs, seqs, tts, recording ->
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
            prefixes = RECORDABLE.map { (p, label) -> PrefixUi(p, label, prefs.prefixClips.containsKey(p.key), recording == p) },
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
            is GamesEvent.RecordPrefix -> toggleRecording(event.prefix)
            is GamesEvent.PlayPrefix -> viewModelScope.launch { prompts.prefixClip(event.prefix)?.let { voice.playAndAwait(it) } }
            is GamesEvent.DeletePrefix -> viewModelScope.launch { replacePrefix(event.prefix, null) }
        }
    }

    private fun toggleRecording(prefix: PromptPrefix) {
        val current = recordingPrefix.value
        if (current != null) {
            recordJob?.cancel()
            val file = recorder.stop()
            recordingPrefix.value = null
            if (file != null) viewModelScope.launch { replacePrefix(current, storage.commitAudio(file)) }
            if (current == prefix) return
        }
        if (!recorder.start()) return
        recordingPrefix.value = prefix
        recordJob = viewModelScope.launch {
            delay(MAX_RECORDING_MS)
            if (recordingPrefix.value == prefix) toggleRecording(prefix)
        }
    }

    private suspend fun replacePrefix(prefix: PromptPrefix, relativePath: String?) {
        val old = settings.settings.first().prefixClips[prefix.key]
        settings.update { s ->
            s.copy(prefixClips = if (relativePath == null) s.prefixClips - prefix.key else s.prefixClips + (prefix.key to relativePath))
        }
        if (old != null && old != relativePath) storage.delete(old)
    }

    override fun onCleared() {
        if (recordingPrefix.value != null) recorder.cancel()
    }

    private companion object {
        const val MAX_RECORDING_MS = 5_000L
    }
}
