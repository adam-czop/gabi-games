package com.familygames.presentation.common

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import com.familygames.R
import com.familygames.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Sound effects (docs/08 §6). Rewarding sounds are a bit louder than the neutral ones. */
enum class Sfx(@param:RawRes val res: Int, val volume: Float) {
    TAP(R.raw.sfx_tap, 0.5f),
    FLIP(R.raw.sfx_flip, 0.5f),
    MATCH(R.raw.sfx_match, 0.7f),
    SOFT(R.raw.sfx_soft, 0.4f),
    SNAP(R.raw.sfx_snap, 0.5f),
    STAR(R.raw.sfx_star, 0.7f),
    WIN(R.raw.sfx_win, 0.8f),
    POP(R.raw.sfx_pop, 0.55f),
}

/** Xylophone bars (docs/12 M15b): eight marimba notes, up to 8 at once for multi-touch. */
fun interface NotePlayer {
    fun play(bar: Int)
}

class SoundPoolNotePlayer(context: Context, settings: SettingsRepository, scope: CoroutineScope) : NotePlayer {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .build()
    private val notes = listOf(R.raw.note_c, R.raw.note_d, R.raw.note_e, R.raw.note_f, R.raw.note_g, R.raw.note_a, R.raw.note_c2, R.raw.note_d2)
        .map { pool.load(context, it, 1) }

    @Volatile
    private var enabled = true

    init {
        scope.launch { settings.settings.map { it.soundEnabled }.distinctUntilChanged().collect { enabled = it } }
    }

    override fun play(bar: Int) {
        if (!enabled) return
        notes.getOrNull(bar)?.let { pool.play(it, 0.7f, 0.7f, 1, 0, 1f) }
    }
}

fun interface SoundPlayer {
    /** [rate] 0.5–2.0 changes the pitch (rising stars in S04). Silent when sound is off in settings. */
    fun play(sfx: Sfx, rate: Float)

    fun play(sfx: Sfx) = play(sfx, 1f)
}

/** SoundPool loaded once for the whole app (docs/01 §8): max 6 streams, game usage. */
class SoundPoolPlayer(context: Context, settings: SettingsRepository, scope: CoroutineScope) : SoundPlayer {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val soundIds: Map<Sfx, Int> = Sfx.entries.associateWith { pool.load(context, it.res, 1) }

    @Volatile
    private var enabled = true

    init {
        scope.launch {
            settings.settings.map { it.soundEnabled }.distinctUntilChanged().collect { enabled = it }
        }
    }

    override fun play(sfx: Sfx, rate: Float) {
        if (!enabled) return
        val id = soundIds[sfx] ?: return
        pool.play(id, sfx.volume, sfx.volume, 1, 0, rate.coerceIn(0.5f, 2f))
    }
}
