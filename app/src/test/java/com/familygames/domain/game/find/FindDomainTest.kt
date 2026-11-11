package com.familygames.domain.game.find

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ScenesTest {
    @Test
    fun `every scene has enough big things for a game, distinct keys and drawings inside the board`() {
        assertEquals(4, Scenes.all.size)
        Scenes.all.forEach { scene ->
            assertTrue(scene.key, scene.objects.count { it.big } >= FindLevel.BIG.rounds)
            assertTrue(scene.key, scene.objects.count { !it.big } >= 2)
            assertEquals(scene.objects.size, scene.objects.map { it.key }.distinct().size)
            scene.objects.forEach { o ->
                val (l, t, r, b) = o.bounds.toList()
                assertTrue("${scene.key}/${o.key}", l >= 0f && t >= 0f && r <= Scene.WIDTH + 10f && b <= Scene.HEIGHT)
            }
        }
    }

    @Test
    fun `the centre of every thing finds that thing, the empty sky finds nothing`() {
        Scenes.all.forEach { scene ->
            scene.objects.forEach { o ->
                // Some point of the object must select it (it is not hidden behind others).
                val hits = o.shapes.any { s ->
                    val (l, t, r, b) = s.bounds().toList()
                    scene.objectAt((l + r) / 2, (t + b) / 2)?.key == o.key
                }
                assertTrue("${scene.key}/${o.key} cannot be tapped", hits)
            }
        }
        assertNull(Scenes.byKey("farm")!!.objectAt(700f, 40f))
    }

    @Test
    fun `a near miss still counts, a far one does not`() {
        val farm = Scenes.byKey("farm")!!
        assertEquals("sun", farm.objectAt(1080f, 96f - 52f - 10f)?.key)
        assertNotEquals("sun", farm.objectAt(1080f - 200f, 96f)?.key)
    }
}

class FindGameTest {
    private val farm = Scenes.byKey("farm")!!

    @Test
    fun `big level asks only about big things, all level adds small ones, no repeats`() {
        repeat(30) { seed ->
            val big = FindGame.deal(farm, FindLevel.BIG, Random(seed))
            assertEquals(5, big.targets.size)
            assertTrue(big.targets.all { it.big })
            val all = FindGame.deal(farm, FindLevel.ALL, Random(seed))
            assertEquals(5, all.targets.size)
            assertTrue(all.targets.count { !it.big } >= 2)
            assertEquals(5, all.targets.map { it.key }.distinct().size)
        }
    }

    @Test
    fun `misses count, two misses give a hint, the scenery is ignored`() {
        val game = FindGame.deal(farm, FindLevel.BIG, Random(1))
        val wrong = farm.objects.first { it.key != game.current.key }.key
        assertEquals(FindOutcome.Ignored, game.tap(null))
        assertEquals(FindOutcome.Wrong(hint = false), game.tap(wrong))
        assertEquals(FindOutcome.Wrong(hint = true), game.tap(wrong))
        assertEquals(FindOutcome.Correct(last = false), game.tap(game.current.key))
        assertEquals(FindOutcome.Ignored, game.tap(wrong))
        assertEquals(2, game.mistakes)
        assertTrue(game.next())
        assertEquals(1, game.round)
    }

    @Test
    fun `the last right answer finishes the game`() {
        val game = FindGame.deal(farm, FindLevel.BIG, Random(2))
        repeat(4) {
            assertEquals(FindOutcome.Correct(last = false), game.tap(game.current.key))
            game.next()
        }
        assertEquals(FindOutcome.Correct(last = true), game.tap(game.current.key))
        assertTrue(game.finished)
        assertFalse(game.next())
    }

    @Test
    fun `the next scene differs from the previous one`() {
        repeat(30) { seed -> assertNotEquals("farm", FindGame.pickScene(Random(seed), "farm").key) }
    }
}
