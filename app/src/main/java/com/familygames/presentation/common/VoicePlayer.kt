package com.familygames.presentation.common

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.familygames.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.coroutines.resume

/** Plays the parent's voice recordings (docs/03 S03, S10). */
fun interface VoicePlayer {
    /** Plays [path] and returns when it has finished; recordings never overlap (queued). */
    suspend fun playAndAwait(path: String)
}

/** MediaPlayer-based [VoicePlayer]: muted with the S01 sound toggle (Q6), at most 9 s per recording. */
class MediaVoicePlayer(private val settings: SettingsRepository) : VoicePlayer {
    private val queue = Mutex()

    override suspend fun playAndAwait(path: String) = queue.withLock {
        if (!settings.settings.first().soundEnabled) return@withLock
        withTimeoutOrNull(MAX_PLAY_MS) { playOnce(path) } ?: Unit
    }

    private suspend fun playOnce(path: String) = suspendCancellableCoroutine { cont ->
        val player = MediaPlayer()
        cont.invokeOnCancellation { player.release() }
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            player.setDataSource(path)
            player.setOnCompletionListener {
                it.release()
                if (cont.isActive) cont.resume(Unit)
            }
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                if (cont.isActive) cont.resume(Unit)
                true
            }
            player.setOnPreparedListener { it.start() }
            player.prepareAsync()
        } catch (e: Exception) {
            // A missing or broken recording must never interrupt the game (docs/04 §7).
            Timber.w(e, "Voice playback failed")
            player.release()
            if (cont.isActive) cont.resume(Unit)
        }
    }

    private companion object {
        const val MAX_PLAY_MS = 9_000L
    }
}
