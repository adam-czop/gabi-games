package com.familygames.presentation.parent.photos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.familygames.data.local.files.CropFraction
import com.familygames.domain.model.Aspect
import com.familygames.presentation.theme.Palette
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Max zoom relative to "frame filled" (docs/03 S10). */
private const val MAX_ZOOM = 5f

/**
 * S10 crop: the picture moves under a fixed 1:1 or 4:3 frame — one finger pans, two fingers zoom.
 * The picture always covers the whole frame. [onCrop] gets the visible part as fractions of the bitmap.
 */
@Composable
fun CropView(image: ImageBitmap, aspect: Aspect, resetKey: Any, onCrop: (CropFraction) -> Unit, modifier: Modifier = Modifier) {
    val reportCrop by rememberUpdatedState(onCrop)
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        val ratio = if (aspect == Aspect.SQUARE) 1f else 4f / 3f
        val frameW = min(viewW * 0.86f, viewH * 0.86f * ratio)
        val frameH = frameW / ratio
        val frame = Rect(Offset((viewW - frameW) / 2, (viewH - frameH) / 2), Size(frameW, frameH))
        val minScale = max(frameW / image.width, frameH / image.height)

        var scale by remember(resetKey) { mutableFloatStateOf(minScale) }
        var center by remember(resetKey) { mutableStateOf(frame.center) }

        fun clampCenter(c: Offset, s: Float): Offset {
            val halfW = image.width * s / 2
            val halfH = image.height * s / 2
            return Offset(
                c.x.coerceIn(frame.right - halfW, frame.left + halfW),
                c.y.coerceIn(frame.bottom - halfH, frame.top + halfH),
            )
        }

        LaunchedEffect(scale, center, resetKey) {
            val left = center.x - image.width * scale / 2
            val top = center.y - image.height * scale / 2
            reportCrop(
                CropFraction(
                    left = ((frame.left - left) / (image.width * scale)).coerceIn(0f, 1f),
                    top = ((frame.top - top) / (image.height * scale)).coerceIn(0f, 1f),
                    right = ((frame.right - left) / (image.width * scale)).coerceIn(0f, 1f),
                    bottom = ((frame.bottom - top) / (image.height * scale)).coerceIn(0f, 1f),
                ),
            )
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(resetKey, minScale) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(minScale, minScale * MAX_ZOOM)
                        val factor = newScale / scale
                        // Zoom around the fingers, then follow the pan.
                        val moved = centroid + (center - centroid) * factor + pan
                        scale = newScale
                        center = clampCenter(moved, newScale)
                    }
                },
        ) {
            val w = image.width * scale
            val h = image.height * scale
            drawImage(
                image = image,
                dstOffset = IntOffset((center.x - w / 2).roundToInt(), (center.y - h / 2).roundToInt()),
                dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
            val dim = Palette.Ink.copy(alpha = 0.55f)
            drawRect(dim, Offset.Zero, Size(size.width, frame.top))
            drawRect(dim, Offset(0f, frame.bottom), Size(size.width, size.height - frame.bottom))
            drawRect(dim, Offset(0f, frame.top), Size(frame.left, frame.height))
            drawRect(dim, Offset(frame.right, frame.top), Size(size.width - frame.right, frame.height))
            val thin = 1.dp.toPx()
            for (i in 1..2) {
                val x = frame.left + frame.width * i / 3
                val y = frame.top + frame.height * i / 3
                drawLine(Color.White.copy(alpha = 0.5f), Offset(x, frame.top), Offset(x, frame.bottom), thin)
                drawLine(Color.White.copy(alpha = 0.5f), Offset(frame.left, y), Offset(frame.right, y), thin)
            }
            drawRect(Color.White, frame.topLeft, frame.size, style = Stroke(2.dp.toPx()))
            val corner = 22.dp.toPx()
            val thick = 5.dp.toPx()
            listOf(frame.topLeft to Offset(1f, 1f), frame.topRight to Offset(-1f, 1f), frame.bottomLeft to Offset(1f, -1f), frame.bottomRight to Offset(-1f, -1f))
                .forEach { (p, d) ->
                    drawLine(Color.White, p, p + Offset(corner * d.x, 0f), thick)
                    drawLine(Color.White, p, p + Offset(0f, corner * d.y), thick)
                }
        }
    }
}
