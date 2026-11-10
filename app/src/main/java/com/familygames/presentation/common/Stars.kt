package com.familygames.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.familygames.R
import com.familygames.presentation.theme.Palette

/** Filled Honey stars for the score, Line for the rest (docs/08 §7). */
@Composable
fun StarsRow(
    count: Int,
    modifier: Modifier = Modifier,
    max: Int = 3,
    size: Dp = 36.dp,
    gap: Dp = 6.dp,
    starModifier: (index: Int) -> Modifier = { Modifier },
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        repeat(max) { i ->
            Icon(
                painter = painterResource(R.drawable.ic_star_filled),
                contentDescription = null,
                tint = if (i < count) Palette.Honey else Palette.Line,
                modifier = starModifier(i).size(size),
            )
        }
    }
}
