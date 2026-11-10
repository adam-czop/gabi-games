package com.familygames.presentation.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.familygames.R
import com.familygames.presentation.theme.Dimens
import com.familygames.presentation.theme.KidTheme
import com.familygames.presentation.theme.Palette

/**
 * White round button with a 1 dp line and an Ink2 line icon (docs/08 §8 `BigRoundButton`).
 * [size] 96 dp for primary actions, 64 dp for secondary ones (no shadow, like `.btn-round.small`).
 */
@Composable
fun RoundIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.TouchPrimary,
    iconTint: Color = Palette.Ink2,
) {
    val small = size < Dimens.TouchPrimary
    Box(
        modifier = modifier
            .size(size)
            .then(if (small) Modifier else Modifier.softShadow(CircleShape))
            .kidPressable(onClick = onClick)
            .background(Palette.Surface, CircleShape)
            .border(Dimens.Stroke, Palette.Line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(if (small) Dimens.IconSmall else Dimens.IconLarge),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F4F0)
@Composable
private fun RoundIconButtonPreview() {
    KidTheme {
        Row(Modifier.padding(24.dp)) {
            RoundIconButton(R.drawable.ic_home, "", {})
            RoundIconButton(R.drawable.ic_volume_on, "", {}, Modifier.padding(start = 24.dp), size = Dimens.TouchSecondary)
        }
    }
}
