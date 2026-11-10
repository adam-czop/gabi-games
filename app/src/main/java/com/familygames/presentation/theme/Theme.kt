package com.familygames.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KidColorScheme = lightColorScheme(
    primary = Palette.Accent,
    onPrimary = Color.White,
    primaryContainer = Palette.AccentSoft,
    onPrimaryContainer = Palette.AccentInk,
    secondary = Palette.Mist,
    secondaryContainer = Palette.MistSoft,
    tertiary = Palette.Honey,
    tertiaryContainer = Palette.HoneySoft,
    background = Palette.Bg,
    onBackground = Palette.Ink,
    surface = Palette.Surface,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Bg2,
    onSurfaceVariant = Palette.Ink2,
    outline = Palette.Line2,
    outlineVariant = Palette.Line,
)

/** Same palette as the child zone; the parent zone differs by typography and density (docs/08 §1). */
private val ParentColorScheme = lightColorScheme(
    primary = Palette.AccentInk,
    onPrimary = Color.White,
    primaryContainer = Palette.AccentSoft,
    onPrimaryContainer = Palette.AccentInk,
    secondary = Palette.Mist,
    secondaryContainer = Palette.MistSoft,
    background = Palette.Bg,
    onBackground = Palette.ParentInk,
    surface = Palette.Surface,
    onSurface = Palette.ParentInk,
    surfaceVariant = Palette.Bg2,
    onSurfaceVariant = Palette.ParentInk2,
    outline = Palette.Line2,
    outlineVariant = Palette.ParentLine,
    error = Palette.Danger,
    onError = Color.White,
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(Dimens.RadiusS),
    medium = RoundedCornerShape(Dimens.RadiusM),
    large = RoundedCornerShape(Dimens.RadiusL),
    extraLarge = RoundedCornerShape(Dimens.RadiusXL),
)

/** Child zone (S01–S07). Light theme only — no dark mode in v1 (ADR-012). */
@Composable
fun KidTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KidColorScheme, typography = KidTypography, shapes = AppShapes, content = content)
}

/** Parent zone (S08–S14). */
@Composable
fun ParentTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ParentColorScheme, typography = ParentTypography, shapes = AppShapes, content = content)
}
