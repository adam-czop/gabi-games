package com.familygames.domain.game.garden

import kotlin.random.Random

/** How far a plant has grown; water moves it up to [BUD], the sun opens the bud. */
enum class Stage { EMPTY, SEED, SPROUT, LEAVES, BUD, FLOWER }

data class Plot(val stage: Stage = Stage.EMPTY, val color: Int = 0)

sealed interface GardenOutcome {
    data object Nothing : GardenOutcome
    data object Planted : GardenOutcome
    /** [grown] plants moved up a stage. */
    data class Watered(val grown: Int) : GardenOutcome
    /** [bloomed] buds opened. */
    data class Sunshine(val bloomed: Int) : GardenOutcome
    /** A flower went to the vase; [color] is its paint. */
    data class Picked(val color: Int) : GardenOutcome
}

/**
 * M28 · "Ogródek" — free play without a goal (docs/12 §0). Tap a bed to sow, the watering can makes every sown
 * plant grow one step (seed → sprout → leaves → bud), the sun opens all buds, and a tap on a flower puts it into
 * the vase so the bed is free again. Flower colours are random paints; nothing can go wrong.
 */
class Garden(private val random: Random, beds: Int = BEDS) {
    private val plots = MutableList(beds) { Plot() }
    var vase: List<Int> = emptyList()
        private set

    val beds: List<Plot> get() = plots.toList()

    fun tapBed(i: Int): GardenOutcome {
        val plot = plots.getOrNull(i) ?: return GardenOutcome.Nothing
        return when (plot.stage) {
            Stage.EMPTY -> {
                plots[i] = Plot(Stage.SEED, random.nextInt(COLORS))
                GardenOutcome.Planted
            }
            Stage.FLOWER -> {
                plots[i] = Plot()
                vase = (vase + plot.color).takeLast(VASE_MAX)
                GardenOutcome.Picked(plot.color)
            }
            else -> GardenOutcome.Nothing
        }
    }

    fun water(): GardenOutcome {
        var grown = 0
        plots.indices.forEach { i ->
            val s = plots[i].stage
            if (s in Stage.SEED..Stage.LEAVES) {
                plots[i] = plots[i].copy(stage = Stage.entries[s.ordinal + 1])
                grown++
            }
        }
        return GardenOutcome.Watered(grown)
    }

    fun sun(): GardenOutcome {
        var bloomed = 0
        plots.indices.forEach { i ->
            if (plots[i].stage == Stage.BUD) {
                plots[i] = plots[i].copy(stage = Stage.FLOWER)
                bloomed++
            }
        }
        return GardenOutcome.Sunshine(bloomed)
    }

    companion object {
        const val BEDS = 4
        const val COLORS = 6
        const val VASE_MAX = 9
    }
}
