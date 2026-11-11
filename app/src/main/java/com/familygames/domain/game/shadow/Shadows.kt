package com.familygames.domain.game.shadow

import com.familygames.domain.game.board.DropOutcome
import com.familygames.domain.model.Photo
import kotlin.math.abs
import kotlin.random.Random

/** Levels of "Cienie" (docs/12 M21): shadows per round; the hardest one takes them all from one category. */
enum class ShadowLevel(val key: String, val items: Int, val sameCategory: Boolean, val rounds: Int = 3) {
    TWO("shadow_2", 2, sameCategory = false),
    THREE("shadow_3", 3, sameCategory = false),
    FOUR("shadow_4", 4, sameCategory = true),
    ;

    companion object {
        fun fromKey(key: String): ShadowLevel = entries.firstOrNull { it.key == key } ?: TWO
    }
}

/** One round: the shadows in their order on the board and the same pictures, shuffled, in the tray. */
data class ShadowRound(val shadows: List<Photo>, val tray: List<Photo>)

/** Deals the rounds from the built-in illustrations (only they have a clean background to cut a shadow from). */
class ShadowDealer(private val random: Random) {

    /** Empty when there are not enough illustrations for the level. */
    fun deal(groups: Map<Long, List<Photo>>, level: ShadowLevel): List<ShadowRound> {
        val usable = groups.mapValues { (_, photos) -> photos.filter { it.isBuiltIn } }.filterValues { it.isNotEmpty() }
        if (!canPlay(usable, level)) return emptyList()
        val used = mutableSetOf<Long>()
        return List(level.rounds) {
            val picked = if (level.sameCategory) fromOneCategory(usable, level.items, used) else acrossCategories(usable, level.items, used)
            used += picked.map { it.id }
            ShadowRound(picked, shuffledApart(picked))
        }
    }


    /** Different categories first, so the shapes differ a lot; fresh pictures first. */
    private fun acrossCategories(groups: Map<Long, List<Photo>>, n: Int, used: Set<Long>): List<Photo> {
        val queues = groups.values.map { photos -> photos.shuffled(random).sortedBy { it.id in used }.toMutableList() }.shuffled(random)
        val picked = mutableListOf<Photo>()
        while (picked.size < n) {
            queues.filter { it.isNotEmpty() }.forEach { q -> if (picked.size < n) picked += q.removeAt(0) }
        }
        return picked.shuffled(random)
    }

    private fun fromOneCategory(groups: Map<Long, List<Photo>>, n: Int, used: Set<Long>): List<Photo> {
        val category = groups.values.filter { it.size >= n }.maxBy { photos -> photos.count { it.id !in used } * 1000 + random.nextInt(1000) }
        return category.shuffled(random).sortedBy { it.id in used }.take(n).shuffled(random)
    }

    /** The tray never repeats the order of the shadows (a picture right under its own shadow is too easy). */
    private fun shuffledApart(shadows: List<Photo>): List<Photo> {
        if (shadows.size < 2) return shadows
        repeat(20) {
            val tray = shadows.shuffled(random)
            if (tray.indices.none { tray[it].id == shadows[it].id }) return tray
        }
        return shadows.drop(1) + shadows.first()
    }

    companion object {
        /** Enough built-in illustrations for [level] (the level tile is greyed out otherwise). */
        fun canPlay(groups: Map<Long, List<Photo>>, level: ShadowLevel): Boolean {
            val builtIn = groups.mapValues { (_, photos) -> photos.count { it.isBuiltIn } }
            return if (level.sameCategory) builtIn.values.any { it >= level.items } else builtIn.values.sum() >= level.items
        }
    }
}

/** The shadows of one round: each accepts only its own picture. */
class ShadowBoard(val shadows: List<Photo>) {
    private val placed = BooleanArray(shadows.size)
    var mistakes = 0
        private set

    val filled: List<Boolean> get() = placed.toList()

    fun drop(photoId: Long, slot: Int): DropOutcome {
        if (slot !in shadows.indices || placed[slot] || shadows.withIndex().any { (i, p) -> p.id == photoId && placed[i] }) return DropOutcome.Ignored
        if (shadows[slot].id != photoId) {
            mistakes++
            return DropOutcome.Wrong
        }
        placed[slot] = true
        return DropOutcome.Correct(won = placed.all { it })
    }

    fun slotOf(photoId: Long): Int = shadows.indexOfFirst { it.id == photoId }
}

/**
 * Cuts the shadow out of a built-in illustration (ADR-031): the drawings sit on a plain background (a flat colour, a
 * lighter disc, a faint ground ellipse) and every shape has a dark outline. A flood fill from the edges through
 * background-like pixels marks the background; everything it cannot reach is the object.
 */
object Silhouette {
    /**
     * [pixels] are ARGB, row by row. Returns true for object pixels. A pixel is background-like when every channel is
     * within [tolerance] of one of [backgrounds] (compression noise and anti-aliased edges included), or pale grey.
     *
     * The fill runs only through pixels whose whole 3 × 3 neighbourhood is background-like, so it cannot slip through
     * a gap of 1–2 px in an outline (compression leaves such gaps); one step of growing afterwards gives the edge back.
     */
    fun mask(pixels: IntArray, width: Int, height: Int, backgrounds: List<Int>, tolerance: Int = TOLERANCE): BooleanArray {
        require(pixels.size == width * height) { "pixels do not match the size" }
        val ground = (height * GROUND_FROM).toInt()
        val like = BooleanArray(pixels.size) { isBackground(pixels[it], backgrounds, tolerance, onGround = it / width >= ground) }
        val core = BooleanArray(pixels.size) { i ->
            val x = i % width
            val y = i / width
            like[i] && (-1..1).all { dy -> (-1..1).all { dx -> like.at(x + dx, y + dy, width, height) } }
        }
        val reached = BooleanArray(pixels.size)
        val stack = IntArray(pixels.size)
        var top = 0
        fun push(i: Int) {
            if (!reached[i] && core[i]) {
                reached[i] = true
                stack[top++] = i
            }
        }
        for (x in 0 until width) {
            push(x)
            push((height - 1) * width + x)
        }
        for (y in 0 until height) {
            push(y * width)
            push(y * width + width - 1)
        }
        while (top > 0) {
            val i = stack[--top]
            val x = i % width
            val y = i / width
            if (x > 0) push(i - 1)
            if (x < width - 1) push(i + 1)
            if (y > 0) push(i - width)
            if (y < height - 1) push(i + width)
        }
        return BooleanArray(pixels.size) { i ->
            val x = i % width
            val y = i / width
            val background = reached[i] || (like[i] && (-1..1).any { dy -> (-1..1).any { dx -> reached.at(x + dx, y + dy, width, height) } })
            !background
        }
    }

    /** Outside the picture counts as background, so the edges of the drawing are never cut off. */
    private fun BooleanArray.at(x: Int, y: Int, width: Int, height: Int): Boolean =
        x !in 0 until width || y !in 0 until height || this[y * width + x]

    private fun isBackground(argb: Int, backgrounds: List<Int>, tolerance: Int, onGround: Boolean): Boolean =
        !isNearWhite(argb) && ((onGround && isPaleGrey(argb)) || backgrounds.any { bg -> isNear(argb, bg, tolerance) })

    private fun isNear(argb: Int, bg: Int, tolerance: Int): Boolean =
        abs((argb shr 16 and 0xFF) - (bg shr 16 and 0xFF)) <= tolerance &&
            abs((argb shr 8 and 0xFF) - (bg shr 8 and 0xFF)) <= tolerance &&
            abs((argb and 0xFF) - (bg and 0xFF)) <= tolerance

    /**
     * White and cream fills (a duck, a cow's face) are never background, even next to a tiny gap in the outline left
     * by compression; the backgrounds themselves are never brighter than #F3.
     */
    private fun isNearWhite(argb: Int): Boolean = minOf(argb shr 16 and 0xFF, argb shr 8 and 0xFF, argb and 0xFF) >= NEAR_WHITE

    /**
     * Light, nearly colourless pixels on the ground band are scenery too: the road under the vehicles, the faint
     * ground ellipse and the soft edges between them. Higher up a pale grey may be part of the object (a cow's spot).
     */
    private fun isPaleGrey(argb: Int): Boolean {
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        return minOf(r, g, b) >= PALE_MIN && maxOf(r, g, b) - minOf(r, g, b) <= PALE_CHROMA
    }

    /** Background colours of the built-in categories (`tools/builtin_art.py`), by the asset name prefix. */
    fun backgroundsFor(assetPath: String): List<Int> {
        val (bg, disc) = BUILTIN[assetPath.substringAfterLast('/').substringBefore('_')] ?: return emptyList()
        return listOf(bg, disc, blend(disc, INK, 0.06f), blend(bg, INK, 0.06f))
    }

    private fun blend(base: Int, over: Int, alpha: Float): Int {
        fun ch(shift: Int) = (((base shr shift) and 0xFF) * (1 - alpha) + ((over shr shift) and 0xFF) * alpha).toInt() and 0xFF
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    const val TOLERANCE = 18
    private const val INK = 0xFF3B3936.toInt()

    private const val PALE_MIN = 130
    private const val PALE_CHROMA = 24

    /** The road and the ground ellipse lie in the lowest part of the drawings (y ≥ 314 of 400). */
    private const val GROUND_FROM = 0.72f
    private const val NEAR_WHITE = 0xF7

    private val BUILTIN = mapOf(
        "dom" to (0xFFF1EDE7.toInt() to 0xFFE9E2D8.toInt()),
        "zwierzeta" to (0xFFEEF1EC.toInt() to 0xFFE2E9E0.toInt()),
        "zabawki" to (0xFFEDF0F1.toInt() to 0xFFE0E7EA.toInt()),
        "pojazdy" to (0xFFF3F0E8.toInt() to 0xFFE9E3D6.toInt()),
    )
}
