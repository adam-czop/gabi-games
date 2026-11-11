package com.familygames.domain.game.find

import kotlin.math.hypot

// MARK: Shapes

/** A piece of a scene drawing in board units (1200 × 640); colours are ARGB. Outlined pieces get the Ink line. */
sealed interface SceneShape {
    val fill: Int?
    val outline: Boolean

    /** Is ([x], [y]) on the shape or within [pad] units of it? */
    fun hit(x: Float, y: Float, pad: Float): Boolean

    /** left, top, right, bottom. */
    fun bounds(): FloatArray

    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val radius: Float, override val fill: Int?, override val outline: Boolean) : SceneShape {
        override fun hit(x: Float, y: Float, pad: Float) = x in this.x - pad..this.x + w + pad && y in this.y - pad..this.y + h + pad
        override fun bounds() = floatArrayOf(x, y, x + w, y + h)
    }

    data class Oval(val cx: Float, val cy: Float, val rx: Float, val ry: Float, override val fill: Int?, override val outline: Boolean) : SceneShape {
        override fun hit(x: Float, y: Float, pad: Float): Boolean {
            val dx = (x - cx) / (rx + pad)
            val dy = (y - cy) / (ry + pad)
            return dx * dx + dy * dy <= 1f
        }

        override fun bounds() = floatArrayOf(cx - rx, cy - ry, cx + rx, cy + ry)
    }

    class Poly(val xs: FloatArray, val ys: FloatArray, override val fill: Int?, override val outline: Boolean) : SceneShape {
        override fun hit(x: Float, y: Float, pad: Float): Boolean {
            var inside = false
            var j = xs.size - 1
            for (i in xs.indices) {
                if ((ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) inside = !inside
                j = i
            }
            return inside || (pad > 0f && xs.indices.any { i -> segmentDistance(x, y, xs[i], ys[i], xs[(i + 1) % xs.size], ys[(i + 1) % xs.size]) <= pad })
        }

        override fun bounds() = floatArrayOf(xs.min(), ys.min(), xs.max(), ys.max())
    }

    /** An open line (rays, ropes, legs): never filled. */
    class Line(val xs: FloatArray, val ys: FloatArray, val width: Float, val color: Int) : SceneShape {
        override val fill: Int? = null
        override val outline: Boolean = false
        override fun hit(x: Float, y: Float, pad: Float) =
            (0 until xs.size - 1).any { i -> segmentDistance(x, y, xs[i], ys[i], xs[i + 1], ys[i + 1]) <= width / 2 + pad }

        override fun bounds() = floatArrayOf(xs.min() - width / 2, ys.min() - width / 2, xs.max() + width / 2, ys.max() + width / 2)
    }
}

internal fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    val len2 = dx * dx + dy * dy
    val t = if (len2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}

/** Something the child can find: [key] maps to its Polish name and question in the strings. */
class SceneObject(val key: String, val big: Boolean, val shapes: List<SceneShape>) {
    fun hit(x: Float, y: Float, pad: Float): Boolean = shapes.any { it.hit(x, y, pad) }

    val bounds: FloatArray = shapes.map { it.bounds() }.let { b ->
        floatArrayOf(b.minOf { it[0] }, b.minOf { it[1] }, b.maxOf { it[2] }, b.maxOf { it[3] })
    }
}

/** A built-in picture: scenery drawn first, then the objects back to front. */
class Scene(val key: String, val background: List<SceneShape>, val objects: List<SceneObject>) {
    /** The front-most object under the finger; a near miss (within [NEAR] units) still counts for small fingers. */
    fun objectAt(x: Float, y: Float): SceneObject? =
        objects.lastOrNull { it.hit(x, y, 0f) } ?: objects.lastOrNull { it.hit(x, y, NEAR) }

    companion object {
        const val WIDTH = 1200f
        const val HEIGHT = 640f
        const val NEAR = 18f
    }
}

// MARK: Builder

private class ShapesBuilder {
    val shapes = mutableListOf<SceneShape>()
    fun rect(x: Float, y: Float, w: Float, h: Float, fill: Int?, r: Float = 0f, outline: Boolean = true) {
        shapes += SceneShape.Rect(x, y, w, h, r, fill, outline)
    }
    fun oval(cx: Float, cy: Float, rx: Float, ry: Float, fill: Int?, outline: Boolean = true) {
        shapes += SceneShape.Oval(cx, cy, rx, ry, fill, outline)
    }
    fun circle(cx: Float, cy: Float, r: Float, fill: Int?, outline: Boolean = true) = oval(cx, cy, r, r, fill, outline)
    fun poly(vararg p: Float, fill: Int?, outline: Boolean = true) {
        shapes += SceneShape.Poly(FloatArray(p.size / 2) { p[it * 2] }, FloatArray(p.size / 2) { p[it * 2 + 1] }, fill, outline)
    }
    fun line(vararg p: Float, width: Float = 6f, color: Int = C.INK) {
        shapes += SceneShape.Line(FloatArray(p.size / 2) { p[it * 2] }, FloatArray(p.size / 2) { p[it * 2 + 1] }, width, color)
    }
}

private class SceneBuilder(val key: String) {
    val background = mutableListOf<SceneShape>()
    val objects = mutableListOf<SceneObject>()
    fun scenery(block: ShapesBuilder.() -> Unit) {
        background += ShapesBuilder().apply(block).shapes
    }
    fun obj(key: String, big: Boolean, block: ShapesBuilder.() -> Unit) {
        objects += SceneObject(key, big, ShapesBuilder().apply(block).shapes)
    }
}

private fun scene(key: String, block: SceneBuilder.() -> Unit): Scene = SceneBuilder(key).apply(block).let { Scene(it.key, it.background, it.objects) }

/** Muted crayon colours of the built-in art (tools/builtin_art.py), so the scenes match the illustrations. */
private object C {
    const val INK = 0xFF3B3936.toInt()
    const val SKY = 0xFFE6ECF0.toInt()
    const val GRASS = 0xFFCFE0C9.toInt()
    const val GRASS_D = 0xFFB8D0B0.toInt()
    const val SAGE = 0xFFA9C2B4.toInt()
    const val SAGE_D = 0xFF7E9C8C.toInt()
    const val MIST = 0xFFA9BCCB.toInt()
    const val MIST_L = 0xFFD3E0E8.toInt()
    const val MIST_D = 0xFF7F95A6.toInt()
    const val HONEY = 0xFFE6C98F.toInt()
    const val HONEY_L = 0xFFF2E3C2.toInt()
    const val TERRA = 0xFFDDA38C.toInt()
    const val TERRA_D = 0xFFC9826A.toInt()
    const val ROSE = 0xFFEDBFB8.toInt()
    const val PINK = 0xFFF2CFC8.toInt()
    const val WOOD = 0xFFC9A27E.toInt()
    const val WOOD_L = 0xFFE2C9AC.toInt()
    const val WOOD_D = 0xFFA9825F.toInt()
    const val CREAM = 0xFFFFF8EC.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val GREY = 0xFFD8D1C7.toInt()
    const val GREY_D = 0xFF9C958B.toInt()
    const val TYRE = 0xFF5A5651.toInt()
    const val WALL = 0xFFF4EEE4.toInt()
}

// MARK: Scenes

/** M23 · "Znajdź na obrazku": four built-in scenes (ADR-033). */
object Scenes {
    val all: List<Scene> by lazy { listOf(farm(), city(), kitchen(), playground()) }

    fun byKey(key: String): Scene? = all.firstOrNull { it.key == key }

    private fun farm() = scene("farm") {
        scenery {
            rect(0f, 0f, 1200f, 400f, C.SKY, outline = false)
            oval(300f, 420f, 520f, 120f, C.GRASS_D, outline = false)
            rect(0f, 380f, 1200f, 260f, C.GRASS, outline = false)
            line(20f, 470f, 1180f, 470f, width = 6f, color = C.WOOD_D)
            for (x in 60..1140 step 90) line(x.toFloat(), 440f, x.toFloat(), 500f, width = 10f, color = C.WOOD_D)
        }
        obj("sun", big = true) {
            for (a in 0 until 8) {
                val ang = a * Math.PI / 4
                line(1080f + 66f * kotlin.math.cos(ang).toFloat(), 96f + 66f * kotlin.math.sin(ang).toFloat(), 1080f + 92f * kotlin.math.cos(ang).toFloat(), 96f + 92f * kotlin.math.sin(ang).toFloat(), width = 7f, color = C.HONEY)
            }
            circle(1080f, 96f, 52f, C.HONEY)
        }
        obj("barn", big = true) {
            rect(80f, 190f, 260f, 210f, C.TERRA)
            poly(62f, 196f, 210f, 96f, 358f, 196f, fill = C.TERRA_D)
            rect(165f, 290f, 90f, 110f, C.CREAM)
            line(165f, 290f, 255f, 400f, width = 6f)
            line(255f, 290f, 165f, 400f, width = 6f)
        }
        obj("tree", big = true) {
            rect(424f, 240f, 40f, 160f, C.WOOD)
            circle(400f, 210f, 60f, C.SAGE)
            circle(490f, 205f, 60f, C.SAGE)
            circle(444f, 150f, 70f, C.SAGE)
        }
        obj("tractor", big = true) {
            rect(900f, 320f, 170f, 80f, C.SAGE_D, r = 10f)
            rect(960f, 240f, 80f, 84f, C.MIST_L, r = 8f)
            rect(1060f, 290f, 14f, 40f, C.GREY_D)
            circle(950f, 420f, 52f, C.TYRE)
            circle(950f, 420f, 20f, C.HONEY)
            circle(1060f, 428f, 34f, C.TYRE)
            circle(1060f, 428f, 13f, C.HONEY)
        }
        obj("cow", big = true) {
            rect(570f, 450f, 18f, 70f, C.WHITE)
            rect(690f, 450f, 18f, 70f, C.WHITE)
            oval(640f, 430f, 110f, 58f, C.WHITE)
            oval(600f, 410f, 28f, 22f, C.INK, outline = false)
            oval(670f, 450f, 24f, 16f, C.INK, outline = false)
            line(752f, 365f, 742f, 345f, width = 8f, color = C.HONEY)
            line(792f, 365f, 802f, 345f, width = 8f, color = C.HONEY)
            oval(772f, 395f, 42f, 38f, C.WHITE)
            oval(772f, 420f, 30f, 18f, C.ROSE)
            circle(758f, 388f, 5f, C.INK, outline = false)
            circle(786f, 388f, 5f, C.INK, outline = false)
        }
        obj("pig", big = false) {
            oval(300f, 560f, 64f, 42f, C.PINK)
            poly(330f, 510f, 344f, 486f, 356f, 514f, fill = C.ROSE)
            poly(372f, 512f, 392f, 490f, 398f, 520f, fill = C.ROSE)
            circle(372f, 540f, 36f, C.PINK)
            oval(394f, 548f, 15f, 11f, C.ROSE)
            circle(362f, 530f, 4f, C.INK, outline = false)
        }
        obj("chicken", big = false) {
            line(500f, 590f, 500f, 612f, width = 5f, color = C.HONEY)
            line(520f, 590f, 520f, 612f, width = 5f, color = C.HONEY)
            oval(510f, 568f, 40f, 28f, C.WHITE)
            circle(544f, 532f, 18f, C.WHITE)
            poly(536f, 514f, 544f, 500f, 552f, 514f, fill = C.TERRA)
            poly(560f, 528f, 578f, 534f, 560f, 540f, fill = C.HONEY)
            circle(548f, 528f, 3.5f, C.INK, outline = false)
        }
        obj("sheep", big = false) {
            rect(1040f, 560f, 12f, 40f, C.INK, outline = false)
            rect(1100f, 560f, 12f, 40f, C.INK, outline = false)
            for ((x, y) in listOf(1030f to 530f, 1070f to 515f, 1110f to 530f, 1050f to 560f, 1095f to 560f)) circle(x, y, 32f, C.WHITE)
            oval(1150f, 525f, 24f, 30f, C.GREY_D)
        }
        obj("flower", big = false) {
            line(170f, 600f, 170f, 540f, width = 6f, color = C.SAGE_D)
            for ((dx, dy) in listOf(0f to -18f, 18f to 0f, 0f to 18f, -18f to 0f)) circle(170f + dx, 530f + dy, 14f, C.ROSE)
            circle(170f, 530f, 10f, C.HONEY)
        }
    }

    private fun city() = scene("city") {
        scenery {
            rect(0f, 0f, 1200f, 410f, C.SKY, outline = false)
            rect(0f, 400f, 1200f, 70f, C.GREY, outline = false)
            rect(0f, 470f, 1200f, 170f, C.GREY_D, outline = false)
            for (x in 40..1160 step 140) rect(x.toFloat(), 548f, 70f, 12f, C.WHITE, outline = false)
        }
        obj("cloud", big = true) {
            circle(880f, 100f, 46f, C.WHITE)
            circle(940f, 80f, 58f, C.WHITE)
            circle(1000f, 104f, 42f, C.WHITE)
            rect(870f, 104f, 140f, 42f, C.WHITE, r = 20f, outline = false)
        }
        obj("block", big = true) {
            rect(60f, 110f, 210f, 300f, C.MIST)
            for (r in 0 until 4) for (c in 0 until 3) rect(80f + c * 64f, 130f + r * 64f, 42f, 42f, C.CREAM)
        }
        obj("house", big = true) {
            rect(310f, 250f, 180f, 160f, C.HONEY)
            poly(292f, 256f, 400f, 166f, 508f, 256f, fill = C.TERRA)
            rect(375f, 330f, 50f, 80f, C.WOOD)
            rect(328f, 280f, 40f, 40f, C.MIST_L)
        }
        obj("tree", big = true) {
            rect(578f, 290f, 30f, 120f, C.WOOD)
            circle(593f, 250f, 66f, C.SAGE)
        }
        obj("lights", big = false) {
            rect(712f, 250f, 14f, 160f, C.GREY_D)
            rect(690f, 160f, 58f, 120f, C.INK, r = 12f)
            circle(719f, 188f, 14f, C.TERRA, outline = false)
            circle(719f, 220f, 14f, C.HONEY, outline = false)
            circle(719f, 252f, 14f, C.SAGE, outline = false)
        }
        obj("bus", big = true) {
            rect(780f, 450f, 330f, 130f, C.HONEY, r = 24f)
            for (c in 0 until 4) rect(800f + c * 70f, 470f, 54f, 46f, C.MIST_L, r = 6f)
            rect(1080f, 470f, 22f, 70f, C.MIST_L, r = 4f)
            circle(850f, 585f, 30f, C.TYRE)
            circle(1040f, 585f, 30f, C.TYRE)
        }
        obj("car", big = true) {
            poly(250f, 520f, 290f, 480f, 390f, 480f, 430f, 520f, fill = C.MIST_L)
            rect(200f, 515f, 260f, 64f, C.TERRA, r = 22f)
            circle(260f, 585f, 28f, C.TYRE)
            circle(400f, 585f, 28f, C.TYRE)
        }
        obj("bike", big = false) {
            circle(540f, 440f, 26f, null)
            circle(620f, 440f, 26f, null)
            line(540f, 440f, 570f, 405f, 610f, 405f, 620f, 440f, width = 6f, color = C.TERRA_D)
            line(570f, 405f, 585f, 440f, 540f, 440f, width = 6f, color = C.TERRA_D)
            line(566f, 396f, 584f, 396f, width = 7f)
            line(606f, 405f, 612f, 390f, width = 6f)
        }
        obj("dog", big = false) {
            rect(1090f, 425f, 10f, 30f, C.WOOD_D, outline = false)
            rect(1122f, 425f, 10f, 30f, C.WOOD_D, outline = false)
            oval(1110f, 420f, 34f, 20f, C.WOOD)
            circle(1078f, 400f, 18f, C.WOOD)
            oval(1068f, 398f, 7f, 14f, C.WOOD_D)
            line(1142f, 414f, 1154f, 398f, width = 6f, color = C.WOOD_D)
        }
    }

    private fun kitchen() = scene("kitchen") {
        scenery {
            rect(0f, 0f, 1200f, 480f, C.WALL, outline = false)
            rect(0f, 470f, 1200f, 170f, C.WOOD_L, outline = false)
            line(0f, 472f, 1200f, 472f, width = 5f, color = C.WOOD_D)
        }
        obj("window", big = true) {
            rect(500f, 50f, 220f, 160f, C.MIST_L)
            line(610f, 50f, 610f, 210f, width = 7f)
            line(500f, 130f, 720f, 130f, width = 7f)
        }
        obj("fridge", big = true) {
            rect(60f, 100f, 190f, 400f, C.WHITE, r = 18f)
            line(60f, 250f, 250f, 250f, width = 5f)
            line(222f, 150f, 222f, 210f, width = 8f, color = C.GREY_D)
            line(222f, 290f, 222f, 370f, width = 8f, color = C.GREY_D)
        }
        obj("clock", big = false) {
            circle(900f, 110f, 46f, C.WHITE)
            line(900f, 110f, 900f, 80f, width = 6f)
            line(900f, 110f, 922f, 120f, width = 6f)
        }
        obj("table", big = true) {
            rect(380f, 360f, 16f, 160f, C.WOOD_D)
            rect(724f, 360f, 16f, 160f, C.WOOD_D)
            rect(350f, 336f, 420f, 28f, C.WOOD, r = 6f)
        }
        obj("chair", big = true) {
            rect(272f, 230f, 18f, 190f, C.WOOD_D)
            rect(272f, 230f, 90f, 22f, C.WOOD_D, r = 6f)
            rect(272f, 404f, 120f, 18f, C.WOOD, r = 6f)
            rect(278f, 420f, 14f, 100f, C.WOOD_D)
            rect(372f, 420f, 14f, 100f, C.WOOD_D)
        }
        obj("cup", big = false) {
            circle(498f, 314f, 16f, null)
            rect(440f, 288f, 52f, 48f, C.TERRA, r = 8f)
        }
        obj("apple", big = false) {
            circle(640f, 308f, 28f, C.TERRA_D)
            line(640f, 282f, 646f, 266f, width = 5f, color = C.WOOD_D)
            oval(658f, 270f, 12f, 7f, C.SAGE)
        }
        obj("stove", big = true) {
            rect(900f, 290f, 230f, 200f, C.GREY, r = 10f)
            rect(930f, 360f, 170f, 100f, C.INK, r = 8f)
            circle(950f, 315f, 10f, C.INK, outline = false)
            circle(1080f, 315f, 10f, C.INK, outline = false)
        }
        obj("pot", big = false) {
            line(942f, 252f, 920f, 252f, width = 9f)
            line(1068f, 252f, 1090f, 252f, width = 9f)
            rect(940f, 226f, 130f, 64f, C.MIST_D, r = 10f)
            rect(930f, 216f, 150f, 14f, C.MIST, r = 6f)
        }
        obj("cat", big = false) {
            oval(820f, 580f, 54f, 30f, C.HONEY)
            circle(770f, 550f, 26f, C.HONEY)
            poly(752f, 534f, 756f, 508f, 772f, 528f, fill = C.HONEY)
            poly(780f, 528f, 794f, 508f, 794f, 536f, fill = C.HONEY)
            line(872f, 576f, 900f, 540f, width = 8f, color = C.WOOD_D)
            circle(762f, 548f, 3.5f, C.INK, outline = false)
            circle(780f, 548f, 3.5f, C.INK, outline = false)
        }
    }

    private fun playground() = scene("playground") {
        scenery {
            rect(0f, 0f, 1200f, 380f, C.SKY, outline = false)
            rect(0f, 370f, 1200f, 270f, C.GRASS, outline = false)
        }
        obj("sun", big = true) {
            for (a in 0 until 8) {
                val ang = a * Math.PI / 4
                line(120f + 62f * kotlin.math.cos(ang).toFloat(), 90f + 62f * kotlin.math.sin(ang).toFloat(), 120f + 86f * kotlin.math.cos(ang).toFloat(), 90f + 86f * kotlin.math.sin(ang).toFloat(), width = 7f, color = C.HONEY)
            }
            circle(120f, 90f, 48f, C.HONEY)
        }
        obj("kite", big = false) {
            line(960f, 170f, 940f, 220f, 970f, 260f, 945f, 300f, width = 4f)
            poly(960f, 70f, 1010f, 120f, 960f, 170f, 910f, 120f, fill = C.TERRA)
            line(960f, 70f, 960f, 170f, width = 4f)
            line(910f, 120f, 1010f, 120f, width = 4f)
        }
        obj("slide", big = true) {
            line(120f, 560f, 150f, 260f, width = 10f, color = C.MIST_D)
            line(180f, 560f, 210f, 260f, width = 10f, color = C.MIST_D)
            for (y in 300..520 step 55) line(126f + (560 - y) / 10f, y.toFloat(), 186f + (560 - y) / 10f, y.toFloat(), width = 7f, color = C.MIST_D)
            rect(140f, 250f, 90f, 22f, C.MIST, r = 6f)
            poly(220f, 250f, 250f, 262f, 430f, 560f, 400f, 576f, fill = C.TERRA)
        }
        obj("swing", big = true) {
            line(500f, 580f, 560f, 230f, 800f, 230f, 860f, 580f, width = 12f, color = C.WOOD_D)
            line(640f, 236f, 640f, 470f, width = 4f)
            line(720f, 236f, 720f, 470f, width = 4f)
            rect(620f, 466f, 120f, 18f, C.HONEY, r = 6f)
        }
        obj("bench", big = true) {
            rect(930f, 330f, 220f, 18f, C.WOOD, r = 6f)
            rect(930f, 290f, 220f, 16f, C.WOOD, r = 6f)
            rect(950f, 346f, 14f, 50f, C.WOOD_D)
            rect(1116f, 346f, 14f, 50f, C.WOOD_D)
        }
        obj("sandbox", big = true) {
            rect(860f, 470f, 300f, 120f, C.HONEY_L, r = 10f)
            rect(860f, 470f, 300f, 18f, C.WOOD, r = 6f)
        }
        obj("bucket", big = false) {
            line(926f, 470f, 950f, 436f, 990f, 436f, 1012f, 470f, width = 5f)
            poly(920f, 470f, 1018f, 470f, 1006f, 540f, 932f, 540f, fill = C.MIST)
        }
        obj("ball", big = false) {
            circle(560f, 580f, 36f, C.MIST_L)
            line(524f, 580f, 596f, 580f, width = 6f, color = C.TERRA)
            line(560f, 544f, 560f, 616f, width = 6f, color = C.TERRA)
        }
    }
}
