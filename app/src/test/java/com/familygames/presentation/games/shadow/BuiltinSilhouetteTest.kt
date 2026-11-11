package com.familygames.presentation.games.shadow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.familygames.domain.game.shadow.Silhouette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * ADR-031: the shadows are cut from the real built-in illustrations at run time, so check every one of them.
 * Also writes `build/reports/shadows-sheet.png` (picture | shadow) for a visual check.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BuiltinSilhouetteTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `every built-in illustration gives a shadow that is the object, not the frame`() {
        val names = context.assets.list(DIR).orEmpty().filter { it.endsWith(".webp") }.sorted()
        assertEquals(32, names.size)
        val cell = 200
        val sheet = Bitmap.createBitmap(cell * 2 * 4, cell * (names.size + 3) / 4, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet).apply { drawColor(Color.WHITE) }
        val problems = mutableListOf<String>()
        names.forEachIndexed { index, name ->
            val bitmap = context.assets.open("$DIR/$name").use { BitmapFactory.decodeStream(it) }
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
            val mask = Silhouette.mask(pixels, w, h, Silhouette.backgroundsFor("$DIR/$name"))

            val share = mask.count { it } / mask.size.toFloat()
            if (share !in 0.08f..0.7f) problems += "$name covers $share of the picture"
            val border = (0 until w).flatMap { listOf(it, (h - 1) * w + it) } + (0 until h).flatMap { listOf(it * w, it * w + w - 1) }
            if (border.any { mask[it] }) problems += "$name touches the edge"
            // The object fills a good part of the drawing both ways (not a few specks left by noise).
            val xs = mask.indices.filter { mask[it] }.map { it % w }
            val ys = mask.indices.filter { mask[it] }.map { it / w }
            if (xs.isEmpty() || xs.max() - xs.min() < w / 4 || ys.max() - ys.min() < h / 4) problems += "$name is too small"

            val shadow = Bitmap.createBitmap(IntArray(w * h) { if (mask[it]) SHADOW else Color.WHITE }, w, h, Bitmap.Config.ARGB_8888)
            val x = (index % 4) * cell * 2
            val y = (index / 4) * cell
            canvas.drawBitmap(bitmap, null, Rect(x, y, x + cell, y + cell), null)
            canvas.drawBitmap(shadow, null, Rect(x + cell, y, x + cell * 2, y + cell), null)
        }
        File("build/reports").mkdirs()
        File("build/reports/shadows-sheet.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(problems.joinToString("; "), problems.isEmpty())
    }

    private companion object {
        const val DIR = "builtin/thumbs"
        const val SHADOW = 0xFF8B867F.toInt()
    }
}
