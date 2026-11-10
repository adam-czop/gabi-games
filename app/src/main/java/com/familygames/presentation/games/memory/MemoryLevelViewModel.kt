package com.familygames.presentation.games.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.MemoryLevel
import com.familygames.domain.model.PhotoSource
import com.familygames.domain.repository.CategoryRepository
import com.familygames.domain.repository.GameResultRepository
import com.familygames.domain.repository.SettingsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LevelStatus { Available, Selected, Locked, NotEnoughPhotos }

data class LevelTileUi(val level: MemoryLevel, val bestStars: Int, val status: LevelStatus) {
    val playable: Boolean get() = status == LevelStatus.Available || status == LevelStatus.Selected
}

data class CategoryChipUi(val id: Long, val name: String, val cover: PhotoSource?)

data class MemoryLevelUiState(
    val loading: Boolean = true,
    val levels: List<LevelTileUi> = emptyList(),
    val categories: List<CategoryChipUi> = emptyList(),
    val selectedCategoryId: Long? = null,
)

sealed interface MemoryLevelEvent {
    data class SelectCategory(val id: Long) : MemoryLevelEvent
    data class LevelTapped(val level: MemoryLevel) : MemoryLevelEvent
}

sealed interface MemoryLevelEffect {
    data class Play(val level: MemoryLevel, val categoryId: Long) : MemoryLevelEffect
    data class Refuse(val level: MemoryLevel) : MemoryLevelEffect
}

/** Minimum photos for a category to appear in S02 at all (docs/03 S02). */
private const val MIN_PHOTOS_FOR_CHIP = 2

/** S02 rules (docs/03): Locked only with level locks on and no star on the previous level; too few photos greys out. */
internal fun buildLevelTiles(
    memoryPhotoCount: Int,
    bestStars: Map<String, Int>,
    lastLevelKey: String,
    locksEnabled: Boolean,
): List<LevelTileUi> = MemoryLevel.entries.mapIndexed { index, level ->
    val previous = MemoryLevel.entries.getOrNull(index - 1)
    val status = when {
        locksEnabled && previous != null && (bestStars[previous.key] ?: 0) < 1 -> LevelStatus.Locked
        memoryPhotoCount < level.pairs -> LevelStatus.NotEnoughPhotos
        level.key == lastLevelKey -> LevelStatus.Selected
        else -> LevelStatus.Available
    }
    LevelTileUi(level, bestStars[level.key] ?: 0, status)
}

class MemoryLevelViewModel(
    categories: CategoryRepository,
    results: GameResultRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val chosenCategory = MutableStateFlow<Long?>(null)
    private val effects = Channel<MemoryLevelEffect>(Channel.BUFFERED)
    val effect: Flow<MemoryLevelEffect> = effects.receiveAsFlow()

    val uiState: StateFlow<MemoryLevelUiState> = combine(
        categories.observeSummaries(),
        results.observeBestStars(GameKind.MEMORY),
        settings.settings,
        chosenCategory,
    ) { summaries, best, prefs, chosen ->
        val visible = summaries.filter { !it.category.isHidden && it.memoryCount >= MIN_PHOTOS_FOR_CHIP }
        val selected = visible.firstOrNull { it.category.id == chosen }
            ?: visible.firstOrNull { it.category.id == prefs.lastMemoryCategory }
            ?: visible.firstOrNull()
        MemoryLevelUiState(
            loading = false,
            levels = buildLevelTiles(selected?.memoryCount ?: 0, best, prefs.lastMemoryLevel, prefs.levelLocksEnabled),
            categories = visible.map { CategoryChipUi(it.category.id, it.category.name, it.cover) },
            selectedCategoryId = selected?.category?.id,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemoryLevelUiState())

    fun onEvent(event: MemoryLevelEvent) {
        when (event) {
            is MemoryLevelEvent.SelectCategory -> {
                chosenCategory.value = event.id
                viewModelScope.launch { settings.update { it.copy(lastMemoryCategory = event.id) } }
            }
            is MemoryLevelEvent.LevelTapped -> {
                val state = uiState.value
                val tile = state.levels.firstOrNull { it.level == event.level }
                val categoryId = state.selectedCategoryId
                viewModelScope.launch {
                    if (tile?.playable == true && categoryId != null) {
                        settings.update { it.copy(lastMemoryLevel = event.level.key, lastMemoryCategory = categoryId) }
                        effects.send(MemoryLevelEffect.Play(event.level, categoryId))
                    } else {
                        effects.send(MemoryLevelEffect.Refuse(event.level))
                    }
                }
            }
        }
    }
}
