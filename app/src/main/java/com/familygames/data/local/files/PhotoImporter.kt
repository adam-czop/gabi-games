package com.familygames.data.local.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
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
