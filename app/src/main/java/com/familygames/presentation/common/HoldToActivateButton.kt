package com.familygames.presentation.common

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.familygames.R
import com.familygames.presentation.theme.Dimens
import com.familygames.presentation.theme.KidTheme
import com.familygames.presentation.theme.Palette
import kotlinx.coroutines.launch

/**
 * Deliberately hard-to-hit parent entry (S01 lock): a plain tap does nothing, the finger has to stay
 * down for [durationMs] while an Accent progress ring fills. Releasing early resets the ring.
 */
@Composable
fun HoldToActivateButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onActivated: () -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Int = 3000,
    size: Dp = Dimens.TouchSecondary,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val activate by rememberUpdatedState(onActivated)

    Box(
        modifier = modifier
            .size(size)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
                // Accessibility services get a direct action; the child-proof hold applies to touch only.
                onClick { activate(); true }
            }
            .pointerInput(durationMs) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val hold = scope.launch {
                        progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
                        activate()
                        progress.snapTo(0f)
                    }
                    waitForUpOrCancellation()
                    if (hold.isActive) {
                        hold.cancel()
                        scope.launch { progress.animateTo(0f, tween(200)) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.55f)
                .background(Palette.Surface, CircleShape)
                .border(Dimens.Stroke, Palette.Line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Palette.Ink2,
                modifier = Modifier.size(Dimens.IconSmall),
            )
        }
        Canvas(Modifier.fillMaxSize().padding(2.dp)) {
            val sweep = 360f * progress.value
            if (sweep > 0f) {
                drawArc(
                    color = Palette.Accent,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F4F0)
@Composable
private fun HoldToActivateButtonPreview() {
    KidTheme {
        HoldToActivateButton(R.drawable.ic_lock, "", {}, Modifier.padding(24.dp))
    }
}
