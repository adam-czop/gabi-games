package com.familygames.domain.game.garden

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GardenTest {
    @Test
    fun `sow, water three times, sun, pick - and the bed is free again`() {
        val garden = Garden(Random(1))
        assertEquals(GardenOutcome.Planted, garden.tapBed(0))
        assertEquals(GardenOutcome.Nothing, garden.tapBed(0))
        repeat(3) { assertEquals(GardenOutcome.Watered(1), garden.water()) }
        assertEquals(Stage.BUD, garden.beds[0].stage)
        assertEquals(GardenOutcome.Watered(0), garden.water())
        assertEquals(GardenOutcome.Sunshine(1), garden.sun())
        assertEquals(Stage.FLOWER, garden.beds[0].stage)
        val color = garden.beds[0].color
        assertEquals(GardenOutcome.Picked(color), garden.tapBed(0))
        assertEquals(Stage.EMPTY, garden.beds[0].stage)
        assertEquals(listOf(color), garden.vase)
    }

    @Test
    fun `the sun opens only buds, water never goes past the bud`() {
        val garden = Garden(Random(2))
        garden.tapBed(0)
        garden.tapBed(1)
        repeat(3) { garden.water() }
        garden.tapBed(2)
        assertEquals(GardenOutcome.Sunshine(2), garden.sun())
        assertEquals(Stage.SEED, garden.beds[2].stage)
        repeat(10) { garden.water() }
        assertEquals(Stage.BUD, garden.beds[2].stage)
    }

    @Test
    fun `the vase keeps the last flowers, colours are paints, taps outside the beds do nothing`() {
        val garden = Garden(Random(3))
        repeat(15) {
            garden.tapBed(0)
            repeat(3) { garden.water() }
            garden.sun()
            garden.tapBed(0)
        }
        assertEquals(Garden.VASE_MAX, garden.vase.size)
        assertTrue(garden.vase.all { it in 0 until Garden.COLORS })
        assertEquals(GardenOutcome.Nothing, garden.tapBed(9))
    }
}
