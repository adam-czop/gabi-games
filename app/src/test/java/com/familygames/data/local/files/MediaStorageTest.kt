package com.familygames.data.local.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.familygames.domain.model.PhotoSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaStorageTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var files: File
    private lateinit var storage: MediaStorage

    @Before
    fun setUp() {
        files = tmp.newFolder("files")
        storage = MediaStorage(files, tmp.newFolder("cache"))
    }

    @Test
    fun `photo is scaled to 1600 px with a 400 px thumbnail and no temp files`() = runTest {
        val bitmap = Bitmap.createBitmap(3200, 2400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(126, 156, 140)) }

        val stored = storage.savePhoto(bitmap)

        val photo = BitmapFactory.decodeFile(storage.resolve(stored.filePath).path)
        val thumb = BitmapFactory.decodeFile(storage.resolve(stored.thumbPath).path)
        assertEquals(1600 to 1200, photo.width to photo.height)
        assertEquals(400 to 300, thumb.width to thumb.height)
        assertTrue(stored.filePath.startsWith("photos/") && stored.thumbPath.startsWith("photos/thumbs/"))
        assertTrue(files.walkTopDown().none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `small photo is not upscaled`() = runTest {
        val stored = storage.savePhoto(Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888))

        assertEquals(300, BitmapFactory.decodeFile(storage.resolve(stored.filePath).path).width)
    }

    @Test
    fun `delete removes files and ignores assets and nulls`() = runTest {
        val stored = storage.savePhoto(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888))

        storage.delete(stored.filePath, stored.thumbPath, null, "asset://builtin/dom_kanapa.webp")

        assertFalse(storage.resolve(stored.filePath).exists())
        assertFalse(storage.resolve(stored.thumbPath).exists())
    }

    @Test
    fun `recording is moved into private audio storage`() = runTest {
        val recording = storage.newRecordingFile().apply { writeText("m4a") }

        val relative = storage.commitAudio(recording)

        assertTrue(relative.startsWith("audio/") && relative.endsWith(".m4a"))
        assertEquals("m4a", storage.resolve(relative).readText())
        assertFalse(recording.exists())
    }

    @Test
    fun `stored paths map to sources`() {
        assertEquals(PhotoSource.Asset("builtin/dom_kanapa.webp"), storage.toSource("asset://builtin/dom_kanapa.webp"))
        assertEquals(PhotoSource.LocalFile(File(files, "photos/a.jpg").absolutePath), storage.toSource("photos/a.jpg"))
    }
}
