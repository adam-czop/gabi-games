package com.familygames.domain.game.maze

/** Where the ball is: on [edge], [t] units from the edge's first node. */
data class TrackPos(val edge: Int, val t: Float)

sealed interface TraceOutcome {
    /** Nothing changed (not holding the ball, or already at the goal). */
    data object Ignored : TraceOutcome

    /** The ball followed the finger; [travelled] is the distance it rolled. */
    data class Moved(val travelled: Float) : TraceOutcome

    /** The finger left the corridor: the ball stays where it was (no penalty for the child, only counted). */
    data object Slipped : TraceOutcome

    /** The ball reached the goal. */
    data object Finished : TraceOutcome
}

/**
 * Rules of "Labirynt" (docs/12 M19). The child holds the ball and leads it along the corridors:
 * - the ball is grabbed by touching it (generous radius) and follows the finger only inside the corridor;
 * - it never jumps: each move looks only at corridor parts within [reach] (along the corridors) of the ball,
 *   so a finger crossing a wall into a neighbouring corridor does not move it;
 * - leaving the corridor drops the ball where it was and counts a slip; touching it again (even without
 *   lifting the finger) picks it up.
 */
class Tracer(val track: Track) {
    private val half = track.width / 2f
    private val reach = track.width * REACH
    private val grabRadius = track.width * GRAB

    var position: TrackPos = startPosition()
        private set
    var point: Point = track.nodes[track.start]
        private set
    var holding: Boolean = false
        private set
    var slips: Int = 0
        private set
    var finished: Boolean = false
        private set

    /** Finger down: grabs the ball if the touch is on (or right next to) it. */
    fun down(p: Point): Boolean {
        if (finished) return false
        holding = p.distanceTo(point) <= grabRadius
        return holding
    }

    fun move(p: Point): TraceOutcome {
        if (finished) return TraceOutcome.Ignored
        if (!holding) {
            // Coming back to the ball with the finger still down picks it up again.
            // Smaller than the slip distance, so a finger resting at the corridor edge does not grab and drop in a loop.
            if (p.distanceTo(point) <= track.width * REGRAB) holding = true else return TraceOutcome.Ignored
        }
        val best = closestReachable(p)
        if (best == null || best.second > half + track.width * SLACK) {
            holding = false
            slips++
            return TraceOutcome.Slipped
        }
        val before = point
        position = best.first
        point = track.pointOn(position.edge, position.t)
        if (point.distanceTo(track.nodes[track.goal]) <= track.width * GOAL) {
            point = track.nodes[track.goal]
            finished = true
            holding = false
            return TraceOutcome.Finished
        }
        return TraceOutcome.Moved(before.distanceTo(point))
    }

    fun up() {
        holding = false
    }

    /** The corridor point nearest to [p] that the ball can roll to from where it is, with its distance to [p]. */
    private fun closestReachable(p: Point): Pair<TrackPos, Float>? {
        val (a, b) = track.edges[position.edge]
        val dist = track.shortestFrom(mapOf(a to position.t, b to track.lengths[position.edge] - position.t), reach)
        var best: Pair<TrackPos, Float>? = null
        fun consider(edge: Int, from: Float, to: Float) {
            if (to < from) return
            val (ea, eb) = track.edges[edge]
            val t = projectOnSegment(track.nodes[ea], track.nodes[eb], p, from, to)
            val d = track.pointOn(edge, t).distanceTo(p)
            if (best == null || d < best!!.second) best = TrackPos(edge, t) to d
        }
        consider(position.edge, maxOf(0f, position.t - reach), minOf(track.lengths[position.edge], position.t + reach))
        track.edges.forEachIndexed { e, (ea, eb) ->
            val len = track.lengths[e]
            if (dist[ea] < reach) consider(e, 0f, minOf(len, reach - dist[ea]))
            if (dist[eb] < reach) consider(e, maxOf(0f, len - (reach - dist[eb])), len)
        }
        return best
    }

    private fun startPosition(): TrackPos {
        val edge = track.adjacency[track.start].first()
        return TrackPos(edge, if (track.edges[edge].first == track.start) 0f else track.lengths[edge])
    }

    companion object {
        /** How far (in corridor widths) the ball may roll in one finger move. */
        const val REACH = 1.2f

        /** Touches this close to the ball (in corridor widths from its centre) grab it. */
        const val GRAB = 0.9f

        /** A finger that slipped off picks the ball up again this close to it, without lifting. */
        const val REGRAB = 0.4f

        /** Extra room outside the corridor edge before the ball is dropped. */
        const val SLACK = 0.3f

        /** The goal counts as reached this close to its centre. */
        const val GOAL = 0.3f
    }
}

/** Stars for a whole game: about one slip per track is still perfect; always at least one star. */
object TraceScoring {
    fun stars(tracks: Int, slips: Int): Int = when {
        slips <= tracks -> 3
        slips <= tracks * 3 -> 2
        else -> 1
    }
}
