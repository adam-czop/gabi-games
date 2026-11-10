package com.familygames.presentation.parent.photos

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.data.local.files.CropFraction
import com.familygames.data.local.files.MediaStorage
import com.familygames.data.local.files.PhotoImporter
import com.familygames.data.local.files.VoiceRecorder
import com.familygames.domain.model.Aspect
import com.familygames.domain.model.Category
import com.familygames.domain.model.NewPhoto
import com.familygames.domain.model.PhotoSource
import com.familygames.domain.repository.CategoryRepository
import com.familygames.domain.repository.PhotoRepository
import com.familygames.presentation.common.VoicePlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface RecordingState {
    data object None : RecordingState
    data class Recording(val elapsedMs: Long, val levels: List<Float>) : RecordingState
    /** [path] absolute; [isNew] = not yet stored (a temp file to commit on save). */
    data class Ready(val path: String, val durationMs: Long, val isNew: Boolean) : RecordingState
}

/** S10 has two recordings: the label ("babcia") and, optionally, the whole question (docs/12 M10.4). */
enum class RecordTarget { Label, Prompt }

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
    val recording: RecordingState = RecordingState.None,
    val promptRecording: RecordingState = RecordingState.None,
    val countable: Boolean = false,
    val saving: Boolean = false,
    val decodeFailed: Boolean = false,
) {
    fun recordingOf(target: RecordTarget): RecordingState = if (target == RecordTarget.Label) recording else promptRecording

    val anyRecording: Boolean get() = recording is RecordingState.Recording || promptRecording is RecordingState.Recording
}

sealed interface PhotoEditEvent {
    data class AspectChanged(val aspect: Aspect) : PhotoEditEvent
    data object Rotate : PhotoEditEvent
    data class LabelChanged(val text: String) : PhotoEditEvent
    data class CategoryChanged(val id: Long) : PhotoEditEvent
    data class UseInMemory(val enabled: Boolean) : PhotoEditEvent
    data class UseInPuzzle(val enabled: Boolean) : PhotoEditEvent
    data class RecordToggle(val target: RecordTarget = RecordTarget.Label) : PhotoEditEvent
    data class Play(val target: RecordTarget = RecordTarget.Label) : PhotoEditEvent
    data class DeleteRecording(val target: RecordTarget = RecordTarget.Label) : PhotoEditEvent
    data class Countable(val enabled: Boolean) : PhotoEditEvent
    data class Save(val crop: CropFraction?) : PhotoEditEvent
    data object Skip : PhotoEditEvent
}

const val LABEL_MAX = 24
private const val MAX_RECORDING_MS = 8_000L
private const val LEVEL_SAMPLE_MS = 100L

/** S10 · add (crop, label, voice, games) one or more new photos, or edit an existing one. */
class PhotoEditViewModel(
    private val photoId: Long?,
    private val pendingUris: List<String>,
    initialCategoryId: Long?,
    private val photos: PhotoRepository,
    private val categories: CategoryRepository,
    private val importer: PhotoImporter,
    private val storage: MediaStorage,
    private val recorder: VoiceRecorder,
    private val voice: VoicePlayer,
) : ViewModel() {

    private val state = MutableStateFlow(PhotoEditUiState(editing = photoId != null, total = pendingUris.size.coerceAtLeast(1), categoryId = initialCategoryId))
    val uiState: StateFlow<PhotoEditUiState> = state.asStateFlow()

    private val done = Channel<Unit>(Channel.BUFFERED)
    val finished: Flow<Unit> = done.receiveAsFlow()

    private var bitmap: Bitmap? = null
    private var recordJob: Job? = null
    private var originalAudio: String? = null
    private var originalPrompt: String? = null

    init {
        viewModelScope.launch {
            val all = categories.observeCategories().first()
            state.update { s -> s.copy(categories = all, categoryId = s.categoryId ?: all.firstOrNull()?.id) }
            if (photoId != null) loadExisting(photoId) else loadPending(0)
        }
    }

    private suspend fun loadExisting(id: Long) {
        val photo = photos.get(id) ?: return done.send(Unit)
        originalAudio = photo.audioPath
        originalPrompt = photo.promptAudioPath
        state.update {
            it.copy(
                loading = false, existing = photo.image, builtIn = photo.isBuiltIn, aspect = photo.aspect,
                label = photo.label.orEmpty(), categoryId = photo.categoryId,
                useInMemory = photo.useInMemory, useInPuzzle = photo.useInPuzzle,
                recording = photo.audioPath?.let { RecordingState.Ready(it, 0, isNew = false) } ?: RecordingState.None,
                promptRecording = photo.promptAudioPath?.let { RecordingState.Ready(it, 0, isNew = false) } ?: RecordingState.None,
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
                label = "", recording = RecordingState.None, promptRecording = RecordingState.None, countable = false,
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
            is PhotoEditEvent.RecordToggle -> toggleRecording(event.target)
            is PhotoEditEvent.Play -> (state.value.recordingOf(event.target) as? RecordingState.Ready)?.let { r -> viewModelScope.launch { voice.playAndAwait(r.path) } }
            is PhotoEditEvent.DeleteRecording -> deleteRecording(event.target)
            is PhotoEditEvent.Countable -> state.update { it.copy(countable = event.enabled) }
            is PhotoEditEvent.Save -> save(event.crop)
            PhotoEditEvent.Skip -> viewModelScope.launch { discardTempRecordings(); loadPending(state.value.index + 1) }
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

    private fun PhotoEditUiState.with(target: RecordTarget, value: RecordingState) =
        if (target == RecordTarget.Label) copy(recording = value) else copy(promptRecording = value)

    private fun toggleRecording(target: RecordTarget) {
        if (state.value.recordingOf(target) is RecordingState.Recording) return stopRecording(target)
        // One microphone: finish the other recording first.
        RecordTarget.entries.filter { it != target && state.value.recordingOf(it) is RecordingState.Recording }.forEach { stopRecording(it) }
        discardTempRecording(target)
        if (!recorder.start()) return
        val started = System.currentTimeMillis()
        state.update { it.with(target, RecordingState.Recording(0, emptyList())) }
        recordJob = viewModelScope.launch {
            while (true) {
                delay(LEVEL_SAMPLE_MS)
                val elapsed = System.currentTimeMillis() - started
                val level = (recorder.amplitude() / 32767f).coerceIn(0.05f, 1f)
                state.update { s ->
                    val r = s.recordingOf(target) as? RecordingState.Recording ?: return@update s
                    s.with(target, r.copy(elapsedMs = elapsed, levels = (r.levels + level).takeLast(40)))
                }
                if (elapsed >= MAX_RECORDING_MS) {
                    stopRecording(target)
                    break
                }
            }
        }
    }

    private fun stopRecording(target: RecordTarget) {
        recordJob?.cancel()
        val elapsed = (state.value.recordingOf(target) as? RecordingState.Recording)?.elapsedMs ?: 0
        val file: File? = recorder.stop()
        state.update { it.with(target, file?.let { f -> RecordingState.Ready(f.absolutePath, elapsed, isNew = true) } ?: RecordingState.None) }
    }

    private fun deleteRecording(target: RecordTarget) {
        discardTempRecording(target)
        state.update { it.with(target, RecordingState.None) }
    }

    private fun discardTempRecording(target: RecordTarget) {
        val current = state.value.recordingOf(target)
        if (current is RecordingState.Recording) {
            recordJob?.cancel()
            recorder.cancel()
        }
        (current as? RecordingState.Ready)?.takeIf { it.isNew }?.let { File(it.path).delete() }
    }

    private fun discardTempRecordings() = RecordTarget.entries.forEach { discardTempRecording(it) }

    private fun save(crop: CropFraction?) {
        val s = state.value
        val categoryId = s.categoryId ?: return
        if (s.saving || s.anyRecording) return
        viewModelScope.launch {
            state.update { it.copy(saving = true) }
            val audio = s.recording as? RecordingState.Ready
            val audioRel = if (audio?.isNew == true) storage.commitAudio(File(audio.path)) else null
            val prompt = s.promptRecording as? RecordingState.Ready
            val promptRel = if (prompt?.isNew == true) storage.commitAudio(File(prompt.path)) else null
            if (photoId != null) {
                photos.updateDetails(photoId, s.label, categoryId, s.useInMemory, s.useInPuzzle)
                photos.setCountable(photoId, s.countable)
                when {
                    audioRel != null -> photos.setAudio(photoId, audioRel)
                    audio == null && originalAudio != null -> photos.setAudio(photoId, null)
                }
                when {
                    promptRel != null -> photos.setPromptAudio(photoId, promptRel)
                    prompt == null && originalPrompt != null -> photos.setPromptAudio(photoId, null)
                }
                state.update { it.copy(saving = false) }
                done.send(Unit)
            } else {
                val source = bitmap ?: return@launch
                val stored = importer.saveCropped(source, crop ?: CropFraction(0f, 0f, 1f, 1f))
                photos.add(
                    NewPhoto(
                        categoryId, stored.filePath, stored.thumbPath, s.aspect, s.label, audioRel, s.useInMemory, s.useInPuzzle,
                        promptAudioPath = promptRel, countable = s.countable,
                    ),
                )
                state.update { it.copy(saving = false) }
                loadPending(s.index + 1)
            }
        }
    }

    override fun onCleared() {
        discardTempRecordings()
    }
}
