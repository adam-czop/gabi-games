package com.familygames.domain.game.weather

import kotlin.random.Random

enum class WeatherKind { SUN, RAIN, SNOW }

/** Things to put on, each good for one kind of weather. */
enum class Garment(val weather: WeatherKind) {
    CAP(WeatherKind.SUN),
    GLASSES(WeatherKind.SUN),
    UMBRELLA(WeatherKind.RAIN),
    BOOTS(WeatherKind.RAIN),
    HAT(WeatherKind.SNOW),
    MITTENS(WeatherKind.SNOW),
}

/** Levels of "Ubierz na pogodę" (docs/12 M29): how many things that do not fit lie in the tray. */
enum class WeatherLevel(val key: String, val wrong: Int) {
    EASY("weather_1", 1),
    HARDER("weather_2", 2),
    ;

    companion object {
        fun fromKey(key: String): WeatherLevel = entries.firstOrNull { it.key == key } ?: EASY
    }
}

/** One round: the weather outside and the tray (the right things and some that do not fit, mixed). */
data class WeatherRound(val weather: WeatherKind, val tray: List<Garment>) {
    val needed: List<Garment> get() = tray.filter { it.weather == weather }
}

class WeatherDealer(private val random: Random) {
    /** Every weather once, in random order; the odd things come from the other weathers. */
    fun deal(level: WeatherLevel): List<WeatherRound> = WeatherKind.entries.shuffled(random).map { weather ->
        val right = Garment.entries.filter { it.weather == weather }
        val wrong = Garment.entries.filter { it.weather != weather }.shuffled(random).take(level.wrong)
        WeatherRound(weather, (right + wrong).shuffled(random))
    }
}

/** The child figure of one round: it takes only what suits the weather, each thing once. */
class Dresser(val round: WeatherRound) {
    private val worn = mutableSetOf<Garment>()
    var mistakes = 0
        private set

    val wearing: Set<Garment> get() = worn.toSet()

    sealed interface Outcome {
        data object Ignored : Outcome
        data object Wrong : Outcome
        data class Worn(val dressed: Boolean) : Outcome
    }

    fun put(garment: Garment): Outcome {
        if (garment !in round.tray || garment in worn) return Outcome.Ignored
        if (garment.weather != round.weather) {
            mistakes++
            return Outcome.Wrong
        }
        worn += garment
        return Outcome.Worn(dressed = worn.containsAll(round.needed))
    }
}
