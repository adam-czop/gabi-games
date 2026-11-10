package com.familygames.presentation.parent.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.domain.model.CategorySummary
import com.familygames.domain.model.MemoryLevel
import com.familygames.domain.model.Photo
import com.familygames.domain.repository.CategoryRepository
import com.familygames.domain.repository.PhotoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PhotoManagerUiState(
    val loading: Boolean = true,
    val categories: List<CategorySummary> = emptyList(),
    val selectedCategoryId: Long? = null,
    val photos: List<Photo> = emptyList(),
    val selection: Set<Long> = emptySet(),
    val newCategoryError: Boolean = false,
) {
    val selected: CategorySummary? get() = categories.firstOrNull { it.category.id == selectedCategoryId }
    val selectionMode: Boolean get() = selection.isNotEmpty()

    /** How many more Memory photos unlock the next level, and which level (null = enough for all). */
    val missingForNextLevel: Pair<Int, MemoryLevel>? get() {
        val count = selected?.memoryCount ?: 0
        val next = MemoryLevel.entries.firstOrNull { it.pairs > count } ?: return null
        return (next.pairs - count) to next
    }
}

sealed interface PhotoManagerEvent {
    data class SelectCategory(val id: Long) : PhotoManagerEvent
    data class CreateCategory(val name: String) : PhotoManagerEvent
    data class ToggleSelection(val photoId: Long) : PhotoManagerEvent
    data object ClearSelection : PhotoManagerEvent
    data object DeleteSelected : PhotoManagerEvent
    data class MoveSelected(val categoryId: Long) : PhotoManagerEvent
    data object SetSelectedAsCover : PhotoManagerEvent
}

/** S09 · photos of one category, selection mode, new category. */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoManagerViewModel(
    private val categories: CategoryRepository,
    private val photos: PhotoRepository,
) : ViewModel() {

    private val chosen = MutableStateFlow<Long?>(null)
    private val local = MutableStateFlow(PhotoManagerUiState())

    val uiState: StateFlow<PhotoManagerUiState> = combine(
        categories.observeSummaries(),
        chosen,
        local,
    ) { summaries, chosenId, l -> Triple(summaries, summaries.firstOrNull { it.category.id == chosenId } ?: summaries.firstOrNull(), l) }
        .flatMapLatest { (summaries, selected, l) ->
            val photosFlow = selected?.let { photos.observeByCategory(it.category.id) } ?: flowOf(emptyList())
            photosFlow.map { list ->
                l.copy(loading = false, categories = summaries, selectedCategoryId = selected?.category?.id, photos = list, selection = l.selection.filter { id -> list.any { it.id == id } }.toSet())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhotoManagerUiState())

    fun onEvent(event: PhotoManagerEvent) {
        when (event) {
            is PhotoManagerEvent.SelectCategory -> {
                chosen.value = event.id
                local.update { it.copy(selection = emptySet()) }
            }
            is PhotoManagerEvent.CreateCategory -> viewModelScope.launch {
                runCatching { categories.create(event.name) }
                    .onSuccess { id ->
                        chosen.value = id
                        local.update { it.copy(newCategoryError = false) }
                    }
                    .onFailure { local.update { it.copy(newCategoryError = true) } }
            }
            is PhotoManagerEvent.ToggleSelection -> local.update {
                it.copy(selection = if (event.photoId in it.selection) it.selection - event.photoId else it.selection + event.photoId)
            }
            PhotoManagerEvent.ClearSelection -> local.update { it.copy(selection = emptySet()) }
            PhotoManagerEvent.DeleteSelected -> viewModelScope.launch {
                photos.delete(uiState.value.selection)
                local.update { it.copy(selection = emptySet()) }
            }
            is PhotoManagerEvent.MoveSelected -> viewModelScope.launch {
                photos.moveTo(uiState.value.selection, event.categoryId)
                local.update { it.copy(selection = emptySet()) }
            }
            PhotoManagerEvent.SetSelectedAsCover -> viewModelScope.launch {
                val state = uiState.value
                val photoId = state.selection.singleOrNull() ?: return@launch
                state.selectedCategoryId?.let { categories.setCover(it, photoId) }
                local.update { it.copy(selection = emptySet()) }
            }
        }
    }

    fun clearNewCategoryError() = local.update { it.copy(newCategoryError = false) }
}
