package com.familygames.data.local.files

import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.familygames.domain.model.PhotoSource
import com.familygames.util.DispatcherProvider
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** Relative paths (to [filesDir]) of a stored photo and its thumbnail. */
data class StoredPhoto(val filePath: String, val thumbPath: String)

/**
 * Private media storage (docs/02 §3): photos and thumbnails as JPEG, voice recordings as m4a.
 * Nothing is written to the gallery or external storage. Writes are atomic (`.tmp` + rename), so a
 * crash never leaves a half-written photo behind.
 */
class MediaStorage(
    private val filesDir: File,
    private val cacheDir: File,
    private val dispatchers: DispatcherProvider = DispatcherProvider(),
) {

    /** Scales [bitmap] down to the storage limits and writes photo + thumbnail. EXIF is never written. */
    suspend fun savePhoto(bitmap: Bitmap): StoredPhoto = withContext(dispatchers.io) {
        val id = UUID.randomUUID().toString()
        val photo = "$PHOTOS_DIR/$id.jpg"
        val thumb = "$THUMBS_DIR/$id.jpg"
        try {
            writeJpeg(bitmap, PHOTO_MAX_SIDE, PHOTO_QUALITY, resolve(photo))
            writeJpeg(bitmap, THUMB_MAX_SIDE, THUMB_QUALITY, resolve(thumb))
        } catch (e: Exception) {
            resolve(photo).delete()
            resolve(thumb).delete()
            throw e
        }
        StoredPhoto(photo, thumb)
    }

    /** Temporary file for the voice recorder (S10); moved into storage by [commitAudio]. */
    fun newRecordingFile(): File =
        File(cacheDir, RECORDINGS_DIR).apply { mkdirs() }.resolve("${UUID.randomUUID()}.m4a")

    /** Moves a finished recording into private storage and returns its relative path. */
    suspend fun commitAudio(recording: File): String = withContext(dispatchers.io) {
        val relative = "$AUDIO_DIR/${UUID.randomUUID()}.m4a"
        val target = resolve(relative)
        target.parentFile?.mkdirs()
        if (!recording.renameTo(target)) {
            recording.copyTo(target, overwrite = true)
            recording.delete()
        }
        relative
    }

    fun resolve(relativePath: String): File = File(filesDir, relativePath)

    /** Maps a stored path (relative or `asset://…`) to a [PhotoSource]. */
    fun toSource(storedPath: String): PhotoSource =
        if (storedPath.startsWith(ASSET_PREFIX)) {
            PhotoSource.Asset(storedPath.removePrefix(ASSET_PREFIX))
        } else {
            PhotoSource.LocalFile(resolve(storedPath).absolutePath)
        }

    /** Deletes stored files; `null` and built-in `asset://` paths are ignored. */
    suspend fun delete(vararg relativePaths: String?) = withContext(dispatchers.io) {
        relativePaths.filterNotNull()
            .filterNot { it.startsWith(ASSET_PREFIX) }
            .forEach { resolve(it).delete() }
    }

    private fun writeJpeg(source: Bitmap, maxSide: Int, quality: Int, target: File) {
        target.parentFile?.mkdirs()
        val scaled = source.scaledToMaxSide(maxSide)
        val tmp = File(target.parentFile, target.name + ".tmp")
        try {
            tmp.outputStream().use { out ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) throw IOException("JPEG encoding failed")
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
            }
        } finally {
            tmp.delete()
            if (scaled !== source) scaled.recycle()
        }
    }

    private fun Bitmap.scaledToMaxSide(maxSide: Int): Bitmap {
        val longest = max(width, height)
        if (longest <= maxSide) return this
        val factor = maxSide.toFloat() / longest
        return scale((width * factor).roundToInt(), (height * factor).roundToInt(), filter = true)
    }

    companion object {
        const val ASSET_PREFIX = "asset://"
        const val PHOTOS_DIR = "photos"
        const val THUMBS_DIR = "photos/thumbs"
        const val AUDIO_DIR = "audio"
        const val RECORDINGS_DIR = "recordings"
        const val PHOTO_MAX_SIDE = 1600
        const val THUMB_MAX_SIDE = 400
        private const val PHOTO_QUALITY = 85
        private const val THUMB_QUALITY = 80
    }
}
