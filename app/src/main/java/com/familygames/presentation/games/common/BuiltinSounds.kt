package com.familygames.presentation.games.common

import androidx.annotation.StringRes
import com.familygames.R
import com.familygames.domain.model.Photo
import com.familygames.domain.model.PhotoSource

/**
 * Sounds of the built-in illustrations for "Memo dźwiękowe" (docs/12 M14). ADR-023: no bundled animal recordings –
 * the synthesizer says the onomatopoeia ("miau miau"); the parent's photos say their name (ADR-030).
 */
object BuiltinSounds {
    private val sounds = mapOf(
        "zwierzeta_kot" to R.string.sound_cat,
        "zwierzeta_pies" to R.string.sound_dog,
        "zwierzeta_krowa" to R.string.sound_cow,
        "zwierzeta_kaczka" to R.string.sound_duck,
        "zwierzeta_kon" to R.string.sound_horse,
        "zwierzeta_swinka" to R.string.sound_pig,
        "zwierzeta_kura" to R.string.sound_hen,
        "zwierzeta_owca" to R.string.sound_sheep,
        "zabawki_auto" to R.string.sound_car,
        "zabawki_bebenek" to R.string.sound_drum,
        "zabawki_kaczuszka" to R.string.sound_rubber_duck,
        "zabawki_rakieta" to R.string.sound_rocket,
        "pojazdy_samochod" to R.string.sound_car,
        "pojazdy_pociag" to R.string.sound_train,
        "pojazdy_straz" to R.string.sound_fire_truck,
        "pojazdy_autobus" to R.string.sound_bus,
        "pojazdy_traktor" to R.string.sound_tractor,
    )

    @StringRes
    fun soundFor(photo: Photo): Int? {
        val asset = (photo.image as? PhotoSource.Asset)?.path ?: return null
        return sounds[asset.substringAfterLast('/').substringBeforeLast('.')]
    }

    fun hasSound(photo: Photo, ttsAvailable: Boolean): Boolean = ttsAvailable && (soundFor(photo) != null || !photo.label.isNullOrBlank())
}
