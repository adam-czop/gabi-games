package com.familygames.presentation.common

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.familygames.domain.model.PhotoSource

/** Small software bitmaps for games drawn on one Canvas (Bańki, docs/12 M15a). */
fun interface ThumbLoader {
    suspend fun load(source: PhotoSource, sizePx: Int): ImageBitmap?
}

class CoilThumbLoader(private val context: Context) : ThumbLoader {
    override suspend fun load(source: PhotoSource, sizePx: Int): ImageBitmap? {
        val request = ImageRequest.Builder(context)
            .data(source.toImageModel())
            .size(sizePx)
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
        return result.image.toBitmap().asImageBitmap()
    }
}
