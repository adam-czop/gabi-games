package com.familygames.domain.game.coloring

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A closed area of a colouring page: a polygon in page units (1000 × 750). */
class Region(val xs: FloatArray, val ys: FloatArray) {
    /** Even-odd ray casting. */
    fun contains(x: Float, y: Float): Boolean {
        var inside = false
        var j = xs.size - 1
        for (i in xs.indices) {
            if ((ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) inside = !inside
            j = i
        }
        return inside
    }
}

/** Built-in outline (docs/12 M18): regions back to front, plus decorative lines that are only drawn. */
class ColoringPage(val key: String, val regions: List<Region>, val lines: List<Region> = emptyList()) {
    /** The front-most region under the point, or null for the background. */
    fun regionAt(x: Float, y: Float): Int? = regions.indices.lastOrNull { regions[it].contains(x, y) }

    companion object {
        const val WIDTH = 1000f
        const val HEIGHT = 750f
    }
}

private fun poly(vararg p: Float): Region = Region(FloatArray(p.size / 2) { p[it * 2] }, FloatArray(p.size / 2) { p[it * 2 + 1] })

private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, deg: Float = 0f, n: Int = 48): Region {
    val a = deg * PI.toFloat() / 180f
    val xs = FloatArray(n)
    val ys = FloatArray(n)
    for (i in 0 until n) {
        val t = i * 2 * PI.toFloat() / n
        val x = rx * cos(t)
        val y = ry * sin(t)
        xs[i] = cx + x * cos(a) - y * sin(a)
        ys[i] = cy + x * sin(a) + y * cos(a)
    }
    return Region(xs, ys)
}

private fun circle(cx: Float, cy: Float, r: Float) = ellipse(cx, cy, r, r)

private fun rect(x: Float, y: Float, w: Float, h: Float) = poly(x, y, x + w, y, x + w, y + h, x, y + h)

/** Open polyline for decorations (whiskers, strings); drawn, never filled. */
private fun line(vararg p: Float) = poly(*p)

/** Twelve simple pictures with thick closed outlines (docs/12 M18). */
object ColoringPages {
    val all: List<ColoringPage> by lazy {
        listOf(
            ColoringPage(
                "house",
                listOf(
                    rect(620f, 170f, 60f, 130f),
                    rect(250f, 330f, 500f, 330f),
                    poly(210f, 345f, 500f, 130f, 790f, 345f),
                    rect(450f, 480f, 100f, 180f),
                    rect(300f, 400f, 110f, 100f),
                    rect(590f, 400f, 110f, 100f),
                ),
            ),
            ColoringPage(
                "cat",
                listOf(
                    ellipse(705f, 575f, 130f, 34f, -30f),
                    ellipse(500f, 530f, 170f, 150f),
                    poly(400f, 240f, 415f, 115f, 485f, 195f),
                    poly(600f, 240f, 585f, 115f, 515f, 195f),
                    circle(500f, 310f, 120f),
                    circle(455f, 290f, 16f),
                    circle(545f, 290f, 16f),
                    poly(482f, 330f, 518f, 330f, 500f, 350f),
                ),
                listOf(line(380f, 340f, 455f, 345f), line(380f, 370f, 455f, 358f), line(620f, 340f, 545f, 345f), line(620f, 370f, 545f, 358f)),
            ),
            ColoringPage(
                "dog",
                listOf(
                    ellipse(790f, 410f, 62f, 22f, -40f),
                    rect(430f, 540f, 55f, 150f),
                    rect(640f, 540f, 55f, 150f),
                    ellipse(560f, 500f, 210f, 120f),
                    circle(360f, 330f, 115f),
                    ellipse(262f, 345f, 40f, 88f, 15f),
                    ellipse(458f, 345f, 40f, 88f, -15f),
                    ellipse(360f, 392f, 58f, 42f),
                    circle(360f, 370f, 17f),
                    circle(320f, 305f, 13f),
                    circle(400f, 305f, 13f),
                ),
            ),
            ColoringPage(
                "car",
                listOf(
                    poly(330f, 320f, 640f, 320f, 730f, 440f, 250f, 440f),
                    rect(140f, 430f, 720f, 150f),
                    poly(350f, 342f, 480f, 342f, 480f, 432f, 295f, 432f),
                    poly(500f, 342f, 628f, 342f, 695f, 432f, 500f, 432f),
                    circle(300f, 590f, 78f),
                    circle(700f, 590f, 78f),
                    circle(300f, 590f, 32f),
                    circle(700f, 590f, 32f),
                    rect(825f, 455f, 35f, 45f),
                ),
            ),
            ColoringPage(
                "flower",
                buildList {
                    add(rect(490f, 400f, 22f, 300f))
                    add(ellipse(590f, 585f, 85f, 32f, -30f))
                    add(ellipse(410f, 625f, 85f, 32f, 30f))
                    for (i in 0 until 6) {
                        val a = i * PI.toFloat() / 3 - PI.toFloat() / 2
                        add(circle(500f + 108f * cos(a), 290f + 108f * sin(a), 70f))
                    }
                    add(circle(500f, 290f, 66f))
                },
            ),
            ColoringPage(
                "sun",
                buildList {
                    for (i in 0 until 8) {
                        val a = i * PI.toFloat() / 4
                        val d = 0.21f
                        add(
                            poly(
                                500f + 165f * cos(a - d), 375f + 165f * sin(a - d),
                                500f + 300f * cos(a), 375f + 300f * sin(a),
                                500f + 165f * cos(a + d), 375f + 165f * sin(a + d),
                            ),
                        )
                    }
                    add(circle(500f, 375f, 160f))
                    add(circle(445f, 335f, 18f))
                    add(circle(555f, 335f, 18f))
                },
                listOf(line(430f, 420f, 465f, 450f, 500f, 460f, 535f, 450f, 570f, 420f)),
            ),
            ColoringPage(
                "fish",
                listOf(
                    poly(380f, 250f, 500f, 160f, 580f, 250f),
                    poly(420f, 500f, 520f, 590f, 560f, 500f),
                    poly(690f, 375f, 870f, 255f, 870f, 495f),
                    ellipse(460f, 375f, 260f, 150f),
                    circle(320f, 340f, 26f),
                    ellipse(520f, 375f, 50f, 95f),
                ),
                listOf(line(400f, 290f, 430f, 375f, 400f, 460f)),
            ),
            ColoringPage(
                "butterfly",
                listOf(
                    ellipse(375f, 285f, 145f, 110f, -25f),
                    ellipse(625f, 285f, 145f, 110f, 25f),
                    ellipse(400f, 490f, 105f, 82f, 25f),
                    ellipse(600f, 490f, 105f, 82f, -25f),
                    circle(360f, 285f, 42f),
                    circle(640f, 285f, 42f),
                    ellipse(500f, 400f, 30f, 175f),
                    circle(500f, 210f, 40f),
                ),
                listOf(line(490f, 175f, 455f, 100f, 430f, 90f), line(510f, 175f, 545f, 100f, 570f, 90f)),
            ),
            ColoringPage(
                "tree",
                listOf(
                    rect(450f, 420f, 100f, 280f),
                    circle(390f, 340f, 135f),
                    circle(610f, 340f, 135f),
                    circle(500f, 225f, 145f),
                    circle(385f, 370f, 28f),
                    circle(585f, 270f, 28f),
                    circle(625f, 410f, 28f),
                    circle(470f, 190f, 28f),
                ),
            ),
            ColoringPage(
                "teddy",
                listOf(
                    ellipse(325f, 500f, 52f, 105f, 25f),
                    ellipse(675f, 500f, 52f, 105f, -25f),
                    ellipse(500f, 540f, 172f, 165f),
                    ellipse(500f, 565f, 95f, 100f),
                    circle(395f, 690f, 55f),
                    circle(605f, 690f, 55f),
                    circle(370f, 160f, 56f),
                    circle(630f, 160f, 56f),
                    circle(500f, 255f, 140f),
                    ellipse(500f, 310f, 62f, 46f),
                    ellipse(500f, 292f, 23f, 15f),
                    circle(450f, 225f, 15f),
                    circle(550f, 225f, 15f),
                ),
            ),
            ColoringPage(
                "balloons",
                listOf(
                    ellipse(350f, 280f, 100f, 125f, -10f),
                    ellipse(660f, 290f, 100f, 125f, 10f),
                    ellipse(505f, 220f, 108f, 132f),
                    poly(355f, 402f, 340f, 428f, 372f, 428f),
                    poly(652f, 412f, 640f, 438f, 672f, 436f),
                    poly(505f, 350f, 490f, 376f, 520f, 376f),
                ),
                listOf(line(356f, 428f, 420f, 560f, 505f, 690f), line(656f, 436f, 600f, 560f, 505f, 690f), line(505f, 376f, 490f, 520f, 505f, 690f)),
            ),
            ColoringPage(
                "rocket",
                listOf(
                    poly(445f, 600f, 555f, 600f, 500f, 725f),
                    poly(400f, 460f, 320f, 615f, 410f, 600f),
                    poly(600f, 460f, 680f, 615f, 590f, 600f),
                    rect(400f, 300f, 200f, 300f),
                    poly(400f, 302f, 500f, 110f, 600f, 302f),
                    circle(500f, 395f, 56f),
                ),
            ),
        )
    }
}
