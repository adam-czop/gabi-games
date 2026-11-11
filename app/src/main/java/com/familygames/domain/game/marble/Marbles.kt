package com.familygames.domain.game.marble

import com.familygames.domain.game.maze.Point
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

// MARK: Path

/** The track the marbles roll along, measured by distance from its start (board units, 1200 × 640). */
class MarblePath(val points: List<Point>) {
    private val along: FloatArray = FloatArray(points.size).also { a ->
        for (i in 1 until points.size) a[i] = a[i - 1] + points[i - 1].distanceTo(points[i])
    }
    val length: Float get() = along.last()

    init {
        require(points.size >= 2) { "a path needs two points" }
    }

    /** The point [s] units from the start (clamped to the path). */
    fun pointAt(s: Float): Point {
        val d = s.coerceIn(0f, length)
        var lo = 0
        var hi = along.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (along[mid] <= d) lo = mid else hi = mid
        }
        val seg = along[hi] - along[lo]
        val k = if (seg == 0f) 0f else (d - along[lo]) / seg
        val a = points[lo]
        val b = points[hi]
        return Point(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k)
    }
}

/** Levels of "Kulki" (docs/12 M22): few colours, short chains and a slow pace; never a lost game. */
enum class MarbleLevel(val key: String, val colors: Int, val balls: Int, val match: Int, val speed: Float) {
    L1("marbles_1", colors = 2, balls = 12, match = 2, speed = 16f),
    L2("marbles_2", colors = 3, balls = 18, match = 3, speed = 18f),
    L3("marbles_3", colors = 3, balls = 24, match = 3, speed = 20f),
    L4("marbles_4", colors = 4, balls = 30, match = 3, speed = 22f),
    ;

    fun path(): MarblePath = MarblePath(
        when (this) {
            L1 -> sample(60) { u -> Point(600f + 500f * cos(PI * (1 - u)).toFloat(), 540f - 400f * sin(PI * u).toFloat()) }
            L2 -> sample(120) { u -> Point(60f + 1080f * u, 230f - 140f * sin(u * 4 * PI).toFloat()) }
            L3 -> snake(listOf(120f, 300f), left = 140f, right = 1060f)
            L4 -> snake(listOf(100f, 250f, 400f), left = 130f, right = 1070f)
        },
    )

    companion object {
        fun fromKey(key: String): MarbleLevel = entries.firstOrNull { it.key == key } ?: L1

        private fun sample(n: Int, f: (Float) -> Point): List<Point> = List(n + 1) { f(it.toFloat() / n) }

        /** Rows joined by half circles, starting at the left edge of the first row. */
        private fun snake(rows: List<Float>, left: Float, right: Float): List<Point> {
            val points = mutableListOf<Point>()
            rows.forEachIndexed { i, y ->
                val (from, to) = if (i % 2 == 0) left to right else right to left
                val start = if (i == 0) 40f else from
                points += Point(start, y)
                points += Point(to, y)
                if (i < rows.lastIndex) {
                    val r = (rows[i + 1] - y) / 2
                    val side = if (i % 2 == 0) 1 else -1
                    for (k in 1 until 20) {
                        val a = -PI / 2 + PI * k / 20
                        points += Point(to + side * r * cos(a).toFloat(), y + r + r * sin(a).toFloat())
                    }
                }
            }
            return points
        }
    }
}

// MARK: Engine

sealed interface MarbleEvent {
    /** [count] marbles popped; [combo] is 1 for a direct hit and grows with every chain reaction. */
    data class Popped(val count: Int, val combo: Int, val at: Point) : MarbleEvent
    data object Inserted : MarbleEvent
    /** The shot left the board without touching the chain; the marble is back in the cannon. */
    data object Missed : MarbleEvent
    data object Won : MarbleEvent
}

/** A run of touching marbles: colours from the rear to the front; the rear one is [rear] units along the path. */
class Segment(val colors: MutableList<Int>, var rear: Float) {
    fun posOf(index: Int): Float = rear + index * Marbles.DIAMETER
    val front: Float get() = posOf(colors.lastIndex)
}

data class Shot(val x: Float, val y: Float, val dx: Float, val dy: Float, val color: Int)

/**
 * Rules of "Kulki" (docs/12 M22). The chain rolls slowly along [path] and stops before the end, so the game cannot
 * be lost. A shot marble joins the chain where it hits; [MarbleLevel.match] or more touching marbles of one colour pop.
 * Only the rearmost segment is pushed; gaps close faster, and a segment rolls back to the one behind it when the
 * colours on both sides of the gap match — touching again can pop more (a chain reaction).
 * The cannon only ever holds colours still on the board (owner, 2026-11-11).
 */
class MarbleEngine(
    val path: MarblePath,
    val level: MarbleLevel,
    private val random: Random,
    initial: List<Int> = deal(level, random),
) {
    /** From the rear (index 0) to the front. */
    internal val segments = mutableListOf(Segment(initial.toMutableList(), -(initial.size - 1) * Marbles.DIAMETER))

    var shot: Shot? = null
        private set
    var current: Int = 0
        private set
    var next: Int = 0
        private set
    var wasted = 0
        private set
    private var rollingIn = true
    private var combo = 0

    val stopAt: Float get() = path.length - Marbles.DIAMETER * 0.6f
    val won: Boolean get() = segments.all { it.colors.isEmpty() }
    val remaining: Int get() = segments.sumOf { it.colors.size }

    init {
        current = pick()
        next = pick()
    }

    /** Visible marbles with their colour and place. */
    fun marbles(): List<Pair<Int, Point>> = segments.flatMap { seg ->
        seg.colors.indices.mapNotNull { i -> seg.posOf(i).takeIf { it >= 0f }?.let { s -> seg.colors[i] to path.pointAt(s) } }
    }

    /** Fires the cannon's marble towards [target]; false while a marble is still flying. */
    fun shoot(target: Point): Boolean {
        if (shot != null || won) return false
        val dx = target.x - Marbles.CANNON.x
        val dy = target.y - Marbles.CANNON.y
        val len = hypot(dx, dy)
        if (len < 1f) return false
        shot = Shot(Marbles.CANNON.x, Marbles.CANNON.y, dx / len, dy / len, current)
        current = next
        next = pick()
        return true
    }

    fun swap() {
        val c = current
        current = next
        next = c
    }

    fun step(dt: Float): List<MarbleEvent> {
        if (won) return emptyList()
        val events = mutableListOf<MarbleEvent>()
        moveShot(dt, events)
        moveChain(dt, events)
        if (won) events += MarbleEvent.Won
        return events
    }

    // MARK: Shot

    private fun moveShot(dt: Float, events: MutableList<MarbleEvent>) {
        var s = shot ?: return
        val stepLen = Marbles.DIAMETER / 3
        var travel = Marbles.SHOT_SPEED * dt
        while (travel > 0f) {
            val d = minOf(stepLen, travel)
            travel -= d
            s = s.copy(x = s.x + s.dx * d, y = s.y + s.dy * d)
            val hit = nearestBall(Point(s.x, s.y))
            if (hit != null) {
                shot = null
                insert(hit.first, hit.second, Point(s.x, s.y), s.color, events)
                return
            }
            if (s.x < -Marbles.DIAMETER || s.x > Marbles.BOARD_W + Marbles.DIAMETER || s.y < -Marbles.DIAMETER || s.y > Marbles.BOARD_H + Marbles.DIAMETER) {
                shot = null
                // The marble comes back: what was fired is in the cannon again.
                next = current
                current = s.color
                events += MarbleEvent.Missed
                return
            }
        }
        shot = s
    }

    private fun nearestBall(p: Point): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bestD = Marbles.DIAMETER
        segments.forEachIndexed { si, seg ->
            seg.colors.indices.forEach { i ->
                val s = seg.posOf(i)
                if (s >= 0f) {
                    val d = path.pointAt(s).distanceTo(p)
                    if (d < bestD) {
                        bestD = d
                        best = si to i
                    }
                }
            }
        }
        return best
    }

    private fun insert(si: Int, i: Int, at: Point, color: Int, events: MutableList<MarbleEvent>) {
        val seg = segments[si]
        val s = seg.posOf(i)
        val here = path.pointAt(s)
        val ahead = path.pointAt(s + 1f)
        val inFront = (at.x - here.x) * (ahead.x - here.x) + (at.y - here.y) * (ahead.y - here.y) > 0f
        val index = if (inFront) i + 1 else i
        if (index == 0) seg.rear -= Marbles.DIAMETER
        seg.colors.add(index, color)
        events += MarbleEvent.Inserted
        combo = 0
        if (!popAround(si, index, events)) wasted++
        refreshCannon()
    }

    // MARK: Chain

    private fun moveChain(dt: Float, events: MutableList<MarbleEvent>) {
        if (segments.isEmpty()) return
        val rear = segments.first()
        if (rollingIn && segments.last().front >= path.length * Marbles.ROLL_IN_UNTIL) rollingIn = false
        val speed = when {
            rollingIn -> Marbles.ROLL_IN_SPEED
            segments.size > 1 -> Marbles.CATCH_UP_SPEED
            else -> level.speed
        }
        rear.rear += speed * dt
        // Segments roll back to the one behind them when the colours on both sides of the gap match.
        for (k in 1 until segments.size) {
            val behind = segments[k - 1]
            val ahead = segments[k]
            if (behind.colors.isNotEmpty() && ahead.colors.isNotEmpty() && behind.colors.last() == ahead.colors.first()) {
                ahead.rear -= Marbles.CATCH_UP_SPEED * dt
            }
        }
        // Touching segments join; a join of one colour may pop again.
        var k = 0
        while (k < segments.size - 1) {
            val behind = segments[k]
            val ahead = segments[k + 1]
            if (ahead.rear - behind.front <= Marbles.DIAMETER) {
                val joint = behind.colors.size
                behind.colors += ahead.colors
                segments.removeAt(k + 1)
                if (behind.colors[joint - 1] == behind.colors[joint]) {
                    combo++
                    if (popAround(k, joint, events)) refreshCannon()
                }
            } else {
                k++
            }
        }
        // Never past the end: the front segment stops there, and everything behind stops at it.
        val front = segments.lastOrNull() ?: return
        if (front.front > stopAt) front.rear -= front.front - stopAt
        for (j in segments.size - 2 downTo 0) {
            val limit = segments[j + 1].rear - Marbles.DIAMETER
            if (segments[j].front > limit) segments[j].rear -= segments[j].front - limit
        }
    }

    /** Pops the run of one colour through [index] of segment [si] when it is long enough. */
    private fun popAround(si: Int, index: Int, events: MutableList<MarbleEvent>): Boolean {
        val seg = segments[si]
        val color = seg.colors[index]
        var a = index
        var b = index
        while (a > 0 && seg.colors[a - 1] == color) a--
        while (b < seg.colors.lastIndex && seg.colors[b + 1] == color) b++
        val count = b - a + 1
        if (count < level.match) return false
        val at = path.pointAt(seg.posOf((a + b) / 2).coerceAtLeast(0f))
        val frontPart = seg.colors.subList(b + 1, seg.colors.size).toMutableList()
        val frontRear = seg.posOf(b + 1)
        while (seg.colors.size > a) seg.colors.removeAt(seg.colors.lastIndex)
        segments.removeAt(si)
        var insertAt = si
        if (seg.colors.isNotEmpty()) segments.add(insertAt++, seg)
        if (frontPart.isNotEmpty()) segments.add(insertAt, Segment(frontPart, frontRear))
        events += MarbleEvent.Popped(count, maxOf(1, combo + 1), at)
        return true
    }

    private fun present(): Set<Int> = segments.flatMap { it.colors }.toSet()

    private fun pick(): Int {
        val colors = present().toList().sorted()
        return if (colors.isEmpty()) 0 else colors[random.nextInt(colors.size)]
    }

    /** After pops the cannon may hold a colour that is gone from the board: swap it for one that is still there. */
    private fun refreshCannon() {
        val colors = present()
        if (colors.isEmpty()) return
        if (current !in colors) current = pick()
        if (next !in colors) next = pick()
    }

    companion object {
        /**
         * The starting chain: runs one short of a pop (a single marble on the easiest level), neighbours never of
         * one colour, so every well-aimed marble pops something.
         */
        fun deal(level: MarbleLevel, random: Random): List<Int> {
            val chain = mutableListOf<Int>()
            var last = -1
            while (chain.size < level.balls) {
                val color = (0 until level.colors).filter { it != last }.let { it[random.nextInt(it.size)] }
                val run = if (level.match == 2) 1 else (1..level.match - 1).let { r -> if (random.nextInt(4) == 0) r.first else r.last }
                repeat(minOf(run, level.balls - chain.size)) { chain += color }
                last = color
            }
            return chain
        }
    }
}

/** Sizes and speeds, in board units (≈ dp on a 10" tablet) and seconds. */
object Marbles {
    const val BOARD_W = 1200f
    const val BOARD_H = 640f
    const val DIAMETER = 56f
    const val SHOT_SPEED = 1_300f
    const val ROLL_IN_SPEED = 260f
    const val CATCH_UP_SPEED = 160f

    /** The chain rolls in quickly until its front is this far along the path, then slows down. */
    const val ROLL_IN_UNTIL = 0.22f
    /** Low enough to leave room for the track, high enough for the whole cannon (radius 62) to stay on the board. */
    val CANNON = Point(600f, 572f)

    /** Stars from marbles that joined the chain without popping anything; misses never count. */
    fun stars(balls: Int, wasted: Int): Int = when {
        wasted <= balls / 4 -> 3
        wasted <= balls / 2 -> 2
        else -> 1
    }
}
