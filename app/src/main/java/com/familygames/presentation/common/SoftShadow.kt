package com.familygames.presentation.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.familygames.presentation.theme.Palette

/**
 * Soft two-layer shadow (docs/08 §4, CSS --sh-1): 0 1 2 Ink@5% + 0 6 18 Ink@5%.
 * No hard offset shadows in v2; pressing changes scale only.
 */
fun Modifier.softShadow(shape: Shape): Modifier = this
    .dropShadow(shape, Shadow(radius = 18.dp, color = Palette.Ink, offset = DpOffset(0.dp, 6.dp), alpha = 0.05f))
    .dropShadow(shape, Shadow(radius = 2.dp, color = Palette.Ink, offset = DpOffset(0.dp, 1.dp), alpha = 0.05f))

/** Raised variant (CSS --sh-2): win dialog, dragged puzzle piece. */
fun Modifier.raisedShadow(shape: Shape): Modifier = this
    .dropShadow(shape, Shadow(radius = 36.dp, color = Palette.Ink, offset = DpOffset(0.dp, 16.dp), alpha = 0.08f))
    .dropShadow(shape, Shadow(radius = 4.dp, color = Palette.Ink, offset = DpOffset(0.dp, 2.dp), alpha = 0.05f))
