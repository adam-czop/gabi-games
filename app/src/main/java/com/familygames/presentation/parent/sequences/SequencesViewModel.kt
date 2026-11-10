package com.familygames.presentation.parent.sequences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.domain.model.Photo
import com.familygames.domain.model.PhotoSequence
import com.familygames.domain.repository.PhotoRepository
import com.familygames.domain.repository.SequenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SequenceEditor(val id: Long? = null, val name: String = "", val selected: List<Long> = emptyList()) {
    val canSave: Boolean get() = name.isNotBlank() && selected.size in PhotoSequence.MIN_STEPS..PhotoSequence.MAX_STEPS
}

data class SequencesUiState(
    val loading: Boolean = true,
    val sequences: List<PhotoSequence> = emptyList(),
    val photos: List<Photo> = emptyList(),
    val editor: SequenceEditor? = null,
)

sealed interface SequencesEvent {
    data object New : SequencesEvent
    data class Edit(val sequence: PhotoSequence) : SequencesEvent
    data class Delete(val id: Long) : SequencesEvent
    data class Name(val text: String) : SequencesEvent
    data class TogglePhoto(val id: Long) : SequencesEvent
    data class Move(val index: Int, val by: Int) : SequencesEvent
    data object Save : SequencesEvent
    data object Cancel : SequencesEvent
}

/** S15 · sequences for "Po kolei" (docs/12 M16). */
class SequencesViewModel(private val sequences: SequenceRepository, photos: PhotoRepository) : ViewModel() {
    private val editor = MutableStateFlow<SequenceEditor?>(null)

    val uiState: StateFlow<SequencesUiState> = combine(sequences.observeAll(), photos.observeAll(), editor) { list, all, ed ->
        SequencesUiState(loading = false, sequences = list, photos = all, editor = ed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SequencesUiState())

    fun onEvent(event: SequencesEvent) {
        when (event) {
            SequencesEvent.New -> editor.value = SequenceEditor()
            is SequencesEvent.Edit -> editor.value = SequenceEditor(event.sequence.id, event.sequence.name, event.sequence.steps.map { it.id })
            is SequencesEvent.Delete -> viewModelScope.launch { sequences.delete(event.id) }
            is SequencesEvent.Name -> editor.update { it?.copy(name = event.text.take(NAME_MAX)) }
            is SequencesEvent.TogglePhoto -> editor.update { e ->
                e ?: return@update null
                when {
                    event.id in e.selected -> e.copy(selected = e.selected - event.id)
                    e.selected.size < PhotoSequence.MAX_STEPS -> e.copy(selected = e.selected + event.id)
                    else -> e
                }
            }
            is SequencesEvent.Move -> editor.update { e ->
                e ?: return@update null
                val target = event.index + event.by
                if (event.index !in e.selected.indices || target !in e.selected.indices) return@update e
                e.copy(selected = e.selected.toMutableList().apply { add(target, removeAt(event.index)) })
            }
            SequencesEvent.Save -> {
                val e = editor.value ?: return
                if (!e.canSave) return
                viewModelScope.launch {
                    sequences.save(e.id, e.name, e.selected)
                    editor.value = null
                }
            }
            SequencesEvent.Cancel -> editor.value = null
        }
    }

    private companion object {
        const val NAME_MAX = 24
    }
}
