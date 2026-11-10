package com.familygames.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Size tokens (docs/08 §4). */
object Dimens {
    val RadiusXL = 32.dp
    val RadiusWin = 36.dp
    val RadiusL = 24.dp
    val RadiusM = 18.dp
    val RadiusS = 12.dp

    val Stroke = 1.dp
    val StrokeSelected = 4.dp

    val TouchPrimary = 96.dp
    val TouchSecondary = 64.dp

    val ScreenPaddingH = 32.dp
    val ScreenPaddingTop = 28.dp

    val GridGapMemory = 28.dp
    val GridGapSwap = 8.dp

    val IconSmall = 28.dp
    val IconLarge = 40.dp
}

object KidShapes {
    val XL = RoundedCornerShape(Dimens.RadiusXL)
    val Win = RoundedCornerShape(Dimens.RadiusWin)
    val L = RoundedCornerShape(Dimens.RadiusL)
    val M = RoundedCornerShape(Dimens.RadiusM)
    val S = RoundedCornerShape(Dimens.RadiusS)
}
