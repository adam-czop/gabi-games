package com.familygames.presentation.games.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Drop targets (baskets, slots) by key, measured in root coordinates. */
class DropZones {
    val rects = mutableStateMapOf<Int, Rect>()

    /** The zone that contains the centre of the dropped item (docs/12 M13a). */
    fun hit(point: Offset): Int? = rects.entries.firstOrNull { it.value.contains(point) }?.key
}

@Composable
fun rememberDropZones() = remember { DropZones() }

fun Modifier.dropZone(zones: DropZones, key: Int): Modifier = onGloballyPositioned { zones.rects[key] = it.boundsInRoot() }

/**
 * A picture the child drags (overlay above its siblings, 1.08 scale). [onDrop] gets the zone under the item's centre
 * and returns true when it was accepted; otherwise the item glides back (300 ms).
 */
@Composable
fun DraggableItem(
    zones: DropZones,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDragStart: () -> Unit = {},
    onDrop: (zone: Int?) -> Boolean,
    content: @Composable BoxScope.(dragging: Boolean) -> Unit,
) {
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var dragging by remember { mutableStateOf(false) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val scope = rememberCoroutineScope()
    Box(
        modifier
            .zIndex(if (dragging || offset.value != Offset.Zero) 10f else 0f)
            .offset { IntOffset(offset.value.x.roundToInt(), offset.value.y.roundToInt()) }
            .graphicsLayer {
                val s = if (dragging) 1.08f else 1f
                scaleX = s
                scaleY = s
            }
            .onGloballyPositioned { coords = it }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        onDragStart()
                    },
                    onDragEnd = {
                        dragging = false
                        val center = coords?.boundsInRoot()?.center
                        val accepted = onDrop(center?.let { zones.hit(it) })
                        scope.launch { if (accepted) offset.snapTo(Offset.Zero) else offset.animateTo(Offset.Zero, tween(300)) }
                    },
                    onDragCancel = {
                        dragging = false
                        scope.launch { offset.animateTo(Offset.Zero, tween(300)) }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        scope.launch { offset.snapTo(offset.value + amount) }
                    },
                )
            },
    ) { content(dragging) }
}
