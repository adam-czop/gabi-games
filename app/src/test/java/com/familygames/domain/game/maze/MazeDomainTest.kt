package com.familygames.domain.game.maze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TracerTest {
    private val line = Track.chain(listOf(Point(100f, 300f), Point(400f, 300f), Point(700f, 300f)), width = 100f)

    /** Moves the finger from [from] to [to] in small steps, like a real drag. */
    private fun Tracer.drag(from: Point, to: Point, steps: Int = 60): List<TraceOutcome> =
        (1..steps).map { i -> move(Point(from.x + (to.x - from.x) * i / steps, from.y + (to.y - from.y) * i / steps)) }

    @Test
    fun `dragging along the corridor reaches the goal`() {
        val tracer = Tracer(line)
        assertTrue(tracer.down(Point(110f, 310f)))
        val outcomes = tracer.drag(Point(110f, 310f), Point(700f, 300f))
        assertTrue(TraceOutcome.Finished in outcomes)
        assertTrue(tracer.finished)
        assertEquals(0, tracer.slips)
        assertEquals(Point(700f, 300f), tracer.point)
    }

    @Test
    fun `a touch away from the ball does not grab it`() {
        val tracer = Tracer(line)
        assertFalse(tracer.down(Point(500f, 300f)))
        assertEquals(TraceOutcome.Ignored, tracer.move(Point(520f, 300f)))
        assertEquals(Point(100f, 300f), tracer.point)
    }

    @Test
    fun `leaving the corridor drops the ball where it was and counts a slip`() {
        val tracer = Tracer(line)
        tracer.down(Point(100f, 300f))
        tracer.drag(Point(100f, 300f), Point(300f, 300f))
        val outcomes = tracer.drag(Point(300f, 300f), Point(300f, 460f))
        assertTrue(TraceOutcome.Slipped in outcomes)
        assertEquals(1, tracer.slips)
        assertFalse(tracer.holding)
        assertEquals(300f, tracer.point.x, 1f)
        assertEquals(300f, tracer.point.y, 1f)
    }

    @Test
    fun `coming back to the ball with the finger still down picks it up again`() {
        val tracer = Tracer(line)
        tracer.down(Point(100f, 300f))
        tracer.drag(Point(100f, 300f), Point(100f, 460f))
        assertFalse(tracer.holding)
        tracer.drag(Point(100f, 460f), Point(110f, 300f))
        assertTrue(tracer.holding)
        assertTrue(TraceOutcome.Finished in tracer.drag(Point(110f, 300f), Point(700f, 300f)))
    }

    @Test
    fun `a fast jump far ahead does not move the ball`() {
        val tracer = Tracer(line)
        tracer.down(Point(100f, 300f))
        assertEquals(TraceOutcome.Slipped, tracer.move(Point(650f, 300f)))
        assertEquals(Point(100f, 300f), tracer.point)
    }

    @Test
    fun `the ball never crosses a wall into the neighbouring corridor`() {
        // A U-turn: two parallel corridors 160 apart (width 100, wall 60), joined on the right.
        val u = Track.chain(listOf(Point(100f, 100f), Point(600f, 100f), Point(600f, 260f), Point(100f, 260f)), width = 100f)
        val tracer = Tracer(u)
        tracer.down(Point(100f, 100f))
        tracer.drag(Point(100f, 100f), Point(150f, 100f))
        // Straight down across the wall to the lower corridor, right next to the goal.
        tracer.drag(Point(150f, 100f), Point(150f, 260f))
        assertFalse(tracer.finished)
        assertTrue(tracer.point.y < 160f)
    }

    @Test
    fun `nothing moves after the goal`() {
        val tracer = Tracer(line)
        tracer.down(Point(100f, 300f))
        tracer.drag(Point(100f, 300f), Point(700f, 300f))
        assertFalse(tracer.down(Point(700f, 300f)))
        assertEquals(TraceOutcome.Ignored, tracer.move(Point(650f, 300f)))
    }

    @Test
    fun `stars allow about one slip per track and never drop below one`() {
        assertEquals(3, TraceScoring.stars(3, 0))
        assertEquals(3, TraceScoring.stars(3, 3))
        assertEquals(2, TraceScoring.stars(3, 4))
        assertEquals(2, TraceScoring.stars(3, 9))
        assertEquals(1, TraceScoring.stars(3, 50))
    }
}

class TrackFactoryTest {

    @Test
    fun `every level deals its tracks inside the board`() {
        repeat(10) { seed ->
            MazeLevel.entries.forEach { level ->
                val tracks = TrackFactory(Random(seed)).game(level)
                assertEquals(level.tracks, tracks.size)
                tracks.forEach { t ->
                    t.nodes.forEach { p ->
                        val m = t.width / 2
                        assertTrue("$level $p", p.x >= m && p.x <= TrackFactory.BOARD_W - m && p.y >= m && p.y <= TrackFactory.BOARD_H - m)
                    }
                }
            }
        }
    }

    @Test
    fun `the goal photo fits on the board`() {
        repeat(10) { seed ->
            MazeLevel.entries.forEach { level ->
                TrackFactory(Random(seed)).game(level).forEach { t ->
                    val g = t.nodes[t.goal]
                    val r = t.width * TrackFactory.GOAL_PHOTO / 2
                    assertTrue("$level $g", g.x - r >= 0 && g.x + r <= TrackFactory.BOARD_W && g.y - r >= 0 && g.y + r <= TrackFactory.BOARD_H)
                }
            }
        }
    }

    @Test
    fun `corridor parts far apart along the track are separated by a wall`() {
        MazeLevel.entries.filter { it != MazeLevel.L4 }.forEach { level ->
            level.shapes.distinct().forEach { shape ->
                val track = TrackFactory(Random(1)).build(shape, level.width)
                val along = FloatArray(track.nodes.size)
                for (i in 1 until track.nodes.size) along[i] = along[i - 1] + track.nodes[i - 1].distanceTo(track.nodes[i])
                for (i in track.nodes.indices) for (j in i + 1 until track.nodes.size) {
                    if (along[j] - along[i] > 3 * level.width) {
                        val gap = track.nodes[i].distanceTo(track.nodes[j])
                        assertTrue("$level $shape $i-$j gap $gap", gap >= level.width * 1.15f)
                    }
                }
            }
        }
    }

    @Test
    fun `a maze is a tree that joins every cell, the same for the same seed`() {
        repeat(20) { seed ->
            val maze = TrackFactory(Random(seed)).maze(6, 3, 100f)
            assertEquals(18, maze.nodes.size)
            assertEquals(17, maze.edges.size)
            maze.nodes.indices.forEach { assertNotNull(maze.pathLength(maze.start, it)) }
            maze.edges.forEach { (a, b) -> assertEquals(1, if (a % 6 == b % 6) kotlin.math.abs(a - b) / 6 else kotlin.math.abs(a - b)) }
            assertEquals(maze.edges, TrackFactory(Random(seed)).maze(6, 3, 100f).edges)
        }
    }

    @Test
    fun `the ball solves a maze by following the corridors`() {
        val maze = TrackFactory(Random(7)).maze(6, 3, 100f)
        val route = route(maze, maze.start, maze.goal)
        val tracer = Tracer(maze)
        assertTrue(tracer.down(maze.nodes[maze.start]))
        var outcome: TraceOutcome = TraceOutcome.Ignored
        route.zipWithNext().forEach { (a, b) ->
            val pa = maze.nodes[a]
            val pb = maze.nodes[b]
            for (i in 1..30) if (!tracer.finished) outcome = tracer.move(Point(pa.x + (pb.x - pa.x) * i / 30, pa.y + (pb.y - pa.y) * i / 30))
        }
        assertEquals(TraceOutcome.Finished, outcome)
        assertEquals(0, tracer.slips)
    }

    /** Node path through the tree (depth-first). */
    private fun route(track: Track, from: Int, to: Int, came: Int = -1): List<Int> {
        if (from == to) return listOf(to)
        track.adjacency[from].forEach { e ->
            val (a, b) = track.edges[e]
            val next = if (a == from) b else a
            if (next != came) {
                val rest = route(track, next, to, from)
                if (rest.isNotEmpty()) return listOf(from) + rest
            }
        }
        return emptyList()
    }
}
