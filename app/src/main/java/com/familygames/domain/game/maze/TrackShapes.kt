package com.familygames.domain.game.maze

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Built-in track shapes, from the easiest; [MAZE] is generated anew every time. */
enum class TrackShape { LINE, ARC, WAVE, ZIGZAG, SNAKE, SPIRAL, MAZE }

/** Levels of "Labirynt" (docs/12 M19): three tracks per game, narrower corridors as the shapes get harder. */
enum class MazeLevel(val key: String, val shapes: List<TrackShape>, val width: Float) {
    L1("maze_1", listOf(TrackShape.LINE, TrackShape.ARC, TrackShape.WAVE), 150f),
    L2("maze_2", listOf(TrackShape.WAVE, TrackShape.ARC, TrackShape.ZIGZAG), 130f),
    L3("maze_3", listOf(TrackShape.ZIGZAG, TrackShape.SNAKE, TrackShape.SPIRAL), 115f),
    L4("maze_4", listOf(TrackShape.MAZE, TrackShape.MAZE, TrackShape.MAZE), 100f),
    ;

    val tracks: Int get() = shapes.size

    companion object {
        fun fromKey(key: String): MazeLevel = entries.firstOrNull { it.key == key } ?: L1
    }
}

/** Builds tracks on the 1200 × 640 board; the random source only mirrors the shapes and digs the mazes. */
class TrackFactory(private val random: Random) {

    fun game(level: MazeLevel): List<Track> = level.shapes.map { build(it, level.width) }

    fun build(shape: TrackShape, width: Float): Track = when (shape) {
        TrackShape.MAZE -> maze(MAZE_COLS, MAZE_ROWS, width)
        else -> Track.chain(mirror(curve(shape)), width)
    }

    /** The traced shapes as dense polylines (one point every [STEP] units or so). */
    private fun curve(shape: TrackShape): List<Point> = when (shape) {
        TrackShape.LINE -> listOf(Point(170f, 320f), Point(1030f, 320f)).densify()
        TrackShape.ARC -> sample(48) { u ->
            val a = PI * (1 - u)
            Point(600f + 420f * cos(a).toFloat(), 515f - 380f * sin(a).toFloat())
        }
        TrackShape.WAVE -> sample(72) { u -> Point(150f + 900f * u, 320f - 170f * sin(u * 3 * PI).toFloat()) }
        TrackShape.ZIGZAG -> listOf(
            Point(140f, 170f), Point(320f, 470f), Point(500f, 170f), Point(680f, 470f), Point(860f, 170f), Point(1060f, 470f),
        ).densify()
        TrackShape.SNAKE -> snake()
        TrackShape.SPIRAL -> sample(100) { u ->
            // An oval snail from the centre outwards (the goal photo sits outside, not on top of the inner loop).
            // Loops are SPIRAL_GAP apart vertically, enough for the widest corridor of the level plus a wall.
            val turns = u * SPIRAL_TURNS
            val a = turns * 2 * PI
            val rx = 90f + 280f * turns
            val ry = 40f + SPIRAL_GAP * turns
            Point(600f + rx * cos(a).toFloat(), 320f + ry * sin(a).toFloat())
        }
        TrackShape.MAZE -> error("mazes are graphs, not curves")
    }

    /** Three rows joined by half circles. */
    private fun snake(): List<Point> {
        val rows = listOf(140f, 320f, 500f)
        val left = 200f
        val right = 1000f
        val r = (rows[1] - rows[0]) / 2
        val points = mutableListOf<Point>()
        rows.forEachIndexed { i, y ->
            val (from, to) = if (i % 2 == 0) left to right else right to left
            points += listOf(Point(from, y), Point(to, y)).densify()
            if (i < rows.lastIndex) {
                val cx = to
                val cy = y + r
                val side = if (i % 2 == 0) 1 else -1
                for (k in 1 until 16) {
                    val a = -PI / 2 + PI * k / 16
                    points += Point(cx + side * r * cos(a).toFloat(), cy + r * sin(a).toFloat())
                }
            }
        }
        return points
    }

    private fun mirror(points: List<Point>): List<Point> {
        val flipX = random.nextBoolean()
        val flipY = random.nextBoolean()
        return points.map { Point(if (flipX) BOARD_W - it.x else it.x, if (flipY) BOARD_H - it.y else it.y) }
    }

    /**
     * A perfect maze (one way between any two cells) dug by a random depth-first walk over a grid of cells.
     * Start top-left, goal bottom-right; the tracks go between cell centres.
     */
    fun maze(cols: Int, rows: Int, width: Float): Track {
        val cellW = (BOARD_W - 2 * MARGIN) / cols
        val cellH = (BOARD_H - 2 * MARGIN) / rows
        val nodes = List(cols * rows) { i -> Point(MARGIN + cellW * (i % cols + 0.5f), MARGIN + cellH * (i / cols + 0.5f)) }
        val visited = BooleanArray(nodes.size)
        val edges = mutableListOf<Pair<Int, Int>>()
        val stack = ArrayDeque<Int>()
        visited[0] = true
        stack.addLast(0)
        while (stack.isNotEmpty()) {
            val cell = stack.last()
            val c = cell % cols
            val r = cell / cols
            val next = listOfNotNull(
                (cell - 1).takeIf { c > 0 }, (cell + 1).takeIf { c < cols - 1 },
                (cell - cols).takeIf { r > 0 }, (cell + cols).takeIf { r < rows - 1 },
            ).filter { !visited[it] }
            if (next.isEmpty()) {
                stack.removeLast()
            } else {
                val n = next[random.nextInt(next.size)]
                visited[n] = true
                edges += cell to n
                stack.addLast(n)
            }
        }
        return Track(nodes, edges, start = 0, goal = nodes.size - 1, width = width)
    }

    private fun sample(n: Int, f: (Float) -> Point): List<Point> = List(n + 1) { f(it.toFloat() / n) }

    private fun List<Point>.densify(): List<Point> = zipWithNext().flatMapIndexed { i, (a, b) ->
        val steps = maxOf(1, (a.distanceTo(b) / STEP).toInt())
        val part = (0 until steps).map { k -> Point(a.x + (b.x - a.x) * k / steps, a.y + (b.y - a.y) * k / steps) }
        if (i == size - 2) part + b else part
    }

    companion object {
        const val BOARD_W = 1200f
        const val BOARD_H = 640f
        const val STEP = 24f
        const val MARGIN = 40f
        const val MAZE_COLS = 6
        const val MAZE_ROWS = 3
        const val SPIRAL_TURNS = 1.25f
        const val SPIRAL_GAP = 155f

        /** Size of the goal photo, in corridor widths; it must fit on the board. */
        const val GOAL_PHOTO = 1.3f
    }
}
