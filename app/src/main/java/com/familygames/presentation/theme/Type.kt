package com.familygames.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.familygames.R

val Fredoka = FontFamily(
    Font(R.font.fredoka_medium, FontWeight.Medium),
    Font(R.font.fredoka_semibold, FontWeight.SemiBold),
)

val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
)

/** Child zone text styles (docs/08 §3). */
object KidType {
    val Display = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 60.sp, letterSpacing = 0.5.sp, color = Palette.Ink)
    val Title = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, color = Palette.Ink)
    val Tile = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, color = Palette.Ink)
    val Label = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Medium, fontSize = 24.sp, color = Palette.Ink2)
}

/** Parent zone text styles (docs/08 §3). */
object ParentType {
    val H1 = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = Palette.ParentInk)
    val H2 = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Palette.ParentInk)
    val Body = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Palette.ParentInk)
    val Caption = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 0.6.sp, color = Palette.ParentInk2)
}

internal val KidTypography = Typography(
    displayLarge = KidType.Display,
    headlineLarge = KidType.Title,
    titleLarge = KidType.Tile,
    bodyLarge = KidType.Label,
    labelLarge = KidType.Label,
)

internal val ParentTypography = Typography().let { base ->
    base.copy(
        headlineMedium = ParentType.H1,
        titleMedium = ParentType.H2,
        bodyLarge = ParentType.Body,
        bodyMedium = ParentType.Body.copy(fontSize = 15.sp),
        labelSmall = ParentType.Caption,
        labelLarge = base.labelLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
        bodySmall = base.bodySmall.copy(fontFamily = Nunito),
        labelMedium = base.labelMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
    )
}
