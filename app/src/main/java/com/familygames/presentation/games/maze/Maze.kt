package com.familygames.presentation.games.maze

import android.content.res.Resources
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.R
import com.familygames.domain.game.maze.MazeLevel
import com.familygames.domain.game.maze.Point
import com.familygames.domain.game.maze.TraceOutcome
import com.familygames.domain.game.maze.TraceScoring
import com.familygames.domain.game.maze.Tracer
import com.familygames.domain.game.maze.Track
import com.familygames.domain.game.maze.TrackFactory
import com.familygames.domain.game.maze.TrackShape
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.GameResult
import com.familygames.domain.model.Photo
import com.familygames.domain.model.PhotoSource
import com.familygames.domain.repository.GameResultRepository
import com.familygames.presentation.common.LocalReducedMotion
import com.familygames.presentation.common.PhotoImage
import com.familygames.presentation.common.Sfx
import com.familygames.presentation.common.VoicePrompts
import com.familygames.presentation.common.label
import com.familygames.presentation.common.softShadow
import com.familygames.presentation.games.common.GameEffect
import com.familygames.presentation.games.common.GamePhotos
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
import kotlinx.coroutines.launch
import kotlin.random.Random

// MARK: State

data class MazeUiState(
    val loading: Boolean = true,
    val track: Track? = null,
    val trackIndex: Int = 0,
    val totalTracks: Int = 0,
    val ball: Point = Point(0f, 0f),
    /** Where the ball has rolled on this track (board units), drawn as a soft line. */
    val trail: List<Point> = emptyList(),
    val holding: Boolean = false,
    /** Grows on every slip off the corridor: the ball wobbles where it stopped. */
    val slips: Int = 0,
    /** Grows on a touch away from the ball: the ball hops to show where to start. */
    val nudges: Int = 0,
    /** Photo waiting at the goal; null shows a plain goal ring. */
    val goal: PhotoSource? = null,
    val reached: Boolean = false,
    val winStars: Int? = null,
    val game: Int = 0,
)

sealed interface MazeEvent {
    data class Down(val x: Float, val y: Float) : MazeEvent
    data class Move(val x: Float, val y: Float) : MazeEvent
    data object Up : MazeEvent
    data object Restart : MazeEvent
    data object Home : MazeEvent
}

// MARK: ViewModel

/** M19 · "Labirynt" (S29): lead the ball with a finger along three tracks to family photos. */
class MazeViewModel(
    levelKey: String,
    private val gamePhotos: GamePhotos,
    private val prompts: VoicePrompts,
    private val resources: Resources,
    private val factory: TrackFactory,
    private val results: GameResultRepository,
    private val clock: Clock,
    private val random: Random,
) : ViewModel() {
    private val level = MazeLevel.fromKey(levelKey)
    private val state = MutableStateFlow(MazeUiState())
    val uiState: StateFlow<MazeUiState> = state.asStateFlow()
    private val effects = Channel<GameEffect>(Channel.BUFFERED)
    val effect: Flow<GameEffect> = effects.receiveAsFlow()

    private var tracks: List<Track> = emptyList()
    private var goals: List<Photo> = emptyList()
    private var tracer: Tracer? = null
    private var slips = 0
    private var rolled = 0f
    private var ticks = 0
    private var startedAt = 0L
    private var pending: Job? = null

    init {
        viewModelScope.launch { newGame() }
    }

    fun onEvent(event: MazeEvent) {
        when (event) {
            is MazeEvent.Down -> down(Point(event.x, event.y))
            is MazeEvent.Move -> move(Point(event.x, event.y))
            MazeEvent.Up -> {
                tracer?.up()
                state.update { it.copy(holding = false) }
            }
            MazeEvent.Restart -> viewModelScope.launch { newGame() }
            MazeEvent.Home -> {
                pending?.cancel()
                effects.trySend(GameEffect.Home)
            }
        }
    }

    private suspend fun newGame() {
        pending?.cancel()
        tracks = factory.game(level)
        val photos = gamePhotos.preferFamily(resources.getString(R.string.family_category_name)).shuffled(random)
        goals = if (photos.isEmpty()) emptyList() else List(tracks.size) { photos[it % photos.size] }
        slips = 0
        startedAt = clock.now()
        state.update { MazeUiState(loading = false, totalTracks = tracks.size, game = it.game + 1) }
        showTrack(0)
        prompts.awaitReady()
    }

    private fun showTrack(index: Int) {
        val t = Tracer(tracks[index]).also { tracer = it }
        rolled = 0f
        ticks = 0
        state.update {
            it.copy(
                track = t.track, trackIndex = index, ball = t.point, trail = listOf(t.point), holding = false,
                goal = goals.getOrNull(index)?.thumb, reached = false,
            )
        }
    }

    private fun down(p: Point) {
        val t = tracer ?: return
        if (state.value.reached || state.value.winStars != null) return
        if (t.down(p)) {
            sound(Sfx.TAP)
            state.update { it.copy(holding = true) }
        } else {
            state.update { it.copy(nudges = it.nudges + 1) }
        }
    }

    private fun move(p: Point) {
        val t = tracer ?: return
        when (val outcome = t.move(p)) {
            TraceOutcome.Ignored -> Unit
            is TraceOutcome.Moved -> {
                rolled += outcome.travelled
                if (rolled >= TICK_UNITS) {
                    rolled = 0f
                    ticks++
                    sound(Sfx.TAP, 0.8f + 0.06f * (ticks % 8))
                }
                follow(t, force = false)
            }
            // No sound and no colour: the ball just stays and wobbles (docs/03 §0).
            TraceOutcome.Slipped -> {
                slips++
                state.update { it.copy(holding = false, slips = it.slips + 1) }
            }
            TraceOutcome.Finished -> {
                follow(t, force = true)
                reachGoal()
            }
        }
    }

    private fun follow(t: Tracer, force: Boolean) = state.update { s ->
        val last = s.trail.lastOrNull()
        val trail = if (force || last == null || last.distanceTo(t.point) >= TRAIL_STEP) s.trail + t.point else s.trail
        s.copy(ball = t.point, trail = trail, holding = t.holding)
    }

    private fun reachGoal() {
        state.update { it.copy(reached = true, holding = false) }
        sound(Sfx.MATCH)
        val goal = goals.getOrNull(state.value.trackIndex)
        pending = viewModelScope.launch {
            val started = clock.now()
            if (goal != null) prompts.play(prompts.label(goal))
            val left = GOAL_PAUSE_MS - (clock.now() - started)
            if (left > 0) delay(left)
            val next = state.value.trackIndex + 1
            if (next < tracks.size) showTrack(next) else finish()
        }
    }

    private suspend fun finish() {
        val stars = TraceScoring.stars(tracks.size, slips)
        results.save(
            GameResult(
                game = GameKind.MAZE, levelKey = level.key, stars = stars, moves = tracks.size, mistakes = slips,
                hintsUsed = 0, durationMs = clock.now() - startedAt, finishedAt = clock.now(),
            ),
        )
        state.update { it.copy(winStars = stars) }
        sound(Sfx.WIN)
    }

    private fun sound(sfx: Sfx, rate: Float = 1f) {
        effects.trySend(GameEffect.Sound(sfx, rate))
    }

    companion object {
        /** A soft tick every this many board units rolled, a little higher each time. */
        const val TICK_UNITS = 140f
        const val TRAIL_STEP = 6f
        const val GOAL_PAUSE_MS = 1_600L
    }
}

// MARK: Screen

/** S29 · the board with white corridors, the ball at the start and a round photo at the goal. */
@Composable
fun MazeScreen(state: MazeUiState, onEvent: (MazeEvent) -> Unit, onStarShown: (Int) -> Unit = {}) {
    TaskGameScaffold(
        onHome = { onEvent(MazeEvent.Home) },
        onRestart = { onEvent(MazeEvent.Restart) },
        progress = if (state.totalTracks > 0) state.trackIndex + (if (state.reached) 1 else 0) to state.totalTracks else null,
        winStars = state.winStars,
        onStarShown = onStarShown,
    ) { scale ->
        val track = state.track ?: return@TaskGameScaffold
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 40.dp * scale, end = 40.dp * scale, top = 124.dp * scale, bottom = 24.dp * scale),
        ) {
            Board(track, state, onEvent, Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun Board(track: Track, state: MazeUiState, onEvent: (MazeEvent) -> Unit, modifier: Modifier) {
    val send by rememberUpdatedState(onEvent)
    val shape = RoundedCornerShape(Dimens.RadiusXL)
    val description = stringResource(R.string.cd_maze_board)
    BoxWithConstraints(
        modifier
            .aspectRatio(TrackFactory.BOARD_W / TrackFactory.BOARD_H)
            .softShadow(shape)
            .clip(shape)
            .background(Palette.Bg2)
            .semantics { contentDescription = description }
            .pointerInput(state.game, state.trackIndex) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val k = TrackFactory.BOARD_W / size.width
                    send(MazeEvent.Down(down.position.x * k, down.position.y * k))
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (change.positionChanged()) {
                            send(MazeEvent.Move(change.position.x * k, change.position.y * k))
                            change.consume()
                        }
                    }
                    send(MazeEvent.Up)
                }
            },
    ) {
        val unit = maxWidth / TrackFactory.BOARD_W
        val corridors = remember(track) { corridorPath(track) }
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / TrackFactory.BOARD_W
            scale(k, k, pivot = Offset.Zero) { drawCorridors(track, corridors, state.trail) }
        }
        val goal = track.nodes[track.goal]
        val goalSize = unit * track.width * TrackFactory.GOAL_PHOTO
        val pop by animateFloatAsState(if (state.reached) 1.25f else 1f, spring(dampingRatio = 0.45f), label = "goal")
        Box(
            Modifier
                .offset(x = unit * goal.x - goalSize / 2, y = unit * goal.y - goalSize / 2)
                .size(goalSize)
                .graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                },
        ) {
            GoalPhoto(state.goal, state.reached)
        }
        Ball(track, state)
    }
}

@Composable
private fun GoalPhoto(photo: PhotoSource?, reached: Boolean) {
    val ring = if (reached) Palette.Accent else Palette.Surface
    Box(
        Modifier
            .fillMaxSize()
            .softShadow(CircleShape)
            .background(Palette.Surface, CircleShape)
            .border(Dimens.StrokeSelected, ring, CircleShape)
            .padding(Dimens.StrokeSelected)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            PhotoImage(photo, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Palette.AccentSoft), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_star), null, tint = Palette.Accent, modifier = Modifier.size(Dimens.IconLarge))
            }
        }
    }
}

/** The ball on top of everything: breathes while waiting, wobbles after a slip, hops after a touch beside it. */
@Composable
private fun Ball(track: Track, state: MazeUiState) {
    val reduced = LocalReducedMotion.current
    val wobble = remember { Animatable(0f) }
    LaunchedEffect(state.slips) {
        if (state.slips > 0 && !reduced) {
            wobble.animateTo(0f, keyframes {
                durationMillis = 360
                -1f at 70
                1f at 160
                -0.5f at 250
            })
        }
    }
    val hop = remember { Animatable(1f) }
    LaunchedEffect(state.nudges) {
        if (state.nudges > 0 && !reduced) {
            hop.animateTo(1.3f, tween(140))
            hop.animateTo(1f, spring(dampingRatio = 0.4f))
        }
    }
    val idle = !state.holding && !state.reached && !reduced
    val breathing = if (idle) {
        val transition = rememberInfiniteTransition(label = "ball")
        transition.animateFloat(1f, 1.1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "breath").value
    } else {
        1f
    }
    val held by animateFloatAsState(if (state.holding) 1.12f else 1f, label = "held")
    val fade by animateFloatAsState(if (state.reached) 0f else 1f, tween(300), label = "fade")
    Canvas(Modifier.fillMaxSize()) {
        val k = size.width / TrackFactory.BOARD_W
        val r = track.width * BALL * k * breathing * hop.value * held
        val center = Offset((state.ball.x + wobble.value * track.width * 0.12f) * k, state.ball.y * k)
        drawBall(center, r, fade)
    }
}

// MARK: Drawing

/** All corridors as one path in board units (round caps join them into a smooth track). */
private fun corridorPath(track: Track): Path = Path().apply {
    track.edges.forEach { (a, b) ->
        moveTo(track.nodes[a].x, track.nodes[a].y)
        lineTo(track.nodes[b].x, track.nodes[b].y)
    }
}

private fun round(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.drawCorridors(track: Track, corridors: Path, trail: List<Point>) {
    drawPath(corridors, Palette.Line2, style = round(track.width + EDGE * 2))
    drawPath(corridors, Palette.Surface, style = round(track.width))
    val start = track.nodes[track.start]
    drawCircle(Palette.Line, track.width * 0.42f, Offset(start.x, start.y), style = Stroke(EDGE))
    if (trail.size > 1) {
        val line = Path().apply { trail.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        drawPath(line, Palette.Accent.copy(alpha = 0.35f), style = round(track.width * 0.34f))
    }
}

private fun DrawScope.drawBall(center: Offset, r: Float, alpha: Float) {
    drawCircle(Palette.Ink.copy(alpha = 0.12f * alpha), r, center + Offset(0f, r * 0.12f))
    drawCircle(Palette.Accent, r, center, alpha = alpha)
    drawCircle(Palette.AccentInk, r, center, alpha = alpha, style = Stroke(r * 0.1f))
    drawArc(
        color = Color.White,
        startAngle = 200f, sweepAngle = 70f, useCenter = false,
        topLeft = center - Offset(r * 0.62f, r * 0.62f),
        size = Size(r * 1.24f, r * 1.24f),
        alpha = 0.7f * alpha,
        style = Stroke(r * 0.16f, cap = StrokeCap.Round),
    )
}

/** Level tile preview (S02 style): the level's hardest shape in the tile's line colour. */
@Composable
fun MiniTrack(shape: TrackShape, color: Color, scale: Float) {
    val track = remember(shape) { TrackFactory(Random(MINI_SEED)).build(shape, MINI_WIDTH) }
    val corridors = remember(track) { corridorPath(track) }
    Canvas(Modifier.size(180.dp * scale, 96.dp * scale)) {
        val k = size.width / TrackFactory.BOARD_W
        scale(k, k, pivot = Offset.Zero) {
            drawPath(corridors, color, style = round(track.width))
            val start = track.nodes[track.start]
            drawCircle(Palette.Accent, track.width * 0.5f, Offset(start.x, start.y))
        }
    }
}

/** Ball radius, in corridor widths. */
private const val BALL = 0.34f

/** Corridor edge line, board units. */
private const val EDGE = 4f
private const val MINI_SEED = 3
private const val MINI_WIDTH = 90f

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun MazePreview() {
    val track = TrackFactory(Random(2)).build(TrackShape.SPIRAL, MazeLevel.L3.width)
    val trail = track.nodes.take(20)
    KidTheme {
        MazeScreen(
            MazeUiState(loading = false, track = track, trackIndex = 1, totalTracks = 3, ball = trail.last(), trail = trail, holding = true),
            onEvent = {},
        )
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun MazeGridPreview() {
    val track = TrackFactory(Random(5)).build(TrackShape.MAZE, MazeLevel.L4.width)
    KidTheme {
        MazeScreen(MazeUiState(loading = false, track = track, trackIndex = 0, totalTracks = 3, ball = track.nodes[track.start]), onEvent = {})
    }
}
