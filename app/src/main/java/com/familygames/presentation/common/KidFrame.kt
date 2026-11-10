package com.familygames.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familygames.presentation.theme.Palette

/**
 * Child-zone screen root. Mockups are drawn on a 1280×800 dp frame (docs/03); [content] gets the factor
 * that fits that frame on the current screen (1 on a 10" tablet, less on a 7" one).
 */
@Composable
fun KidFrame(modifier: Modifier = Modifier, singlePointer: Boolean = true, content: @Composable BoxWithConstraintsScope.(scale: Float) -> Unit) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .then(if (singlePointer) Modifier.firstPointerOnly() else Modifier)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val scale = minOf(1f, maxWidth / 1280.dp, maxHeight / 800.dp)
        content(scale)
    }
}
