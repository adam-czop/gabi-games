package com.familygames.presentation.games.find

import androidx.annotation.StringRes
import com.familygames.R

/** Polish name and question of every scene object (UI texts live in strings.xml, CLAUDE.md). */
object FindTexts {
    private class Texts(@param:StringRes val name: Int, @param:StringRes val question: Int, @param:StringRes val sound: Int? = null)

    private val texts = mapOf(
        "sun" to Texts(R.string.find_sun, R.string.find_q_sun),
        "barn" to Texts(R.string.find_barn, R.string.find_q_barn),
        "tree" to Texts(R.string.find_tree, R.string.find_q_tree),
        "tractor" to Texts(R.string.find_tractor, R.string.find_q_tractor, R.string.sound_tractor),
        "cow" to Texts(R.string.find_cow, R.string.find_q_cow, R.string.sound_cow),
        "pig" to Texts(R.string.find_pig, R.string.find_q_pig, R.string.sound_pig),
        "chicken" to Texts(R.string.find_chicken, R.string.find_q_chicken, R.string.sound_hen),
        "sheep" to Texts(R.string.find_sheep, R.string.find_q_sheep, R.string.sound_sheep),
        "flower" to Texts(R.string.find_flower, R.string.find_q_flower),
        "cloud" to Texts(R.string.find_cloud, R.string.find_q_cloud),
        "block" to Texts(R.string.find_block, R.string.find_q_block),
        "house" to Texts(R.string.find_house, R.string.find_q_house),
        "lights" to Texts(R.string.find_lights, R.string.find_q_lights),
        "bus" to Texts(R.string.find_bus, R.string.find_q_bus, R.string.sound_bus),
        "car" to Texts(R.string.find_car, R.string.find_q_car, R.string.sound_car),
        "bike" to Texts(R.string.find_bike, R.string.find_q_bike),
        "dog" to Texts(R.string.find_dog, R.string.find_q_dog, R.string.sound_dog),
        "window" to Texts(R.string.find_window, R.string.find_q_window),
        "fridge" to Texts(R.string.find_fridge, R.string.find_q_fridge),
        "clock" to Texts(R.string.find_clock, R.string.find_q_clock),
        "table" to Texts(R.string.find_table, R.string.find_q_table),
        "chair" to Texts(R.string.find_chair, R.string.find_q_chair),
        "cup" to Texts(R.string.find_cup, R.string.find_q_cup),
        "apple" to Texts(R.string.find_apple, R.string.find_q_apple),
        "stove" to Texts(R.string.find_stove, R.string.find_q_stove),
        "pot" to Texts(R.string.find_pot, R.string.find_q_pot),
        "cat" to Texts(R.string.find_cat, R.string.find_q_cat, R.string.sound_cat),
        "kite" to Texts(R.string.find_kite, R.string.find_q_kite),
        "slide" to Texts(R.string.find_slide, R.string.find_q_slide),
        "swing" to Texts(R.string.find_swing, R.string.find_q_swing),
        "bench" to Texts(R.string.find_bench, R.string.find_q_bench),
        "sandbox" to Texts(R.string.find_sandbox, R.string.find_q_sandbox),
        "bucket" to Texts(R.string.find_bucket, R.string.find_q_bucket),
        "ball" to Texts(R.string.find_ball, R.string.find_q_ball),
    )

    val keys: Set<String> get() = texts.keys

    @StringRes fun name(key: String): Int = texts[key]?.name ?: R.string.app_name

    @StringRes fun question(key: String): Int = texts[key]?.question ?: R.string.app_name

    /** Animals and vehicles also make their sound when found or explored ("krowa – muu"). */
    @StringRes fun sound(key: String): Int? = texts[key]?.sound
}
