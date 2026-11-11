package com.familygames.presentation.parent.photos

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.data.local.files.CropFraction
import com.familygames.data.local.files.PhotoImporter
import com.familygames.domain.model.Aspect
import com.familygames.domain.model.Category
import com.familygames.domain.model.NewPhoto
import com.familygames.domain.model.PhotoSource
import com.familygames.domain.repository.CategoryRepository
import com.familygames.domain.repository.PhotoRepository
import com.familygames.presentation.common.VoicePrompts
import com.familygames.presentation.common.phrase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** S10 has two texts for the synthesizer (ADR-030): the name ("babcia") and, optionally, the whole question. */
enum class SpeakTarget { Label, Prompt }

data class PhotoEditUiState(
    val loading: Boolean = true,
    val editing: Boolean = false,
    val builtIn: Boolean = false,
    val index: Int = 0,
    val total: Int = 1,
    val image: ImageBitmap? = null,
    /** Existing photo shown without cropping (edit mode). */
    val existing: PhotoSource? = null,
    val imageVersion: Int = 0,
    val aspect: Aspect = Aspect.SQUARE,
    val label: String = "",
    val categories: List<Category> = emptyList(),
    val categoryId: Long? = null,
    val useInMemory: Boolean = true,
    val useInPuzzle: Boolean = true,
    val promptText: String = "",
    /** False when the tablet has no Polish voice: the "Posłuchaj" buttons explain it instead of staying silent. */
    val ttsAvailable: Boolean = true,
    val countable: Boolean = false,
    val saving: Boolean = false,
    val decodeFailed: Boolean = false,
)

sealed interface PhotoEditEvent {
    data class AspectChanged(val aspect: Aspect) : PhotoEditEvent
    data object Rotate : PhotoEditEvent
    data class LabelChanged(val text: String) : PhotoEditEvent
    data class CategoryChanged(val id: Long) : PhotoEditEvent
    data class UseInMemory(val enabled: Boolean) : PhotoEditEvent
    data class UseInPuzzle(val enabled: Boolean) : PhotoEditEvent
    data class PromptChanged(val text: String) : PhotoEditEvent
    data class Listen(val target: SpeakTarget) : PhotoEditEvent
    data class Countable(val enabled: Boolean) : PhotoEditEvent
    data class Save(val crop: CropFraction?) : PhotoEditEvent
    data object Skip : PhotoEditEvent
}

const val LABEL_MAX = 24
const val PROMPT_MAX = 80

/** S10 · add (crop, name, question, games) one or more new photos, or edit an existing one. */
class PhotoEditViewModel(
    private val photoId: Long?,
    private val pendingUris: List<String>,
    initialCategoryId: Long?,
    private val photos: PhotoRepository,
    private val categories: CategoryRepository,
    private val importer: PhotoImporter,
    private val prompts: VoicePrompts,
) : ViewModel() {

    private val state = MutableStateFlow(PhotoEditUiState(editing = photoId != null, total = pendingUris.size.coerceAtLeast(1), categoryId = initialCategoryId))
    val uiState: StateFlow<PhotoEditUiState> = state.asStateFlow()

    private val done = Channel<Unit>(Channel.BUFFERED)
    val finished: Flow<Unit> = done.receiveAsFlow()

    private var bitmap: Bitmap? = null

    init {
        viewModelScope.launch {
            val all = categories.observeCategories().first()
            state.update { s -> s.copy(categories = all, categoryId = s.categoryId ?: all.firstOrNull()?.id, ttsAvailable = prompts.ttsAvailable.value) }
            if (photoId != null) loadExisting(photoId) else loadPending(0)
        }
    }

    private suspend fun loadExisting(id: Long) {
        val photo = photos.get(id) ?: return done.send(Unit)
        state.update {
            it.copy(
                loading = false, existing = photo.image, builtIn = photo.isBuiltIn, aspect = photo.aspect,
                label = photo.label.orEmpty(), promptText = photo.promptText.orEmpty(), categoryId = photo.categoryId,
                useInMemory = photo.useInMemory, useInPuzzle = photo.useInPuzzle,
                countable = photo.countable,
            )
        }
    }

    private suspend fun loadPending(index: Int) {
        if (index >= pendingUris.size) return done.send(Unit)
        state.update { it.copy(loading = true, index = index, decodeFailed = false) }
        val decoded = importer.decode(pendingUris[index])
        bitmap = decoded
        state.update {
            it.copy(
                loading = false, image = decoded?.asImageBitmap(), imageVersion = it.imageVersion + 1, decodeFailed = decoded == null,
                label = "", promptText = "", countable = false,
                useInMemory = true, useInPuzzle = true,
            )
        }
    }

    fun onEvent(event: PhotoEditEvent) {
        when (event) {
            is PhotoEditEvent.AspectChanged -> state.update { it.copy(aspect = event.aspect, imageVersion = it.imageVersion + 1) }
            PhotoEditEvent.Rotate -> rotate()
            is PhotoEditEvent.LabelChanged -> state.update { it.copy(label = event.text.take(LABEL_MAX)) }
            is PhotoEditEvent.CategoryChanged -> state.update { it.copy(categoryId = event.id) }
            is PhotoEditEvent.UseInMemory -> state.update { it.copy(useInMemory = event.enabled) }
            is PhotoEditEvent.UseInPuzzle -> state.update { it.copy(useInPuzzle = event.enabled) }
            is PhotoEditEvent.PromptChanged -> state.update { it.copy(promptText = event.text.take(PROMPT_MAX)) }
            is PhotoEditEvent.Listen -> listen(event.target)
            is PhotoEditEvent.Countable -> state.update { it.copy(countable = event.enabled) }
            is PhotoEditEvent.Save -> save(event.crop)
            PhotoEditEvent.Skip -> viewModelScope.launch { loadPending(state.value.index + 1) }
        }
    }

    private fun rotate() {
        val source = bitmap ?: return
        viewModelScope.launch {
            val rotated = importer.rotate(source, 90)
            bitmap = rotated
            state.update { it.copy(image = rotated.asImageBitmap(), imageVersion = it.imageVersion + 1) }
        }
    }

    /** Reads the typed text aloud, so the parent hears how the games will say it. */
    private fun listen(target: SpeakTarget) {
        val s = state.value
        val text = if (target == SpeakTarget.Label) s.label else s.promptText
        viewModelScope.launch { prompts.play(prompts.phrase(text.trim())) }
    }

    private fun save(crop: CropFraction?) {
        val s = state.value
        val categoryId = s.categoryId ?: return
        if (s.saving) return
        viewModelScope.launch {
            state.update { it.copy(saving = true) }
            if (photoId != null) {
                photos.updateDetails(photoId, s.label, s.promptText, categoryId, s.useInMemory, s.useInPuzzle)
                photos.setCountable(photoId, s.countable)
                state.update { it.copy(saving = false) }
                done.send(Unit)
            } else {
                val source = bitmap ?: return@launch
                val stored = importer.saveCropped(source, crop ?: CropFraction(0f, 0f, 1f, 1f))
                photos.add(
                    NewPhoto(
                        categoryId, stored.filePath, stored.thumbPath, s.aspect, s.label, s.useInMemory, s.useInPuzzle,
                        promptText = s.promptText, countable = s.countable,
                    ),
                )
                state.update { it.copy(saving = false) }
                loadPending(s.index + 1)
            }
        }
    }
}
