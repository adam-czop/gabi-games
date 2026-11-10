package com.familygames.presentation.games.coloring

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.R
import com.familygames.data.local.files.DrawingStore
import com.familygames.domain.game.board.UndoStack
import com.familygames.domain.game.coloring.ColoringPage
import com.familygames.domain.game.coloring.ColoringPages
import com.familygames.domain.game.coloring.Region
import com.familygames.domain.model.GameKind
import com.familygames.domain.repository.SettingsRepository
import com.familygames.presentation.common.HoldToActivateButton
import com.familygames.presentation.common.RoundIconButton
import com.familygames.presentation.common.Sfx
import com.familygames.presentation.common.kidPressable
import com.familygames.presentation.common.softShadow
import com.familygames.presentation.games.common.FreePlayClock
import com.familygames.presentation.games.common.FreePlayScaffold
import com.familygames.presentation.games.common.GameEffect
import com.familygames.presentation.games.common.isToddler
import com.familygames.presentation.theme.Dimens
import com.familygames.presentation.theme.KidTheme
import com.familygames.presentation.theme.Palette
import com.familygames.util.DispatcherProvider
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A finger-painted line in page units. */
data class BrushStroke(val points: List<Offset>, val color: Int, val width: Float)

data class ColoringUiState(
    val page: Int = 0,
    /** Region index → paint index. */
    val fills: Map<Int, Int> = emptyMap(),
    val strokes: List<BrushStroke> = emptyList(),
    val color: Int = 0,
    val brush: Boolean = false,
    /** Finger painting is offered from 2,5 years (docs/12 M18). */
    val brushAllowed: Boolean = false,
    val canUndo: Boolean = false,
)

sealed interface ColoringEvent {
    data class Tap(val x: Float, val y: Float) : ColoringEvent
    data class Stroke(val points: List<Offset>) : ColoringEvent
    data class Color(val index: Int) : ColoringEvent
    data object ToggleBrush : ColoringEvent
    data object Undo : ColoringEvent
    data object Clear : ColoringEvent
    data object Next : ColoringEvent
    data object Home : ColoringEvent
}

private sealed interface Step {
    data class Fill(val region: Int, val previous: Int?) : Step
    data object Stroke : Step
}

/** M18 · "Kolorowanki" (S28): free play; finished pages go to `drawings/`. */
class ColoringViewModel(
    private val settings: SettingsRepository,
    private val drawings: DrawingStore,
    private val freePlay: FreePlayClock,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val state = MutableStateFlow(ColoringUiState())
    val uiState: StateFlow<ColoringUiState> = state.asStateFlow()
    private val effects = Channel<GameEffect>(Channel.BUFFERED)
    val effect: Flow<GameEffect> = effects.receiveAsFlow()
    private val undo = UndoStack<Step>(10)

    init {
        viewModelScope.launch {
            val toddler = settings.settings.first().isToddler
            state.update { it.copy(brushAllowed = !toddler) }
            freePlay.start()
        }
    }

    private val page: ColoringPage get() = ColoringPages.all[state.value.page]

    fun onEvent(event: ColoringEvent) {
        when (event) {
            is ColoringEvent.Tap -> fill(event.x, event.y)
            is ColoringEvent.Stroke -> {
                if (event.points.isEmpty()) return
                state.update { it.copy(strokes = it.strokes + BrushStroke(event.points, it.color, BRUSH), canUndo = true) }
                undo.push(Step.Stroke)
            }
            is ColoringEvent.Color -> {
                state.update { it.copy(color = event.index) }
                effects.trySend(GameEffect.Sound(Sfx.TAP))
            }
            ColoringEvent.ToggleBrush -> state.update { it.copy(brush = !it.brush && it.brushAllowed) }
            ColoringEvent.Undo -> undoStep()
            ColoringEvent.Clear -> {
                undo.clear()
                state.update { it.copy(fills = emptyMap(), strokes = emptyList(), canUndo = false) }
            }
            ColoringEvent.Next -> viewModelScope.launch {
                save()
                undo.clear()
                state.update { it.copy(page = (it.page + 1) % ColoringPages.all.size, fills = emptyMap(), strokes = emptyList(), canUndo = false) }
            }
            ColoringEvent.Home -> viewModelScope.launch {
                withContext(NonCancellable) {
                    save()
                    freePlay.stop(GameKind.COLORING)
                }
                effects.send(GameEffect.Home)
            }
        }
    }

    private fun fill(x: Float, y: Float) {
        val region = page.regionAt(x, y) ?: return
        val s = state.value
        if (s.fills[region] == s.color) return
        undo.push(Step.Fill(region, s.fills[region]))
        state.update { it.copy(fills = it.fills + (region to it.color), canUndo = true) }
        effects.trySend(GameEffect.Sound(Sfx.SNAP))
    }

    private fun undoStep() {
        when (val step = undo.pop() ?: return) {
            is Step.Fill -> state.update { s ->
                s.copy(fills = if (step.previous == null) s.fills - step.region else s.fills + (step.region to step.previous))
            }
            Step.Stroke -> state.update { it.copy(strokes = it.strokes.dropLast(1)) }
        }
        state.update { it.copy(canUndo = undo.size > 0) }
    }

    private suspend fun save() {
        val s = state.value
        if (s.fills.isEmpty() && s.strokes.isEmpty()) return
        val bitmap = withContext(dispatchers.default) { ColoringRenderer.render(ColoringPages.all[s.page], s.fills, s.strokes) }
        drawings.save(bitmap)
    }

    companion object {
        const val BRUSH = 36f
    }
}

/** Draws a page into a bitmap for `drawings/` (same look as the screen). */
object ColoringRenderer {
    fun render(page: ColoringPage, fills: Map<Int, Int>, strokes: List<BrushStroke>): Bitmap {
        val w = ColoringPage.WIDTH.toInt()
        val h = ColoringPage.HEIGHT.toInt()
        val bitmap = createBitmap(w, h)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val line = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        val brush = strokes.map { s ->
            android.graphics.Path().apply { s.points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } } to s
        }
        fun paintStrokes() = brush.forEach { (path, s) ->
            line.color = Palette.Paints[s.color].toArgb()
            line.strokeWidth = s.width
            canvas.drawPath(path, line)
        }
        // Painter's order, as on screen: paint outside the picture, then each region over the ones behind it.
        paintStrokes()
        page.regions.forEachIndexed { i, r ->
            val path = r.androidPath()
            fill.color = (fills[i]?.let { Palette.Paints[it] } ?: Palette.Surface).toArgb()
            canvas.drawPath(path, fill)
            canvas.withClip(path) { paintStrokes() }
            line.color = Palette.Ink.toArgb()
            line.strokeWidth = OUTLINE
            canvas.drawPath(path, line)
        }
        line.color = Palette.Ink.toArgb()
        line.strokeWidth = OUTLINE
        page.lines.forEach { canvas.drawPath(it.androidPath(close = false), line) }
        return bitmap
    }

    private fun Region.androidPath(close: Boolean = true) = android.graphics.Path().apply {
        xs.indices.forEach { i -> if (i == 0) moveTo(xs[i], ys[i]) else lineTo(xs[i], ys[i]) }
        if (close) close()
    }
}

private const val OUTLINE = 9f

private fun Region.toPath(close: Boolean = true) = Path().apply {
    xs.indices.forEach { i -> if (i == 0) moveTo(xs[i], ys[i]) else lineTo(xs[i], ys[i]) }
    if (close) close()
}

/** S28 · picture on the left, paint pots on the right, undo / clear (hold) / next. */
@Composable
fun ColoringScreen(state: ColoringUiState, onEvent: (ColoringEvent) -> Unit) {
    FreePlayScaffold(onHome = { onEvent(ColoringEvent.Home) }) { scale ->
        val page = ColoringPages.all[state.page]
        val sheet = RoundedCornerShape(28.dp)
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 150.dp * scale, top = 40.dp * scale, bottom = 40.dp * scale)
                .fillMaxHeight()
                .aspectRatio(ColoringPage.WIDTH / ColoringPage.HEIGHT)
                .softShadow(sheet)
                .background(Palette.Surface, sheet)
                .border(1.dp, Palette.Line, sheet)
                .clipToBounds(),
        ) {
            PageCanvas(page, state, onEvent)
        }
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 36.dp * scale),
            verticalArrangement = Arrangement.spacedBy(14.dp * scale),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Palette.Paints.chunked(2).forEachIndexed { row, pair ->
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(14.dp * scale)) {
                    pair.forEachIndexed { col, color ->
                        val index = row * 2 + col
                        PaintPot(color, index, state.color == index, scale) { onEvent(ColoringEvent.Color(index)) }
                    }
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = Dimens.ScreenPaddingH, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (state.brushAllowed) {
                // Pencil = finger painting; highlighted while it is on, tap again to go back to filling.
                val description = stringResource(if (state.brush) R.string.cd_brush_mode else R.string.cd_fill_mode)
                Box(
                    Modifier
                        .size(Dimens.TouchSecondary)
                        .semantics { contentDescription = description }
                        .kidPressable { onEvent(ColoringEvent.ToggleBrush) }
                        .background(if (state.brush) Palette.Accent else Palette.Surface, CircleShape)
                        .border(1.dp, Palette.Line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_game_coloring), null, tint = if (state.brush) Palette.Surface else Palette.Ink2, modifier = Modifier.size(30.dp))
                }
            }
            RoundIconButton(R.drawable.ic_undo, stringResource(R.string.cd_undo), onClick = { onEvent(ColoringEvent.Undo) }, size = Dimens.TouchSecondary)
            HoldToActivateButton(R.drawable.ic_trash, stringResource(R.string.cd_clear_drawing), onActivated = { onEvent(ColoringEvent.Clear) }, durationMs = 1_500)
            RoundIconButton(R.drawable.ic_next, stringResource(R.string.cd_next_picture), onClick = { onEvent(ColoringEvent.Next) }, size = Dimens.TouchSecondary)
        }
    }
}

@Composable
private fun PageCanvas(page: ColoringPage, state: ColoringUiState, onEvent: (ColoringEvent) -> Unit) {
    val live = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateListOf<Offset>() }
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(state.brush, state.page) {
                val k = ColoringPage.WIDTH / size.width
                if (state.brush) {
                    detectDragGestures(
                        onDragStart = { live.clear(); live += it * k },
                        onDragEnd = {
                            onEvent(ColoringEvent.Stroke(live.toList()))
                            live.clear()
                        },
                        onDragCancel = { live.clear() },
                        onDrag = { change, _ -> live += change.position * k },
                    )
                } else {
                    detectTapGestures { onEvent(ColoringEvent.Tap(it.x * k, it.y * k)) }
                }
            },
    ) {
        val k = size.width / ColoringPage.WIDTH
        scale(k, k, pivot = Offset.Zero) { drawPage(page, state, live) }
    }
}

/**
 * Painter's order: each region covers the ones behind it (fill, then the finger paint inside it, then its outline),
 * so hidden outlines never show through and paint always stays under the lines.
 */
private fun DrawScope.drawPage(page: ColoringPage, state: ColoringUiState, live: List<Offset>) {
    val strokeStyle = { w: Float -> Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val brush = (state.strokes + listOfNotNull(live.takeIf { it.isNotEmpty() }?.let { BrushStroke(it, state.color, ColoringViewModel.BRUSH) })).map { s ->
        Path().apply { s.points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } } to s
    }
    fun paint() = brush.forEach { (path, s) -> drawPath(path, Palette.Paints[s.color], style = strokeStyle(s.width)) }
    paint()
    page.regions.forEachIndexed { i, r ->
        val path = r.toPath()
        drawPath(path, state.fills[i]?.let { Palette.Paints[it] } ?: Palette.Surface)
        if (brush.isNotEmpty()) clipPath(path) { paint() }
        drawPath(path, Palette.Ink, style = strokeStyle(OUTLINE))
    }
    page.lines.forEach { drawPath(it.toPath(close = false), Palette.Ink, style = strokeStyle(OUTLINE)) }
}

@Composable
private fun PaintPot(color: Color, index: Int, selected: Boolean, scale: Float, onClick: () -> Unit) {
    val description = stringResource(R.string.cd_paint, index + 1)
    Box(
        Modifier
            .size(72.dp * scale)
            .semantics {
                contentDescription = description
                this.selected = selected
            }
            .kidPressable(onClick = onClick)
            .border(if (selected) 4.dp else 1.dp, if (selected) Palette.Accent else Palette.Line, CircleShape)
            .padding(if (selected) 8.dp else 4.dp)
            .background(color, CircleShape),
    ) {
        if (selected) Icon(painterResource(R.drawable.ic_check), null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(28.dp * scale))
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun ColoringPreview() {
    KidTheme { ColoringScreen(ColoringUiState(page = 1, fills = mapOf(1 to 1, 4 to 1, 0 to 6), color = 1), onEvent = {}) }
}
