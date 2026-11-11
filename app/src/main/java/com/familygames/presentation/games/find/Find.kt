package com.familygames.presentation.games.find

import android.content.res.Resources
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withRotation
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.R
import com.familygames.domain.game.find.FindGame
import com.familygames.domain.game.find.FindLevel
import com.familygames.domain.game.find.FindOutcome
import com.familygames.domain.game.find.Scene
import com.familygames.domain.game.find.SceneObject
import com.familygames.domain.game.find.SceneShape
import com.familygames.domain.game.find.Scenes
import com.familygames.domain.game.round.RoundScoring
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.GameResult
import com.familygames.domain.repository.GameResultRepository
import com.familygames.domain.voice.VoiceStep
import com.familygames.presentation.common.LocalReducedMotion
import com.familygames.presentation.common.RoundIconButton
import com.familygames.presentation.common.Sfx
import com.familygames.presentation.common.VoicePrompts
import com.familygames.presentation.common.phrase
import com.familygames.presentation.common.softShadow
import com.familygames.presentation.games.common.FreePlayClock
import com.familygames.presentation.games.common.FreePlayScaffold
import com.familygames.presentation.games.common.GameEffect
import com.familygames.presentation.games.common.ProgressDots
import com.familygames.presentation.games.common.TaskGameScaffold
import com.familygames.presentation.games.whereis.SpeakerButton
import com.familygames.presentation.theme.Dimens
import com.familygames.presentation.theme.KidTheme
import com.familygames.presentation.theme.Palette
import com.familygames.util.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

// MARK: Rendering

/**
 * Draws a scene with android.graphics, so the game, the target card and the tests share one renderer.
 * Per-object [scaleOf] / [rotationOf] animate a found or a wrongly tapped thing around its own centre.
 */
object SceneRenderer {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun draw(canvas: android.graphics.Canvas, scene: Scene, k: Float, scaleOf: (String) -> Float = { 1f }, rotationOf: (String) -> Float = { 0f }) {
        canvas.withScale(k, k) {
            scene.background.forEach { drawShape(this, it) }
            scene.objects.forEach { o ->
                val b = o.bounds
                val cx = (b[0] + b[2]) / 2
                val cy = (b[1] + b[3]) / 2
                val s = scaleOf(o.key)
                withRotation(rotationOf(o.key), cx, cy) {
                    withScale(s, s, cx, cy) { o.shapes.forEach { drawShape(this, it) } }
                }
            }
        }
    }

    /** One object alone, fitted into a [size] × [size] square (the "find this" card). */
    fun drawObject(canvas: android.graphics.Canvas, obj: SceneObject, size: Float) {
        val b = obj.bounds
        val w = b[2] - b[0]
        val h = b[3] - b[1]
        val k = size * 0.82f / maxOf(w, h)
        canvas.withTranslation(size / 2 - (b[0] + w / 2) * k, size / 2 - (b[1] + h / 2) * k) {
            withScale(k, k) { obj.shapes.forEach { drawShape(this, it) } }
        }
    }

    private fun drawShape(canvas: android.graphics.Canvas, s: SceneShape) {
        line.color = INK
        line.strokeWidth = OUTLINE
        when (s) {
            is SceneShape.Rect -> {
                val r = RectF(s.x, s.y, s.x + s.w, s.y + s.h)
                s.fill?.let { fill.color = it; canvas.drawRoundRect(r, s.radius, s.radius, fill) }
                if (s.outline) canvas.drawRoundRect(r, s.radius, s.radius, line)
            }
            is SceneShape.Oval -> {
                val r = RectF(s.cx - s.rx, s.cy - s.ry, s.cx + s.rx, s.cy + s.ry)
                s.fill?.let { fill.color = it; canvas.drawOval(r, fill) }
                if (s.outline) canvas.drawOval(r, line)
            }
            is SceneShape.Poly -> {
                val p = android.graphics.Path().apply {
                    s.xs.indices.forEach { i -> if (i == 0) moveTo(s.xs[i], s.ys[i]) else lineTo(s.xs[i], s.ys[i]) }
                    close()
                }
                s.fill?.let { fill.color = it; canvas.drawPath(p, fill) }
                if (s.outline) canvas.drawPath(p, line)
            }
            is SceneShape.Line -> {
                line.color = s.color
                line.strokeWidth = s.width
                val p = android.graphics.Path().apply { s.xs.indices.forEach { i -> if (i == 0) moveTo(s.xs[i], s.ys[i]) else lineTo(s.xs[i], s.ys[i]) } }
                canvas.drawPath(p, line)
            }
        }
    }

    private const val OUTLINE = 5f
    private const val INK = 0xFF3B3936.toInt()
}

// MARK: State

/** Something just tapped: [n] grows with every tap, so the same thing can bounce twice. */
data class TapFx(val key: String, val n: Int)

data class FindUiState(
    val loading: Boolean = true,
    val free: Boolean = false,
    val scene: Scene? = null,
    val target: SceneObject? = null,
    val round: Int = 0,
    val totalRounds: Int = 0,
    val solved: Boolean = false,
    val bounce: TapFx? = null,
    val wiggle: TapFx? = null,
    /** Object that pulses after two misses. */
    val hint: String? = null,
    val winStars: Int? = null,
    val game: Int = 0,
)

sealed interface FindEvent {
    data class Tap(val x: Float, val y: Float) : FindEvent
    data object Replay : FindEvent
    data object NextScene : FindEvent
    data object Restart : FindEvent
    data object Home : FindEvent
}

// MARK: ViewModel

/** M23 · "Znajdź na obrazku" (S32): questions about a built-in scene, or free exploring with names spoken. */
class FindViewModel(
    levelKey: String,
    private val prompts: VoicePrompts,
    private val resources: Resources,
    private val results: GameResultRepository,
    private val freePlay: FreePlayClock,
    private val clock: Clock,
    private val random: Random,
) : ViewModel() {
    private val level = FindLevel.fromKey(levelKey)
    private val state = MutableStateFlow(FindUiState(free = level.isFree))
    val uiState: StateFlow<FindUiState> = state.asStateFlow()
    private val effects = Channel<GameEffect>(Channel.BUFFERED)
    val effect: Flow<GameEffect> = effects.receiveAsFlow()

    private var game: FindGame? = null
    private var taps = 0
    private var startedAt = 0L
    private var talking: Job? = null
    private var pending: Job? = null

    init {
        viewModelScope.launch {
            prompts.awaitReady()
            if (level.isFree) {
                showScene(FindGame.pickScene(random, null))
                freePlay.start()
            } else {
                newGame()
            }
        }
    }

    fun onEvent(event: FindEvent) {
        when (event) {
            is FindEvent.Tap -> tap(event.x, event.y)
            FindEvent.Replay -> game?.let { g -> if (!state.value.solved) say(question(g.current)) }
            FindEvent.NextScene -> state.value.scene?.let { showScene(FindGame.pickScene(random, it.key)) }
            FindEvent.Restart -> viewModelScope.launch { newGame() }
            FindEvent.Home -> {
                talking?.cancel()
                pending?.cancel()
                viewModelScope.launch {
                    if (level.isFree) withContext(NonCancellable) { freePlay.stop(GameKind.FIND) }
                    effects.send(GameEffect.Home)
                }
            }
        }
    }

    private fun showScene(scene: Scene) {
        talking?.cancel()
        state.update { it.copy(loading = false, scene = scene, bounce = null, wiggle = null, game = it.game + 1) }
    }

    private fun newGame() {
        pending?.cancel()
        val g = FindGame.deal(FindGame.pickScene(random, state.value.scene?.key), level, random).also { game = it }
        startedAt = clock.now()
        state.update {
            FindUiState(loading = false, scene = g.scene, target = g.current, totalRounds = g.targets.size, game = it.game + 1)
        }
        say(question(g.current))
    }

    private fun tap(x: Float, y: Float) {
        val s = state.value
        val scene = s.scene ?: return
        if (s.winStars != null) return
        val hit = scene.objectAt(x, y)
        if (level.isFree) {
            hit ?: return
            state.update { it.copy(bounce = TapFx(hit.key, ++taps)) }
            sound(Sfx.TAP, 0.9f + random.nextFloat() * 0.3f)
            say(nameAndSound(hit.key))
            return
        }
        val g = game ?: return
        when (val outcome = g.tap(hit?.key)) {
            FindOutcome.Ignored -> Unit
            is FindOutcome.Wrong -> {
                state.update { it.copy(wiggle = TapFx(hit!!.key, ++taps), hint = if (outcome.hint) g.current.key else it.hint) }
                sound(Sfx.SOFT)
                pending?.cancel()
                pending = viewModelScope.launch {
                    delay(REASK_MS)
                    say(question(g.current))
                }
            }
            is FindOutcome.Correct -> {
                pending?.cancel()
                state.update { it.copy(bounce = TapFx(g.current.key, ++taps), solved = true, hint = null) }
                sound(Sfx.MATCH)
                val found = g.current.key
                pending = viewModelScope.launch {
                    val started = clock.now()
                    talking?.cancel()
                    prompts.play(nameAndSound(found))
                    val left = FEEDBACK_MS - (clock.now() - started)
                    if (left > 0) delay(left)
                    if (outcome.last) {
                        finish(g)
                    } else {
                        g.next()
                        state.update { it.copy(round = g.round, target = g.current, solved = false, bounce = null, wiggle = null) }
                        say(question(g.current))
                    }
                }
            }
        }
    }

    private suspend fun finish(g: FindGame) {
        val stars = RoundScoring.stars(g.targets.size, g.mistakes)
        results.save(
            GameResult(
                game = GameKind.FIND, levelKey = level.key, stars = stars, moves = g.targets.size, mistakes = g.mistakes,
                hintsUsed = 0, durationMs = clock.now() - startedAt, finishedAt = clock.now(),
            ),
        )
        state.update { it.copy(round = g.targets.size, winStars = stars) }
        sound(Sfx.WIN)
    }

    private fun question(obj: SceneObject): List<VoiceStep> = prompts.phrase(resources.getString(FindTexts.question(obj.key)))

    private fun nameAndSound(key: String): List<VoiceStep> =
        prompts.phrase(resources.getString(FindTexts.name(key))) + (FindTexts.sound(key)?.let { prompts.phrase(resources.getString(it)) } ?: emptyList())

    private fun say(steps: List<VoiceStep>) {
        talking?.cancel()
        talking = viewModelScope.launch { prompts.play(steps) }
    }

    private fun sound(sfx: Sfx, rate: Float = 1f) {
        effects.trySend(GameEffect.Sound(sfx, rate))
    }

    private companion object {
        const val REASK_MS = 600L
        const val FEEDBACK_MS = 1_500L
    }
}

// MARK: Screens

/** S32 · task mode: the "find this" card with the speaker on top, the scene below. */
@Composable
fun FindScreen(state: FindUiState, onEvent: (FindEvent) -> Unit, onStarShown: (Int) -> Unit = {}) {
    if (state.free) {
        FreePlayScaffold(onHome = { onEvent(FindEvent.Home) }) { scale ->
            val scene = state.scene ?: return@FreePlayScaffold
            SceneBoard(scene, state, onEvent, stringResource(R.string.cd_find_explore), scale)
            RoundIconButton(
                R.drawable.ic_next, stringResource(R.string.cd_next_scene), onClick = { onEvent(FindEvent.NextScene) },
                modifier = Modifier.align(Alignment.TopEnd).padding(end = Dimens.ScreenPaddingH, top = Dimens.ScreenPaddingTop),
            )
        }
        return
    }
    TaskGameScaffold(
        onHome = { onEvent(FindEvent.Home) },
        onRestart = { onEvent(FindEvent.Restart) },
        progress = null,
        winStars = state.winStars,
        onStarShown = onStarShown,
    ) { scale ->
        val scene = state.scene ?: return@TaskGameScaffold
        SceneBoard(scene, state, onEvent, stringResource(R.string.cd_find_scene), scale)
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 14.dp * scale),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp * scale),
        ) {
            SpeakerButton(scale) { onEvent(FindEvent.Replay) }
            state.target?.let { TargetCard(it, 96.dp * scale) }
            ProgressDots(done = state.round + (if (state.solved) 1 else 0), total = state.totalRounds, scale = scale)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.SceneBoard(scene: Scene, state: FindUiState, onEvent: (FindEvent) -> Unit, description: String, scale: Float) {
    val send by rememberUpdatedState(onEvent)
    val reduced = LocalReducedMotion.current
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(state.bounce) {
        if (state.bounce != null && !reduced) {
            bounce.snapTo(1f)
            bounce.animateTo(1.18f, tween(140))
            bounce.animateTo(1f, tween(260))
        }
    }
    val wiggle = remember { Animatable(0f) }
    LaunchedEffect(state.wiggle) {
        if (state.wiggle != null && !reduced) {
            wiggle.animateTo(0f, keyframes {
                durationMillis = 320
                -5f at 60
                5f at 150
                -3f at 240
            })
        }
    }
    val pulse = if (state.hint != null && !reduced) {
        rememberInfiniteTransition(label = "hint").animateFloat(1f, 1.1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "hint").value
    } else {
        1f
    }
    val shape = RoundedCornerShape(Dimens.RadiusXL)
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 40.dp * scale, end = 40.dp * scale, top = 124.dp * scale, bottom = 24.dp * scale)
            .aspectRatio(Scene.WIDTH / Scene.HEIGHT)
            .softShadow(shape)
            .clip(shape)
            .semantics { contentDescription = description }
            .pointerInput(state.game) {
                detectTapGestures { p ->
                    val k = Scene.WIDTH / size.width
                    send(FindEvent.Tap(p.x * k, p.y * k))
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / Scene.WIDTH
            drawIntoCanvas { c ->
                SceneRenderer.draw(
                    c.nativeCanvas, scene, k,
                    scaleOf = { key ->
                        when (key) {
                            state.bounce?.key -> bounce.value
                            state.hint -> pulse
                            else -> 1f
                        }
                    },
                    rotationOf = { key -> if (key == state.wiggle?.key) wiggle.value else 0f },
                )
            }
        }
    }
}

/** The thing to find, drawn alone on a white card: works even without any voice. */
@Composable
private fun TargetCard(obj: SceneObject, size: androidx.compose.ui.unit.Dp) {
    val description = stringResource(R.string.cd_find_target)
    Box(
        Modifier
            .size(size)
            .semantics { contentDescription = description }
            .softShadow(CircleShape)
            .background(Palette.Surface, CircleShape)
            .border(Dimens.StrokeSelected, Palette.Accent, CircleShape)
            .clip(CircleShape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { c -> SceneRenderer.drawObject(c.nativeCanvas, obj, this.size.width) }
        }
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun FindPreview() {
    val scene = Scenes.all.first()
    KidTheme {
        FindScreen(FindUiState(loading = false, scene = scene, target = scene.objects[4], round = 1, totalRounds = 5), onEvent = {})
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun FindExplorePreview() {
    KidTheme { FindScreen(FindUiState(loading = false, free = true, scene = Scenes.all[2]), onEvent = {}) }
}
