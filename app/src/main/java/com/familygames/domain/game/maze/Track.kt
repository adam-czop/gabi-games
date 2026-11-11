package com.familygames.domain.game.maze

import kotlin.math.hypot
import kotlin.math.sqrt

/** A point in board units (docs/12 M19: the board is 1200 × 640 units, about 1 dp each on a 10" tablet). */
data class Point(val x: Float, val y: Float) {
    fun distanceTo(other: Point): Float = hypot(x - other.x, y - other.y)
}

/**
 * The corridors the ball may roll in: straight segments ([edges]) between [nodes], all [width] wide.
 * A traced path is a chain of short segments; a maze is a tree of segments between cell centres.
 */
class Track(
    val nodes: List<Point>,
    val edges: List<Pair<Int, Int>>,
    val start: Int,
    val goal: Int,
    val width: Float,
) {
    val lengths: FloatArray = FloatArray(edges.size) { nodes[edges[it].first].distanceTo(nodes[edges[it].second]) }

    /** node → indices of the edges touching it. */
    val adjacency: List<List<Int>> = List(nodes.size) { mutableListOf<Int>() }.also { adj ->
        edges.forEachIndexed { i, (a, b) ->
            (adj[a] as MutableList).add(i)
            (adj[b] as MutableList).add(i)
        }
    }

    val totalLength: Float get() = lengths.sum()

    init {
        require(nodes.isNotEmpty() && edges.isNotEmpty()) { "track needs at least one corridor" }
        require(start in nodes.indices && goal in nodes.indices && start != goal) { "start and goal must be different nodes" }
    }

    fun pointOn(edge: Int, t: Float): Point {
        val (a, b) = edges[edge]
        val pa = nodes[a]
        val pb = nodes[b]
        val k = if (lengths[edge] == 0f) 0f else (t / lengths[edge]).coerceIn(0f, 1f)
        return Point(pa.x + (pb.x - pa.x) * k, pa.y + (pb.y - pa.y) * k)
    }

    /** Distance along the corridors between two nodes, or null if they are not connected. */
    fun pathLength(from: Int, to: Int): Float? = shortestFrom(from, Float.MAX_VALUE)[to].takeIf { it < Float.MAX_VALUE }

    /** Dijkstra from one node, stopping at [limit]; unreachable nodes stay at [Float.MAX_VALUE]. */
    internal fun shortestFrom(seeds: Map<Int, Float>, limit: Float): FloatArray {
        val dist = FloatArray(nodes.size) { Float.MAX_VALUE }
        val done = BooleanArray(nodes.size)
        seeds.forEach { (n, d) -> if (d < dist[n]) dist[n] = d }
        while (true) {
            var u = -1
            for (i in dist.indices) if (!done[i] && dist[i] <= limit && (u == -1 || dist[i] < dist[u])) u = i
            if (u == -1) return dist
            done[u] = true
            for (e in adjacency[u]) {
                val (a, b) = edges[e]
                val v = if (a == u) b else a
                val d = dist[u] + lengths[e]
                if (d < dist[v]) dist[v] = d
            }
        }
    }

    private fun shortestFrom(node: Int, limit: Float): FloatArray = shortestFrom(mapOf(node to 0f), limit)

    companion object {
        /** A single corridor through [points], from the first to the last. */
        fun chain(points: List<Point>, width: Float): Track =
            Track(points, (0 until points.size - 1).map { it to it + 1 }, start = 0, goal = points.size - 1, width = width)
    }
}

/** Closest point of segment a–b to p, as the distance from a, limited to [from]…[to]. */
internal fun projectOnSegment(a: Point, b: Point, p: Point, from: Float, to: Float): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len = sqrt(dx * dx + dy * dy)
    if (len == 0f) return 0f
    val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / len
    return t.coerceIn(from, to)
}
