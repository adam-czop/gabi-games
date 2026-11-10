package com.familygames.presentation.common

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role

/** Taps on the same element closer than this are ignored (docs/03 §0.5). */
private const val DEBOUNCE_MS = 250L

/** Tap feedback for the child zone (docs/08 §5): scale 0.97 with a soft spring, no ripple, debounced. */
fun Modifier.kidPressable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lastClick = remember { longArrayOf(0L) }
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "kidPressScale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = {
                val now = SystemClock.uptimeMillis()
                if (now - lastClick[0] >= DEBOUNCE_MS) {
                    lastClick[0] = now
                    onClick()
                }
            },
        )
}

/**
 * Only the first finger counts (docs/03 §0.5): a child laying the whole hand on the screen produces many
 * pointers; every pointer that goes down while another one is held is consumed before the children see it.
 */
fun Modifier.firstPointerOnly(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        var first: PointerId? = null
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            // A palm can put several fingers down in the same event: keep only one of them.
            if (first == null) first = event.changes.firstOrNull { it.changedToDownIgnoreConsumed() }?.id
            event.changes.forEach { change -> if (change.id != first) change.consume() }
        } while (event.changes.any { it.pressed }) // until every finger is up
    }
}
