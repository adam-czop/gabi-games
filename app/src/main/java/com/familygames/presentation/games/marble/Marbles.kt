package com.familygames.presentation.games.marble

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.R
import com.familygames.domain.game.marble.MarbleEngine
import com.familygames.domain.game.marble.MarbleEvent
import com.familygames.domain.game.marble.MarbleLevel
import com.familygames.domain.game.marble.MarblePath
import com.familygames.domain.game.marble.Marbles
import com.familygames.domain.game.maze.Point
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.GameResult
import com.familygames.domain.repository.GameResultRepository
import com.familygames.presentation.common.Sfx
import com.familygames.presentation.common.softShadow
import com.familygames.presentation.games.common.GameEffect
import com.familygames.presentation.games.common.TaskGameScaffold
import com.familygames.presentation.theme.Dimens
import com.familygames.presentation.theme.KidTheme
import com.familygames.presentation.theme.Palette
import com.familygames.util.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// MARK: State

data class MarbleUi(val color: Int, val x: Float, val y: Float)

/** A pop drawn as a fading ring; [age] 0..1. */
data class Burst(val x: Float, val y: Float, val color: Int, val age: Float)

data class MarblesUiState(
    val loading: Boolean = true,
    val path: MarblePath? = null,
    val marbles: List<MarbleUi> = emptyList(),
    val shot: MarbleUi? = null,
    val current: Int = 0,
    val next: Int = 0,
    /** Barrel direction in radians (straight up = -PI / 2). */
    val aim: Float = -PI.toFloat() / 2,
    val bursts: List<Burst> = emptyList(),
    val winStars: Int? = null,
    val game: Int = 0,
)

sealed interface MarblesEvent {
    /** A tap on the board, in board units: on the cannon it swaps the marbles, anywhere else it shoots. */
    data class Tap(val x: Float, val y: Float) : MarblesEvent
    data object Restart : MarblesEvent
    data object Home : MarblesEvent
}

// MARK: ViewModel

/** M22 · "Kulki" (S31): a slow, friendly marble shooter; the chain stops before its house, so it cannot be lost. */
class MarblesViewModel(
    levelKey: String,
    private val results: GameResultRepository,
    private val clock: Clock,
    private val random: Random,
) : ViewModel() {
    private val level = MarbleLevel.fromKey(levelKey)
    private val state = MutableStateFlow(MarblesUiState())
    val uiState: StateFlow<MarblesUiState> = state.asStateFlow()
    private val effects = Channel<GameEffect>(Channel.BUFFERED)
    val effect: Flow<GameEffect> = effects.receiveAsFlow()

    private var engine: MarbleEngine? = null
    private var loop: Job? = null
    private var startedAt = 0L
    private var bursts: List<Burst> = emptyList()

    init {
        newGame()
    }

    fun onEvent(event: MarblesEvent) {
        when (event) {
            is MarblesEvent.Tap -> tap(Point(event.x, event.y))
            MarblesEvent.Restart -> newGame()
            MarblesEvent.Home -> {
                loop?.cancel()
                effects.trySend(GameEffect.Home)
            }
        }
    }

    private fun newGame() {
        loop?.cancel()
        val e = MarbleEngine(level.path(), level, random).also { engine = it }
        bursts = emptyList()
        startedAt = clock.now()
        state.update { MarblesUiState(loading = false, path = e.path, game = it.game + 1) }
        publish(e)
        loop = viewModelScope.launch {
            while (isActive) {
                delay(FRAME_MS)
                frame(e, FRAME_MS / 1000f)
            }
        }
    }

    private fun tap(p: Point) {
        val e = engine ?: return
        if (state.value.winStars != null) return
        if (p.distanceTo(Marbles.CANNON) <= CANNON_TOUCH) {
            e.swap()
            sound(Sfx.FLIP)
        } else if (e.shoot(p)) {
            state.update { it.copy(aim = atan2(p.y - Marbles.CANNON.y, p.x - Marbles.CANNON.x)) }
            sound(Sfx.TAP)
        }
        publish(e)
    }

    private fun frame(e: MarbleEngine, dt: Float) {
        e.step(dt).forEach { event ->
            when (event) {
                is MarbleEvent.Popped -> {
                    sound(Sfx.POP, 0.9f + 0.15f * (event.combo - 1))
                    if (event.combo > 1) sound(Sfx.STAR, 1f + 0.1f * event.combo)
                    bursts = bursts + Burst(event.at.x, event.at.y, 0, 0f)
                }
                MarbleEvent.Inserted -> sound(Sfx.SNAP)
                MarbleEvent.Missed -> Unit
                MarbleEvent.Won -> viewModelScope.launch { finish(e) }
            }
        }
        bursts = bursts.map { it.copy(age = it.age + dt / BURST_S) }.filter { it.age < 1f }
        publish(e)
    }

    private fun publish(e: MarbleEngine) {
        state.update { s ->
            s.copy(
                marbles = e.marbles().map { (c, p) -> MarbleUi(c, p.x, p.y) },
                shot = e.shot?.let { MarbleUi(it.color, it.x, it.y) },
                current = e.current,
                next = e.next,
                bursts = bursts,
            )
        }
    }

    private suspend fun finish(e: MarbleEngine) {
        loop?.cancel()
        delay(WIN_DELAY_MS)
        val stars = Marbles.stars(level.balls, e.wasted)
        results.save(
            GameResult(
                game = GameKind.MARBLES, levelKey = level.key, stars = stars, moves = level.balls, mistakes = e.wasted,
                hintsUsed = 0, durationMs = clock.now() - startedAt, finishedAt = clock.now(),
            ),
        )
        state.update { it.copy(winStars = stars, bursts = emptyList()) }
        sound(Sfx.WIN)
    }

    private fun sound(sfx: Sfx, rate: Float = 1f) {
        effects.trySend(GameEffect.Sound(sfx, rate))
    }

    override fun onCleared() {
        loop?.cancel()
    }

    private companion object {
        const val FRAME_MS = 16L
        const val BURST_S = 0.45f
        const val WIN_DELAY_MS = 700L

        /** Taps this close to the cannon centre swap the marbles (the cannon is 124 units wide). */
        const val CANNON_TOUCH = 70f
    }
}

// MARK: Screen

/** S31 · the track with its house at the end, the chain, and the cannon at the bottom. */
@Composable
fun MarblesScreen(state: MarblesUiState, onEvent: (MarblesEvent) -> Unit, onStarShown: (Int) -> Unit = {}) {
    TaskGameScaffold(
        onHome = { onEvent(MarblesEvent.Home) },
        onRestart = { onEvent(MarblesEvent.Restart) },
        progress = null,
        winStars = state.winStars,
        onStarShown = onStarShown,
    ) { scale ->
        val path = state.path ?: return@TaskGameScaffold
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 40.dp * scale, end = 40.dp * scale, top = 124.dp * scale, bottom = 24.dp * scale),
        ) {
            Board(path, state, onEvent, Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun Board(path: MarblePath, state: MarblesUiState, onEvent: (MarblesEvent) -> Unit, modifier: Modifier) {
    val send by rememberUpdatedState(onEvent)
    val shape = RoundedCornerShape(Dimens.RadiusXL)
    val description = stringResource(R.string.cd_marbles_board)
    val track = remember(path) { Path().apply { path.points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } } }
    Box(
        modifier
            .aspectRatio(Marbles.BOARD_W / Marbles.BOARD_H)
            .softShadow(shape)
            .clip(shape)
            .background(Palette.Bg2)
            .semantics { contentDescription = description }
            .pointerInput(state.game) {
                detectTapGestures { p ->
                    val k = Marbles.BOARD_W / size.width
                    send(MarblesEvent.Tap(p.x * k, p.y * k))
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / Marbles.BOARD_W
            scale(k, k, pivot = Offset.Zero) {
                drawTrack(track, path)
                state.marbles.forEach { drawMarble(it.color, Offset(it.x, it.y)) }
                state.shot?.let { drawMarble(it.color, Offset(it.x, it.y)) }
                state.bursts.forEach { b ->
                    drawCircle(Palette.Surface, Marbles.DIAMETER * (0.5f + b.age), Offset(b.x, b.y), alpha = 1f - b.age, style = Stroke(6f))
                }
                drawCannon(state)
            }
        }
    }
}

// MARK: Drawing

/** Marble colours: four crayon paints of Kolorowanki, each with its own white mark for colour-blind children (ADR-032). */
private val MarbleColors = listOf(Palette.Paints[0], Palette.Paints[1], Palette.Paints[2], Palette.Paints[3])

private fun DrawScope.drawTrack(track: Path, path: MarblePath) {
    val groove = Marbles.DIAMETER + 14f
    drawPath(track, Palette.Line2, style = Stroke(groove + 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(track, Palette.Bg, style = Stroke(groove, cap = StrokeCap.Round, join = StrokeJoin.Round))
    // The little house at the end, where the chain stops and never gets in.
    val end = path.points.last()
    val c = Offset(end.x, end.y)
    val r = Marbles.DIAMETER * 0.75f
    drawCircle(Palette.Surface, r, c)
    drawCircle(Palette.Line2, r, c, style = Stroke(5f))
    val roof = Path().apply {
        moveTo(c.x - r * 0.62f, c.y - r * 0.05f)
        lineTo(c.x, c.y - r * 0.6f)
        lineTo(c.x + r * 0.62f, c.y - r * 0.05f)
    }
    drawPath(roof, Palette.Ink2, style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawRect(Palette.Ink2, Offset(c.x - r * 0.42f, c.y - r * 0.05f), Size(r * 0.84f, r * 0.55f), style = Stroke(6f, join = StrokeJoin.Round))
}

private fun DrawScope.drawMarble(color: Int, c: Offset, radius: Float = Marbles.DIAMETER / 2) {
    val fill = MarbleColors[color % MarbleColors.size]
    drawCircle(Palette.Ink.copy(alpha = 0.12f), radius, c + Offset(0f, radius * 0.12f))
    drawCircle(fill, radius, c)
    drawCircle(Palette.Ink.copy(alpha = 0.18f), radius, c, style = Stroke(radius * 0.08f))
    val mark = if (color == 1) Palette.Ink2 else Color.White
    val m = radius * 0.34f
    when (color % MarbleColors.size) {
        0 -> drawCircle(mark, m * 0.75f, c)
        1 -> drawPath(Path().apply { moveTo(c.x, c.y - m); lineTo(c.x + m, c.y); lineTo(c.x, c.y + m); lineTo(c.x - m, c.y); close() }, mark)
        2 -> drawPath(Path().apply { moveTo(c.x, c.y - m); lineTo(c.x + m, c.y + m * 0.8f); lineTo(c.x - m, c.y + m * 0.8f); close() }, mark)
        else -> {
            drawLine(mark, Offset(c.x - m, c.y), Offset(c.x + m, c.y), strokeWidth = m * 0.55f, cap = StrokeCap.Round)
            drawLine(mark, Offset(c.x, c.y - m), Offset(c.x, c.y + m), strokeWidth = m * 0.55f, cap = StrokeCap.Round)
        }
    }
    drawArc(
        Color.White, startAngle = 200f, sweepAngle = 60f, useCenter = false,
        topLeft = c - Offset(radius * 0.68f, radius * 0.68f), size = Size(radius * 1.36f, radius * 1.36f),
        alpha = 0.6f, style = Stroke(radius * 0.14f, cap = StrokeCap.Round),
    )
}

/** Barrel towards the last shot, the marble ready to fire in the middle, the next one on the side. */
private fun DrawScope.drawCannon(state: MarblesUiState) {
    val c = Offset(Marbles.CANNON.x, Marbles.CANNON.y)
    val degrees = state.aim * 180f / PI.toFloat()
    rotate(degrees, pivot = c) {
        drawRoundRect(
            Palette.Line2, topLeft = Offset(c.x, c.y - 22f), size = Size(112f, 44f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f, 20f),
        )
    }
    drawCircle(Palette.Ink.copy(alpha = 0.1f), 62f, c + Offset(0f, 6f))
    drawCircle(Palette.Surface, 62f, c)
    drawCircle(Palette.Line, 62f, c, style = Stroke(3f))
    drawMarble(state.current, c, radius = Marbles.DIAMETER / 2 + 4f)
    val nextAt = Offset(c.x + 104f * cos(0.35f), c.y + 104f * sin(0.35f) - 30f)
    drawCircle(Palette.Surface, 26f, nextAt)
    drawCircle(Palette.Line, 26f, nextAt, style = Stroke(3f))
    drawMarble(state.next, nextAt, radius = 18f)
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun MarblesPreview() {
    val level = MarbleLevel.L3
    val engine = MarbleEngine(level.path(), level, Random(4)).also { e -> repeat(240) { e.step(1f / 60) } }
    KidTheme {
        MarblesScreen(
            MarblesUiState(
                loading = false, path = engine.path,
                marbles = engine.marbles().map { (c, p) -> MarbleUi(c, p.x, p.y) },
                current = engine.current, next = engine.next, aim = -1.2f,
            ),
            onEvent = {},
        )
    }
}
