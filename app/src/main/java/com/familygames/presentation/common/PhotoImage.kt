package com.familygames.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.remember
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.familygames.domain.model.PhotoSource
import com.familygames.presentation.theme.Palette
import java.io.File

/** Coil model for a [PhotoSource]: app files directly, built-in illustrations via the asset URI scheme. */
fun PhotoSource.toImageModel(): Any = when (this) {
    is PhotoSource.LocalFile -> File(path)
    is PhotoSource.Asset -> "file:///android_asset/$path"
}

/**
 * A photo cropped to its box. A missing file shows the neutral placeholder instead of breaking the game
 * (docs/04 §7).
 */
@Composable
fun PhotoImage(source: PhotoSource?, contentDescription: String?, modifier: Modifier = Modifier) {
    val placeholder = ColorPainter(Palette.PhotoPlaceholders[0])
    if (source == null) {
        Box(modifier.background(Palette.PhotoPlaceholders[0]))
        return
    }
    val context = LocalPlatformContext.current
    val request = remember(source) {
        // Software bitmaps: hardware ones showed corrupted edges under the card's 3D flip (rotationY).
        ImageRequest.Builder(context).data(source.toImageModel()).allowHardware(false).build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        placeholder = placeholder,
        error = placeholder,
        modifier = modifier,
    )
}
