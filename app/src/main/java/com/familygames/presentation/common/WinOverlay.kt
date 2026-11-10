package com.familygames.presentation.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familygames.R
import com.familygames.presentation.theme.KidTheme
import com.familygames.presentation.theme.KidType
import com.familygames.presentation.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Delay before the buttons react: an excited child keeps tapping the screen (docs/03 S04). */
private const val BUTTONS_ENABLED_AFTER_MS = 800L
private const val STAR_STAGGER_MS = 200L

/**
 * S04 · shared success overlay for all games: light fog over the board, "Brawo!", 1–3 stars popping in one
 * by one, then two wordless buttons — play again (accent) and menu (outlined).
 */
@Composable
fun WinOverlay(
    stars: Int,
    onPlayAgain: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    onStarShown: (index: Int) -> Unit = {},
) {
    var buttonsEnabled by remember { mutableStateOf(false) }
    val starAnims = remember { List(3) { Animatable(0f) } }
    val starShown by rememberUpdatedState(onStarShown)

    LaunchedEffect(stars) {
        launch {
            delay(BUTTONS_ENABLED_AFTER_MS)
            buttonsEnabled = true
        }
        starAnims.forEachIndexed { i, anim ->
            delay(STAR_STAGGER_MS)
            launch { anim.animateTo(1f, tween(220)) }
            if (i < stars) starShown(i)
        }
    }
    BackHandler { if (buttonsEnabled) onMenu() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Palette.Bg.copy(alpha = 0.72f))
            // swallow taps on the fog so the board below never reacts
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        ConfettiCanvas(key = stars, modifier = Modifier.fillMaxSize(), particleCount = 40, durationMs = 2000, density = LocalDensity.current.density)
        val cardShape = RoundedCornerShape(36.dp * scale)
        Column(
            modifier = Modifier
                .width(600.dp * scale)
                .raisedShadow(cardShape)
                .background(Palette.Surface, cardShape)
                .border(1.dp, Palette.Line, cardShape)
                .padding(start = 40.dp * scale, end = 40.dp * scale, top = 48.dp * scale, bottom = 44.dp * scale),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp * scale)
                    .background(Palette.HoneySoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_star_filled), null, tint = Palette.Honey, modifier = Modifier.size(64.dp * scale))
            }
            Spacer(Modifier.height(18.dp * scale))
            Text(stringResource(R.string.win_title), style = KidType.Title.copy(fontSize = 52.sp * scale))
            Spacer(Modifier.height(14.dp * scale))
            StarsRow(
                count = stars,
                size = 60.dp * scale,
                gap = 10.dp * scale,
                modifier = Modifier.semantics { contentDescription = "$stars/3" },
                starModifier = { i ->
                    Modifier.graphicsLayer {
                        val p = starAnims[i].value
                        alpha = p
                        scaleX = 0.8f + 0.2f * p
                        scaleY = 0.8f + 0.2f * p
                    }
                },
            )
            Spacer(Modifier.height(30.dp * scale))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp * scale)) {
                WinButton(R.drawable.ic_restart, stringResource(R.string.cd_play_again), Palette.Accent, Color.White, null, scale, buttonsEnabled, onPlayAgain)
                WinButton(R.drawable.ic_home, stringResource(R.string.cd_home), Palette.Surface, Palette.Ink2, Palette.Line2, scale, buttonsEnabled, onMenu)
            }
        }
    }
}

@Composable
private fun WinButton(
    icon: Int,
    description: String,
    background: Color,
    tint: Color,
    border: Color?,
    scale: Float,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(36.dp * scale)
    Box(
        modifier = Modifier
            .size(136.dp * scale)
            .then(if (border == null) Modifier.softShadow(shape) else Modifier)
            .kidPressable(enabled = enabled, onClick = onClick)
            .background(background, shape)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, tint = tint, modifier = Modifier.size(60.dp * scale))
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun WinOverlayPreview() {
    KidTheme { WinOverlay(stars = 2, onPlayAgain = {}, onMenu = {}) }
}
