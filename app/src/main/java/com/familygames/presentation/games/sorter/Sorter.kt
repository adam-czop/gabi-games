package com.familygames.presentation.games.sorter

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.R
import com.familygames.domain.game.board.DropOutcome
import com.familygames.domain.game.round.RoundScoring
import com.familygames.domain.game.sorter.Block
import com.familygames.domain.game.sorter.BlockShape
import com.familygames.domain.game.sorter.SorterBoard
import com.familygames.domain.game.sorter.SorterDealer
import com.familygames.domain.game.sorter.SorterLevel
import com.familygames.domain.game.sorter.SorterRound
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.GameResult
import com.familygames.domain.repository.GameResultRepository
import com.familygames.presentation.common.Sfx
import com.familygames.presentation.common.VoicePrompts
import com.familygames.presentation.common.phrase
import com.familygames.presentation.common.softShadow
import com.familygames.presentation.games.common.DraggableItem
import com.familygames.presentation.games.common.GameEffect
import com.familygames.presentation.games.common.TaskGameScaffold
import com.familygames.presentation.games.common.dropZone
import com.familygames.presentation.games.common.pulse
import com.familygames.presentation.games.common.rememberDropZones
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// MARK: State

data class HoleUi(val shape: BlockShape, val filled: Boolean)

/** A block in the tray; [key] is its place in the round's tray. */
data class BlockUi(val key: Long, val block: Block)

data class SorterUiState(
    val loading: Boolean = true,
    val round: Int = 0,
    val totalRounds: Int = 0,
    val holes: List<HoleUi> = emptyList(),
    val tray: List<BlockUi> = emptyList(),
    val hint: Int? = null,
    val roundDone: Boolean = false,
    val winStars: Int? = null,
    val game: Int = 0,
)

sealed interface SorterEvent {
    data object Restart : SorterEvent
    data object Home : SorterEvent
}

// MARK: ViewModel

/** M26 · "Klocki do dziurek" (S35): drag every block into the hole of its shape; three rounds. */
class SorterViewModel(
    levelKey: String,
    private val dealer: SorterDealer,
    private val prompts: VoicePrompts,
    private val resources: Resources,
    private val results: GameResultRepository,
    private val clock: Clock,
) : ViewModel() {
    private val level = SorterLevel.fromKey(levelKey)
    private val state = MutableStateFlow(SorterUiState())
    val uiState: StateFlow<SorterUiState> = state.asStateFlow()
    private val effects = Channel<GameEffect>(Channel.BUFFERED)
    val effect: Flow<GameEffect> = effects.receiveAsFlow()

    private var rounds: List<SorterRound> = emptyList()
    private var board: SorterBoard? = null
    private val wrongDrops = mutableMapOf<BlockShape, Int>()
    private var mistakes = 0
    private var startedAt = 0L
    private var pending: Job? = null
    private var talking: Job? = null

    init {
        viewModelScope.launch {
            prompts.awaitReady()
            newGame()
        }
    }

    fun onEvent(event: SorterEvent) {
        when (event) {
            SorterEvent.Restart -> newGame()
            SorterEvent.Home -> {
                pending?.cancel()
                talking?.cancel()
                effects.trySend(GameEffect.Home)
            }
        }
    }

    private fun newGame() {
        pending?.cancel()
        rounds = dealer.deal(level)
        mistakes = 0
        startedAt = clock.now()
        state.update { SorterUiState(loading = false, totalRounds = rounds.size, game = it.game + 1) }
        showRound(0)
        say(prompts.phrase(resources.getString(R.string.sorter_intro)))
    }

    private fun showRound(index: Int) {
        val round = rounds[index]
        board = SorterBoard(round.holes)
        wrongDrops.clear()
        state.update {
            it.copy(
                round = index, holes = round.holes.map { s -> HoleUi(s, filled = false) },
                tray = round.tray.mapIndexed { i, b -> BlockUi(i.toLong(), b) }, hint = null, roundDone = false,
            )
        }
    }

    /** Returns true when the block went into its hole. */
    fun drop(blockKey: Long, hole: Int?): Boolean {
        val b = board ?: return false
        val block = state.value.tray.firstOrNull { it.key == blockKey }?.block ?: return false
        if (hole == null || state.value.roundDone || state.value.winStars != null) return false
        return when (val outcome = b.drop(block.shape, hole)) {
            DropOutcome.Ignored -> false
            DropOutcome.Wrong -> {
                mistakes++
                val wrong = (wrongDrops[block.shape] ?: 0) + 1
                wrongDrops[block.shape] = wrong
                effects.trySend(GameEffect.Sound(Sfx.SOFT))
                if (wrong >= 2) state.update { it.copy(hint = b.holeOf(block.shape)) }
                false
            }
            is DropOutcome.Correct -> {
                effects.trySend(GameEffect.Sound(Sfx.SNAP))
                effects.trySend(GameEffect.Sound(Sfx.POP, 0.8f))
                state.update { s ->
                    s.copy(
                        holes = s.holes.mapIndexed { i, h -> if (i == hole) h.copy(filled = true) else h },
                        tray = s.tray.filterNot { it.key == blockKey },
                        hint = if (s.hint == hole) null else s.hint,
                        roundDone = outcome.won,
                    )
                }
                say(prompts.phrase(resources.getString(shapeName(block.shape))))
                if (outcome.won) {
                    pending = viewModelScope.launch {
                        delay(ROUND_PAUSE_MS)
                        talking?.join()
                        val next = state.value.round + 1
                        if (next < rounds.size) showRound(next) else finish()
                    }
                }
                true
            }
        }
    }

    private suspend fun finish() {
        val total = rounds.sumOf { it.holes.size }
        val stars = RoundScoring.stars(total, mistakes)
        results.save(
            GameResult(
                game = GameKind.SORTER, levelKey = level.key, stars = stars, moves = total, mistakes = mistakes,
                hintsUsed = 0, durationMs = clock.now() - startedAt, finishedAt = clock.now(),
            ),
        )
        state.update { it.copy(round = rounds.size, winStars = stars) }
        effects.send(GameEffect.Sound(Sfx.WIN))
    }

    private fun say(steps: List<com.familygames.domain.voice.VoiceStep>) {
        talking?.cancel()
        talking = viewModelScope.launch { prompts.play(steps) }
    }

    private companion object {
        const val ROUND_PAUSE_MS = 1_300L
    }
}

@StringRes
private fun shapeName(shape: BlockShape): Int = when (shape) {
    BlockShape.CIRCLE -> R.string.shape_circle
    BlockShape.SQUARE -> R.string.shape_square
    BlockShape.TRIANGLE -> R.string.shape_triangle
    BlockShape.STAR -> R.string.shape_star
    BlockShape.HEART -> R.string.shape_heart
    BlockShape.MOON -> R.string.shape_moon
}

// MARK: Screen

/** S35 · the lid with holes on top, the blocks below. */
@Composable
fun SorterScreen(state: SorterUiState, onEvent: (SorterEvent) -> Unit, onDrop: (Long, Int?) -> Boolean, onStarShown: (Int) -> Unit = {}) {
    val zones = rememberDropZones()
    TaskGameScaffold(
        onHome = { onEvent(SorterEvent.Home) },
        onRestart = { onEvent(SorterEvent.Restart) },
        progress = if (state.totalRounds > 0) state.round + (if (state.roundDone) 1 else 0) to state.totalRounds else null,
        winStars = state.winStars,
        onStarShown = onStarShown,
    ) { scale ->
        if (state.holes.isEmpty()) return@TaskGameScaffold
        val lid = RoundedCornerShape(Dimens.RadiusXL)
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 124.dp * scale)
                .softShadow(lid)
                .background(Palette.Bg2, lid)
                .border(Dimens.Stroke, Palette.Line2, lid)
                .padding(horizontal = 40.dp * scale, vertical = 36.dp * scale),
            horizontalArrangement = Arrangement.spacedBy(48.dp * scale),
        ) {
            state.holes.forEachIndexed { i, hole ->
                Hole(hole, i, 150.dp * scale, pulse = state.hint == i, modifier = Modifier.dropZone(zones, i))
            }
        }
        key(state.game, state.round) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp * scale),
                horizontalArrangement = Arrangement.spacedBy(48.dp * scale),
            ) {
                state.tray.forEach { item ->
                    key(item.key) {
                        DraggableItem(zones = zones, modifier = Modifier.size(140.dp * scale), onDrop = { zone -> onDrop(item.key, zone) }) {
                            Canvas(Modifier.fillMaxSize()) { drawBlock(item.block.shape, BlockColors[item.block.color % BlockColors.size]) }
                        }
                    }
                }
            }
        }
    }
}

/** Block colours: crayon paints of Kolorowanki — colour is content here, as with the marbles (ADR-032). */
private val BlockColors = listOf(Palette.Paints[0], Palette.Paints[1], Palette.Paints[2], Palette.Paints[3])

@Composable
private fun Hole(hole: HoleUi, index: Int, size: Dp, pulse: Boolean, modifier: Modifier) {
    val description = stringResource(R.string.cd_sorter_hole, index + 1, stringResource(shapeName(hole.shape)))
    // A filled hole shows the block sunk in: smaller, dimmed, then gone.
    val sunk by animateFloatAsState(if (hole.filled) 1f else 0f, tween(450), label = "sunk")
    Box(modifier.size(size).pulse(pulse).semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxSize()) {
            val p = shapePath(hole.shape, this.size)
            drawPath(p, Palette.Ink.copy(alpha = 0.55f))
            drawPath(p, Palette.Ink.copy(alpha = 0.25f), style = Stroke(10f, join = StrokeJoin.Round))
            if (pulse) drawPath(p, Palette.Accent, style = Stroke(8f, join = StrokeJoin.Round))
        }
        if (sunk > 0f) {
            Canvas(
                Modifier.fillMaxSize().graphicsLayer {
                    val s = 1f - 0.25f * sunk
                    scaleX = s
                    scaleY = s
                    alpha = 1f - sunk * 0.6f
                },
            ) { drawPath(shapePath(hole.shape, this.size), Palette.Accent.copy(alpha = 0.5f)) }
        }
    }
}

private fun DrawScope.drawBlock(shape: BlockShape, color: Color) {
    val p = shapePath(shape, size)
    translate(0f, size.height * 0.04f) { drawPath(p, Palette.Ink.copy(alpha = 0.14f)) }
    drawPath(p, color)
    drawPath(p, Palette.Ink, style = Stroke(5f, join = StrokeJoin.Round))
}

/** The six shapes in a [size] box, with a little margin. */
internal fun shapePath(shape: BlockShape, size: Size): Path {
    val w = size.width
    val h = size.height
    val m = minOf(w, h) * 0.06f
    return when (shape) {
        BlockShape.CIRCLE -> Path().apply { addOval(Rect(m, m, w - m, h - m)) }
        BlockShape.SQUARE -> Path().apply { addRoundRect(RoundRect(m * 1.5f, m * 1.5f, w - m * 1.5f, h - m * 1.5f, w * 0.12f, h * 0.12f)) }
        BlockShape.TRIANGLE -> Path().apply {
            moveTo(w / 2, m)
            lineTo(w - m, h - m * 1.6f)
            lineTo(m, h - m * 1.6f)
            close()
        }
        BlockShape.STAR -> Path().apply {
            val cx = w / 2
            val cy = h * 0.53f
            val outer = minOf(w, h) / 2 - m
            val inner = outer * 0.45f
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) outer else inner
                val a = -PI / 2 + i * PI / 5
                val x = cx + (r * cos(a)).toFloat()
                val y = cy + (r * sin(a)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        BlockShape.HEART -> Path().apply {
            moveTo(w / 2, h * 0.92f)
            cubicTo(w * -0.05f, h * 0.55f, w * 0.12f, h * 0.02f, w / 2, h * 0.28f)
            cubicTo(w * 0.88f, h * 0.02f, w * 1.05f, h * 0.55f, w / 2, h * 0.92f)
            close()
        }
        BlockShape.MOON -> Path.combine(
            PathOperation.Difference,
            Path().apply { addOval(Rect(m, m, w - m, h - m)) },
            Path().apply { addOval(Rect(Offset(w * 0.68f, h * 0.36f), w * 0.36f)) },
        )
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun SorterPreview() {
    KidTheme {
        SorterScreen(
            SorterUiState(
                loading = false, round = 1, totalRounds = 3,
                holes = listOf(HoleUi(BlockShape.STAR, false), HoleUi(BlockShape.CIRCLE, true), HoleUi(BlockShape.HEART, false), HoleUi(BlockShape.MOON, false)),
                tray = listOf(BlockUi(0, Block(BlockShape.MOON, 2)), BlockUi(1, Block(BlockShape.HEART, 0)), BlockUi(2, Block(BlockShape.STAR, 1))),
            ),
            onEvent = {},
            onDrop = { _, _ -> false },
        )
    }
}
