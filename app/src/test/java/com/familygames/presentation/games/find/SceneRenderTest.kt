package com.familygames.presentation.games.find

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.familygames.domain.game.find.Scene
import com.familygames.domain.game.find.Scenes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Every scene object has a Polish name and question, and the scenes render. Also writes
 * `build/reports/find-scenes.png` (the four scenes and the target cards) for a visual check.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SceneRenderTest {
    @Test
    fun `every object has texts and every scene draws`() {
        val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        Scenes.all.flatMap { it.objects }.forEach { o ->
            assertTrue(o.key, o.key in FindTexts.keys)
            assertTrue(o.key, res.getString(FindTexts.question(o.key)).endsWith("?"))
        }
        val w = 600
        val h = (w * Scene.HEIGHT / Scene.WIDTH).toInt()
        val card = 90
        val sheet = Bitmap.createBitmap(w * 2, (h + card) * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet).apply { drawColor(Color.WHITE) }
        Scenes.all.forEachIndexed { i, scene ->
            val x = (i % 2) * w.toFloat()
            val y = (i / 2) * (h + card).toFloat()
            canvas.save()
            canvas.translate(x, y)
            SceneRenderer.draw(canvas, scene, w / Scene.WIDTH)
            scene.objects.forEachIndexed { j, o ->
                canvas.save()
                canvas.translate(j * (card * 0.66f), h.toFloat())
                SceneRenderer.drawObject(canvas, o, card * 0.66f)
                canvas.restore()
            }
            canvas.restore()
        }
        // Not blank: the sky colour of the farm is there.
        assertEquals(0xFFE6ECF0.toInt(), sheet.getPixel(10, 10))
        File("build/reports").mkdirs()
        File("build/reports/find-scenes.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val farm = Bitmap.createBitmap(Scene.WIDTH.toInt(), Scene.HEIGHT.toInt(), Bitmap.Config.ARGB_8888)
        SceneRenderer.draw(Canvas(farm), Scenes.all.first(), 1f)
        File("build/reports/find-farm.png").outputStream().use { farm.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
