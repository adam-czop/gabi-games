package com.familygames.presentation.common

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.familygames.data.local.files.MediaStorage
import com.familygames.domain.model.Photo
import com.familygames.domain.repository.SettingsRepository
import com.familygames.domain.voice.PromptPrefix
import com.familygames.domain.voice.PromptSources
import com.familygames.domain.voice.VoicePlan
import com.familygames.domain.voice.VoiceStep
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/** Spoken prompts of the games (docs/12 M10.3): parent recordings first, the Polish speech synthesizer as a fallback. */
interface VoicePrompts {
    /** True once a Polish synthesizer voice is ready. */
    val ttsAvailable: StateFlow<Boolean>

    /** Waits a moment for the synthesizer at game start, so the first question is not lost. */
    suspend fun awaitReady()

    suspend fun prefixClip(prefix: PromptPrefix): String?

    /** Plays the steps one after another (150 ms apart); cancelling the caller stops playback. */
    suspend fun play(steps: List<VoiceStep>)
}

/** The question about [photo]: recorded question, recorded prefix + label, or synthesized "prefix label?". */
suspend fun VoicePrompts.question(photo: Photo, prefix: PromptPrefix?, prefixText: String?): List<VoiceStep> = VoicePlan.build(
    PromptSources(
        questionClip = photo.promptAudioPath,
        prefixClip = prefix?.let { prefixClip(it) },
        labelClip = photo.audioPath,
        label = photo.label,
        prefixText = prefixText,
        ttsAvailable = ttsAvailable.value,
    ),
)

/** Just the name of the photo: the parent's recording or the synthesizer. */
fun VoicePrompts.label(photo: Photo): List<VoiceStep> = VoicePlan.label(photo.audioPath, photo.label, ttsAvailable.value)

class AndroidVoicePrompts(
    context: Context,
    private val settings: SettingsRepository,
    private val storage: MediaStorage,
    private val voice: VoicePlayer,
) : VoicePrompts {
    private val ready = MutableStateFlow(false)
    override val ttsAvailable: StateFlow<Boolean> = ready.asStateFlow()
    private val initDone = CompletableDeferred<Unit>()
    private val queue = Mutex()
    private val pending = mutableMapOf<String, (Unit) -> Unit>()

    private val tts: TextToSpeech? = try {
        TextToSpeech(context.applicationContext) { status -> onInit(status) }
    } catch (_: Exception) {
        initDone.complete(Unit)
        null
    }

    private fun onInit(status: Int) {
        val engine = tts
        if (status == TextToSpeech.SUCCESS && engine != null) {
            val polish = Locale.forLanguageTag("pl-PL")
            if (engine.isLanguageAvailable(polish) >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = polish
                engine.setSpeechRate(SPEECH_RATE)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) = Unit
                    override fun onDone(utteranceId: String) = finish(utteranceId)

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String) = finish(utteranceId)
                    override fun onError(utteranceId: String, errorCode: Int) = finish(utteranceId)
                    override fun onStop(utteranceId: String, interrupted: Boolean) = finish(utteranceId)
                })
                ready.value = true
            }
        }
        initDone.complete(Unit)
    }

    private fun finish(id: String) {
        synchronized(pending) { pending.remove(id) }?.invoke(Unit)
    }

    override suspend fun awaitReady() {
        withTimeoutOrNull(READY_WAIT_MS) { initDone.await() }
    }

    override suspend fun prefixClip(prefix: PromptPrefix): String? =
        settings.settings.first().prefixClips[prefix.key]?.let { storage.resolve(it) }?.takeIf { it.exists() }?.absolutePath

    override suspend fun play(steps: List<VoiceStep>) = queue.withLock {
        if (steps.isEmpty() || !settings.settings.first().soundEnabled) return@withLock
        steps.forEachIndexed { i, step ->
            if (i > 0) delay(GAP_MS)
            when (step) {
                is VoiceStep.Clip -> voice.playAndAwait(step.path)
                is VoiceStep.Speech -> speak(step.text)
            }
        }
    }

    private suspend fun speak(text: String) {
        val engine = tts ?: return
        if (!ready.value) return
        withTimeoutOrNull(MAX_SPEECH_MS) {
            suspendCancellableCoroutine { cont ->
                val id = UUID.randomUUID().toString()
                synchronized(pending) { pending[id] = { if (cont.isActive) cont.resume(Unit) } }
                cont.invokeOnCancellation {
                    synchronized(pending) { pending.remove(id) }
                    engine.stop()
                }
                if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id) != TextToSpeech.SUCCESS) finish(id)
            }
        }
    }

    private companion object {
        const val GAP_MS = 150L
        const val READY_WAIT_MS = 1_500L
        const val MAX_SPEECH_MS = 8_000L
        const val SPEECH_RATE = 0.9f
    }
}
