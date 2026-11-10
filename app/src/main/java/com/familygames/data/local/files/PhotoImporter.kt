package com.familygames.data.local.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.familygames.util.DispatcherProvider
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Crop rectangle as fractions (0..1) of the shown bitmap — keeps the UI free of pixel bookkeeping. */
data class CropFraction(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Brings a picked or photographed image into the app (docs/02 §3, ADR-006): decoded with EXIF orientation
 * applied, limited to 2048 px for editing, then cropped and stored by [MediaStorage] (no EXIF kept).
 */
class PhotoImporter(
    private val context: Context,
    private val storage: MediaStorage,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun decode(uri: String): Bitmap? = withContext(dispatchers.io) {
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri.toUri())
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > EDIT_MAX_SIDE) decoder.setTargetSampleSize(ceil(longest / EDIT_MAX_SIDE.toFloat()).toInt())
            }
        } catch (e: Exception) {
            Timber.w(e, "Cannot decode picked image")
            null
        }
    }

    suspend fun rotate(bitmap: Bitmap, degrees: Int): Bitmap = withContext(dispatchers.default) {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
    }

    suspend fun saveCropped(bitmap: Bitmap, crop: CropFraction): StoredPhoto {
        val cropped = withContext(dispatchers.default) {
            val left = (crop.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
            val top = (crop.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
            val right = (crop.right * bitmap.width).roundToInt().coerceIn(left + 1, bitmap.width)
            val bottom = (crop.bottom * bitmap.height).roundToInt().coerceIn(top + 1, bitmap.height)
            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        }
        return storage.savePhoto(cropped)
    }

    /** Target for the system camera (`TakePicture`), shared through the FileProvider (`cache/camera/`). */
    fun newCameraUri(): Uri {
        val file = File(context.cacheDir, "camera").apply { mkdirs() }.resolve("${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    companion object {
        const val EDIT_MAX_SIDE = 2048
    }
}

/** Parent's voice note for a photo (S10): AAC 64 kbps mono, at most 8 s (docs/02 §3). */
interface VoiceRecorder {
    fun start(): Boolean
    /** Stops and returns the recording, or null if nothing usable was recorded. */
    fun stop(): File?
    fun cancel()
    /** 0..32767, for the level bars. */
    fun amplitude(): Int
}

class MediaVoiceRecorder(private val context: Context, private val storage: MediaStorage) : VoiceRecorder {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    override fun start(): Boolean {
        cancel()
        val target = storage.newRecordingFile()
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(64_000)
            r.setMaxDuration(MAX_MS)
            r.setOutputFile(target)
            r.prepare()
            r.start()
            recorder = r
            file = target
            true
        } catch (e: Exception) {
            Timber.w(e, "Recording failed to start")
            r.release()
            target.delete()
            false
        }
    }

    override fun stop(): File? {
        val r = recorder ?: return null
        recorder = null
        val result = try {
            r.stop()
            file
        } catch (e: RuntimeException) {
            // stop() right after start() throws: nothing was recorded.
            file?.delete()
            null
        } finally {
            r.release()
        }
        file = null
        return result
    }

    override fun cancel() {
        recorder?.let {
            runCatching { it.stop() }
            it.release()
        }
        recorder = null
        file?.delete()
        file = null
    }

    override fun amplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    companion object {
        const val MAX_MS = 8_000
    }
}
