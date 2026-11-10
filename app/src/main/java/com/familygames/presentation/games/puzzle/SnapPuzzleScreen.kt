package com.familygames.presentation.games.puzzle

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.familygames.R
import com.familygames.domain.game.puzzle.PuzzleDifficulty
import com.familygames.domain.game.puzzle.PuzzleTuning
import com.familygames.domain.game.puzzle.isWithinSnap
import com.familygames.presentation.common.KidFrame
import com.familygames.presentation.common.RoundIconButton
import com.familygames.presentation.common.WinOverlay
import com.familygames.presentation.common.raisedShadow
import com.familygames.presentation.common.softShadow
import com.familygames.presentation.theme.Dimens
import com.familygames.presentation.theme.KidShapes
import com.familygames.presentation.theme.KidType
import com.familygames.presentation.theme.Palette
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/** Board and tray placement on the 1280×800 dp mockup frame (S06). All values in px for the current scale. */
private class SnapGeometry(difficulty: PuzzleDifficulty, scale: Float, density: Density) {
    private val unit = scale * density.density
    private fun dp(v: Float) = v * unit

    val board = Rect(Offset(dp(96f), dp(150f)), Size(dp(772f), dp(586f)))
    val inner = Rect(Offset(dp(110f), dp(164f)), Size(dp(744f), dp(558f)))
    val tray = Rect(Offset(dp(908f), dp(150f)), Size(dp(300f), dp(586f)))
    val rows = difficulty.rows
    val cols = difficulty.cols
    val inset = dp(4f)
    val slotSize = Size(inner.width / cols, inner.height / rows)

    /** Size of a piece lying in its slot (scale 1). */
    val pieceSize = Size(slotSize.width - 2 * inset, slotSize.height - 2 * inset)
    val snapDistance = dp(PuzzleTuning.snapDistanceDp(difficulty))

    fun slotRect(id: Int): Rect {
        val r = id / cols
        val c = id % cols
        return Rect(Offset(inner.left + c * slotSize.width + inset, inner.top + r * slotSize.height + inset), pieceSize)
    }

    private val trayPad = dp(16f)
    private val trayGap = dp(12f)

    private class TrayLayout(val cols: Int, val cell: Size, val scale: Float)

    /** For [count] pieces left, the column count that gives the biggest pieces (at most 70%, docs/05 §2). */
    private fun trayLayout(count: Int): TrayLayout = (1..3).map { cols ->
        val rows = ceil(count.coerceAtLeast(1) / cols.toFloat()).toInt()
        val cell = Size(
            (tray.width - 2 * trayPad - (cols - 1) * trayGap) / cols,
            (tray.height - 2 * trayPad - (rows - 1) * trayGap) / rows,
        )
        TrayLayout(cols, cell, min(PuzzleTuning.TRAY_SCALE, min(cell.width / pieceSize.width, cell.height / pieceSize.height)))
    }.maxBy { it.scale }

    fun trayScale(count: Int): Float = trayLayout(count).scale

    /** Pieces grow as the tray empties; the remaining ones stay centred vertically. */
    fun trayCenter(index: Int, count: Int): Offset {
        val layout = trayLayout(count)
        val rows = ceil(count.coerceAtLeast(1) / layout.cols.toFloat()).toInt()
        val pieceH = pieceSize.height * layout.scale
        val usedH = rows * pieceH + (rows - 1) * trayGap
        val top = tray.top + (tray.height - usedH) / 2
        val r = index / layout.cols
        val c = index % layout.cols
        return Offset(
            tray.left + trayPad + c * (layout.cell.width + trayGap) + layout.cell.width / 2,
            top + r * (pieceH + trayGap) + pieceH / 2,
        )
    }
}

private class PieceMotion(start: Offset, startScale: Float) {
    val center = Animatable(start, Offset.VectorConverter)
    val scale = Animatable(startScale)
}

/** S06 · drag pieces from the tray onto the board; a magnet snaps them into the right slot. */
@Composable
fun SnapPuzzleScreen(state: SnapUiState, onEvent: (SnapEvent) -> Unit, onStarShown: (Int) -> Unit = {}) {
    BackHandler { onEvent(SnapEvent.Home) }
    KidFrame { scale ->
        Box(Modifier.fillMaxSize().then(if (state.won) Modifier.blur(6.dp) else Modifier)) {
            val density = LocalDensity.current
            val geo = remember(state.difficulty, scale, density) { SnapGeometry(state.difficulty, scale, density) }
            val image = state.image

            RoundIconButton(
                icon = R.drawable.ic_home,
                contentDescription = stringResource(R.string.cd_home),
                onClick = { onEvent(SnapEvent.Home) },
                modifier = Modifier.padding(start = Dimens.ScreenPaddingH, top = Dimens.ScreenPaddingTop),
            )
            RoundIconButton(
                icon = R.drawable.ic_bulb,
                contentDescription = stringResource(R.string.cd_hint),
                onClick = { onEvent(SnapEvent.Hint) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = Dimens.ScreenPaddingH, top = Dimens.ScreenPaddingTop),
            )

            if (state.missingPhoto) {
                Text(stringResource(R.string.puzzle_photo_missing), style = KidType.Label, modifier = Modifier.align(Alignment.Center))
            } else if (image != null && state.pieces.isNotEmpty()) {
                SnapBoard(state, image, geo, onEvent)
            }

        }
        val stars = state.stars
        if (state.won && stars != null) {
            WinOverlay(stars = stars, onPlayAgain = { onEvent(SnapEvent.Restart) }, onMenu = { onEvent(SnapEvent.Home) }, scale = scale, onStarShown = onStarShown)
        }
    }
}

@Composable
private fun SnapBoard(state: SnapUiState, image: ImageBitmap, geo: SnapGeometry, onEvent: (SnapEvent) -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var draggingId by remember(state.round) { mutableStateOf<Int?>(null) }
    var nearTarget by remember(state.round) { mutableStateOf(false) }
    // Placed locally at release time, before the ViewModel confirms — avoids a one-frame jump back to the tray.
    val placedLocally = remember(state.round) { mutableSetOf<Int>() }

    val unplaced = state.pieces.filterNot { it.placed || it.id in placedLocally }
    fun isPlaced(id: Int) = id in placedLocally || state.pieces.first { it.id == id }.placed
    fun targetCenter(id: Int): Offset =
        if (isPlaced(id)) geo.slotRect(id).center else geo.trayCenter(unplaced.indexOfFirst { it.id == id }.coerceAtLeast(0), unplaced.size)

    val motions = remember(state.round, geo) {
        state.pieces.mapIndexed { index, p -> p.id to PieceMotion(geo.trayCenter(index, state.pieces.size), geo.trayScale(state.pieces.size)) }.toMap()
    }
    val currentState by rememberUpdatedState(state)
    val currentOnEvent by rememberUpdatedState(onEvent)

    val ghostAlpha by animateFloatAsState(if (state.ghostBoost) 0.5f else 0.18f, tween(400), label = "ghost")
    val pulse = rememberInfiniteTransition(label = "hint")
    val pulseAlpha by pulse.animateFloat(0.25f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "pulse")
    val bounce by pulse.animateFloat(0f, -12f, infiniteRepeatable(tween(300), RepeatMode.Reverse), label = "bounce")

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(state.round, geo) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (currentState.won) return@awaitEachGesture
                    // Topmost unplaced piece under the finger.
                    val hit = currentState.pieces.lastOrNull { p ->
                        if (p.placed || p.id in placedLocally) return@lastOrNull false
                        val m = motions.getValue(p.id)
                        val size = geo.pieceSize * m.scale.value
                        val c = m.center.value
                        Rect(Offset(c.x - size.width / 2, c.y - size.height / 2), size).contains(down.position)
                    } ?: return@awaitEachGesture
                    down.consume()
                    val motion = motions.getValue(hit.id)
                    val grab = motion.center.value - down.position
                    draggingId = hit.id
                    scope.launch { motion.scale.animateTo(1f, tween(150)) }
                    drag(down.id) { change ->
                        val center = change.position + grab
                        scope.launch { motion.center.snapTo(center) }
                        val slot = geo.slotRect(hit.id).center
                        nearTarget = isWithinSnap(center.x, center.y, slot.x, slot.y, geo.snapDistance)
                        change.consume()
                    }
                    val end = motion.center.value
                    val slot = geo.slotRect(hit.id).center
                    if (isWithinSnap(end.x, end.y, slot.x, slot.y, geo.snapDistance)) {
                        placedLocally += hit.id
                        currentOnEvent(SnapEvent.PiecePlaced(hit.id))
                    } else {
                        currentOnEvent(SnapEvent.PieceMissed(hit.id))
                    }
                    draggingId = null
                    nearTarget = false
                }
            },
    ) {
        // Board: white frame, inner tray colour, faded ghost picture and dashed slot outlines.
        Box(
            Modifier
                .offset { geo.board.topLeft.round() }
                .size(with(density) { geo.board.width.toDp() }, with(density) { geo.board.height.toDp() })
                .softShadow(KidShapes.M)
                .background(Palette.Surface, KidShapes.M)
                .border(Dimens.Stroke, Palette.Line, KidShapes.M),
        )
        Box(
            Modifier
                .offset { geo.inner.topLeft.round() }
                .size(with(density) { geo.inner.width.toDp() }, with(density) { geo.inner.height.toDp() })
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.Bg2),
        ) {
            PuzzleWholeImage(image, Modifier.fillMaxSize(), alpha = ghostAlpha, desaturate = true)
        }
        Canvas(Modifier.fillMaxSize()) {
            val radius = CornerRadius(10.dp.toPx())
            state.pieces.filterNot { isPlaced(it.id) }.forEach { p ->
                val r = geo.slotRect(p.id)
                val highlighted = (draggingId == p.id && nearTarget)
                val hinted = state.hintTargetId == p.id
                if (highlighted || hinted) {
                    val a = if (hinted && !highlighted) pulseAlpha else 1f
                    drawRoundRect(Palette.Accent.copy(alpha = 0.14f * a), r.topLeft, r.size, radius)
                    drawRoundRect(Palette.Accent.copy(alpha = a), r.topLeft, r.size, radius, style = Stroke(3.dp.toPx()))
                } else {
                    drawRoundRect(
                        Palette.Line2, r.topLeft, r.size, radius,
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx()))),
                    )
                }
            }
        }
        // Tray on the right.
        Box(
            Modifier
                .offset { geo.tray.topLeft.round() }
                .size(with(density) { geo.tray.width.toDp() }, with(density) { geo.tray.height.toDp() })
                .background(Palette.Bg2, KidShapes.L),
        )

        // Pieces: placed first, then the tray, the dragged one on top.
        val order = state.pieces.sortedBy { p ->
            when {
                p.id == draggingId -> 2
                isPlaced(p.id) -> 0
                else -> 1
            }
        }
        val pieceW = with(density) { geo.pieceSize.width.toDp() }
        val pieceH = with(density) { geo.pieceSize.height.toDp() }
        val pieceDescription = stringResource(R.string.cd_piece)
        order.forEach { p ->
            val motion = motions.getValue(p.id)
            val dragging = p.id == draggingId
            val placed = isPlaced(p.id)
            val target = targetCenter(p.id)
            val trayScale = geo.trayScale(unplaced.size)
            LaunchedEffect(target, dragging, placed, trayScale) {
                if (dragging) return@LaunchedEffect
                launch { motion.scale.animateTo(if (placed) 1f else trayScale, tween(300)) }
                motion.center.animateTo(target, if (placed) spring(dampingRatio = 0.75f, stiffness = 400f) else tween(300))
            }
            val hinted = state.hintTargetId == p.id && !placed && !dragging
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .offset {
                        val c = motion.center.value
                        IntOffset((c.x - geo.pieceSize.width / 2).roundToInt(), (c.y - geo.pieceSize.height / 2).roundToInt())
                    }
                    .size(pieceW, pieceH)
                    .graphicsLayer {
                        scaleX = motion.scale.value
                        scaleY = motion.scale.value
                        rotationZ = if (dragging) -3f else 0f
                        translationY = if (hinted) bounce * density.density else 0f
                    }
                    .semantics { contentDescription = pieceDescription }
                    .then(if (dragging) Modifier.raisedShadow(shape) else Modifier.softShadow(shape))
                    .clip(shape)
                    .background(Palette.Surface)
                    .border(3.dp, Palette.Surface, shape),
            ) {
                PuzzlePieceImage(image, p.row, p.col, geo.rows, geo.cols, Modifier.fillMaxSize())
            }
        }
    }
}

private fun Offset.round() = IntOffset(x.roundToInt(), y.roundToInt())
