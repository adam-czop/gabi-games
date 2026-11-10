package com.familygames.presentation.games.puzzle

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.familygames.domain.model.PhotoSource
import com.familygames.presentation.common.toImageModel
import kotlin.math.roundToInt

/** Loads the full puzzle picture as one software bitmap, centre-cropped to the 4:3 board (docs/05 §1). */
fun interface PuzzleImageLoader {
    suspend fun load(source: PhotoSource): ImageBitmap?
}

class CoilPuzzleImageLoader(private val context: Context) : PuzzleImageLoader {
    override suspend fun load(source: PhotoSource): ImageBitmap? {
        val request = ImageRequest.Builder(context)
            .data(source.toImageModel())
            .size(1600)
            .allowHardware(false) // pieces are drawn on a Canvas
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
        return cropTo4x3(result.image.toBitmap()).asImageBitmap()
    }

    private fun cropTo4x3(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        return if (w * 3 > h * 4) {
            val newW = (h * 4f / 3f).roundToInt()
            Bitmap.createBitmap(src, (w - newW) / 2, 0, newW, h)
        } else {
            val newH = (w * 3f / 4f).roundToInt()
            Bitmap.createBitmap(src, 0, (h - newH) / 2, w, newH)
        }
    }
}

/** Draws piece ([row], [col]) of a [rows]×[cols] split of [image] — all pieces come from the one bitmap. */
@Composable
fun PuzzlePieceImage(
    image: ImageBitmap,
    row: Int,
    col: Int,
    rows: Int,
    cols: Int,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    desaturate: Boolean = false,
) {
    val pieceW = image.width / cols
    val pieceH = image.height / rows
    val filter = if (desaturate) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.4f) }) else null
    Canvas(modifier) {
        drawImage(
            image = image,
            srcOffset = IntOffset(col * pieceW, row * pieceH),
            srcSize = IntSize(pieceW, pieceH),
            dstSize = size.toIntSize(),
            alpha = alpha,
            colorFilter = filter,
        )
    }
}

/** The whole picture (ghost image, reference preview). */
@Composable
fun PuzzleWholeImage(image: ImageBitmap, modifier: Modifier = Modifier, alpha: Float = 1f, desaturate: Boolean = false) {
    PuzzlePieceImage(image, row = 0, col = 0, rows = 1, cols = 1, modifier = modifier, alpha = alpha, desaturate = desaturate)
}
