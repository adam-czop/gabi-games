package com.familygames.presentation.games.weather

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.R
import com.familygames.domain.game.round.RoundScoring
import com.familygames.domain.game.weather.Dresser
import com.familygames.domain.game.weather.Garment
import com.familygames.domain.game.weather.WeatherDealer
import com.familygames.domain.game.weather.WeatherKind
import com.familygames.domain.game.weather.WeatherLevel
import com.familygames.domain.game.weather.WeatherRound
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.GameResult
import com.familygames.domain.repository.GameResultRepository
import com.familygames.presentation.common.Sfx
import com.familygames.presentation.common.VoicePrompts
import com.familygames.presentation.common.kidPressable
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

data class WeatherUiState(
    val loading: Boolean = true,
    val round: Int = 0,
    val totalRounds: Int = 0,
    val weather: WeatherKind = WeatherKind.SUN,
    val tray: List<Garment> = emptyList(),
    val wearing: Set<Garment> = emptySet(),
    /** The figure pulses after two wrong things in a row. */
    val hint: Boolean = false,
    val roundDone: Boolean = false,
    val winStars: Int? = null,
    val game: Int = 0,
)

sealed interface WeatherEvent {
    data object Replay : WeatherEvent
    data object Restart : WeatherEvent
    data object Home : WeatherEvent
}

// MARK: ViewModel

/** M29 · "Ubierz na pogodę" (S38): sun, rain and snow — drag what suits the weather onto the child. */
class WeatherViewModel(
    levelKey: String,
    private val dealer: WeatherDealer,
    private val prompts: VoicePrompts,
    private val resources: Resources,
    private val results: GameResultRepository,
    private val clock: Clock,
) : ViewModel() {
    private val level = WeatherLevel.fromKey(levelKey)
    private val state = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = state.asStateFlow()
    private val effects = Channel<GameEffect>(Channel.BUFFERED)
    val effect: Flow<GameEffect> = effects.receiveAsFlow()

    private var rounds: List<WeatherRound> = emptyList()
    private var dresser: Dresser? = null
    private var mistakes = 0
    private var wrongInRow = 0
    private var startedAt = 0L
    private var pending: Job? = null
    private var talking: Job? = null

    init {
        viewModelScope.launch {
            prompts.awaitReady()
            newGame()
        }
    }

    fun onEvent(event: WeatherEvent) {
        when (event) {
            WeatherEvent.Replay -> say(resources.getString(question(state.value.weather)))
            WeatherEvent.Restart -> newGame()
            WeatherEvent.Home -> {
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
        state.update { WeatherUiState(loading = false, totalRounds = rounds.size, game = it.game + 1) }
        showRound(0)
    }

    private fun showRound(index: Int) {
        val round = rounds[index]
        dresser = Dresser(round)
        wrongInRow = 0
        state.update { it.copy(round = index, weather = round.weather, tray = round.tray, wearing = emptySet(), hint = false, roundDone = false) }
        say(resources.getString(question(round.weather)))
    }

    /** Returns true when the thing stays on the child. */
    fun drop(garment: Garment, zone: Int?): Boolean {
        val d = dresser ?: return false
        if (zone == null || state.value.roundDone || state.value.winStars != null) return false
        return when (val outcome = d.put(garment)) {
            Dresser.Outcome.Ignored -> false
            Dresser.Outcome.Wrong -> {
                mistakes++
                wrongInRow++
                effects.trySend(GameEffect.Sound(Sfx.SOFT))
                // Not a "no": the child hears what the thing is for.
                say(resources.getString(goodFor(garment.weather)))
                if (wrongInRow >= 2) state.update { it.copy(hint = true) }
                false
            }
            is Dresser.Outcome.Worn -> {
                wrongInRow = 0
                effects.trySend(GameEffect.Sound(Sfx.MATCH))
                state.update { s -> s.copy(wearing = d.wearing, tray = s.tray - garment, hint = false, roundDone = outcome.dressed) }
                say(resources.getString(name(garment)))
                if (outcome.dressed) {
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
        val total = rounds.sumOf { it.needed.size }
        val stars = RoundScoring.stars(total, mistakes)
        results.save(
            GameResult(
                game = GameKind.WEATHER, levelKey = level.key, stars = stars, moves = total, mistakes = mistakes,
                hintsUsed = 0, durationMs = clock.now() - startedAt, finishedAt = clock.now(),
            ),
        )
        state.update { it.copy(round = rounds.size, winStars = stars) }
        effects.send(GameEffect.Sound(Sfx.WIN))
    }

    private fun say(text: String) {
        talking?.cancel()
        talking = viewModelScope.launch { prompts.play(prompts.phrase(text)) }
    }

    private companion object {
        const val ROUND_PAUSE_MS = 1_500L
    }
}

@StringRes
private fun question(w: WeatherKind): Int = when (w) {
    WeatherKind.SUN -> R.string.weather_q_sun
    WeatherKind.RAIN -> R.string.weather_q_rain
    WeatherKind.SNOW -> R.string.weather_q_snow
}

@StringRes
private fun goodFor(w: WeatherKind): Int = when (w) {
    WeatherKind.SUN -> R.string.weather_for_sun
    WeatherKind.RAIN -> R.string.weather_for_rain
    WeatherKind.SNOW -> R.string.weather_for_snow
}

@StringRes
private fun name(g: Garment): Int = when (g) {
    Garment.CAP -> R.string.garment_cap
    Garment.GLASSES -> R.string.garment_glasses
    Garment.UMBRELLA -> R.string.garment_umbrella
    Garment.BOOTS -> R.string.garment_boots
    Garment.HAT -> R.string.garment_hat
    Garment.MITTENS -> R.string.garment_mittens
}

// MARK: Screen

private const val FIGURE_ZONE = 0
private val Skin = Color(0xFFF3D9C4)
private val Hair = Color(0xFFA9825F)

/** S38 · the window with the weather on the left, the child on the right, things to put on below. */
@Composable
fun WeatherScreen(state: WeatherUiState, onEvent: (WeatherEvent) -> Unit, onDrop: (Garment, Int?) -> Boolean, onStarShown: (Int) -> Unit = {}) {
    val zones = rememberDropZones()
    TaskGameScaffold(
        onHome = { onEvent(WeatherEvent.Home) },
        onRestart = { onEvent(WeatherEvent.Restart) },
        progress = if (state.totalRounds > 0) state.round + (if (state.roundDone) 1 else 0) to state.totalRounds else null,
        winStars = state.winStars,
        onStarShown = onStarShown,
    ) { scale ->
        if (state.loading) return@TaskGameScaffold
        val window = RoundedCornerShape(Dimens.RadiusXL)
        val weatherDescription = stringResource(question(state.weather))
        Box(
            Modifier
                .offset(x = 150.dp * scale, y = 140.dp * scale)
                .size(380.dp * scale)
                .semantics { contentDescription = weatherDescription }
                .softShadow(window)
                .background(Palette.Surface, window)
                .padding(14.dp * scale)
                .clip(RoundedCornerShape(Dimens.RadiusL))
                // A tap on the window says the question again.
                .kidPressable { onEvent(WeatherEvent.Replay) },
        ) {
            Canvas(Modifier.fillMaxSize()) { drawWeather(state.weather) }
        }
        val figureDescription = stringResource(R.string.cd_weather_child)
        Box(
            Modifier
                .offset(x = 690.dp * scale, y = 110.dp * scale)
                .size(380.dp * scale, 450.dp * scale)
                .pulse(state.hint)
                .semantics { contentDescription = figureDescription }
                .dropZone(zones, FIGURE_ZONE),
        ) {
            Canvas(Modifier.fillMaxSize()) { drawChild(state.wearing) }
        }
        key(state.game, state.round) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp * scale),
                horizontalArrangement = Arrangement.spacedBy(40.dp * scale),
            ) {
                state.tray.forEach { g ->
                    key(g) {
                        val description = stringResource(name(g))
                        DraggableItem(
                            zones = zones,
                            modifier = Modifier.size(150.dp * scale).semantics { contentDescription = description },
                            onDrop = { zone -> onDrop(g, zone) },
                        ) {
                            val card = RoundedCornerShape(Dimens.RadiusL)
                            Box(Modifier.matchParentSize().softShadow(card).background(Palette.Surface, card).border(Dimens.Stroke, Palette.Line, card).padding(18.dp * scale)) {
                                Canvas(Modifier.fillMaxSize()) { drawGarmentIcon(g) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: Drawing

private fun DrawScope.outline(path: Path, fill: Color, width: Float = 5f) {
    drawPath(path, fill)
    drawPath(path, Palette.Ink, style = Stroke(width))
}

private fun DrawScope.drawWeather(w: WeatherKind) {
    val sky = when (w) {
        WeatherKind.SUN -> Color(0xFFDDEAF2)
        WeatherKind.RAIN -> Color(0xFFD5DCE1)
        WeatherKind.SNOW -> Color(0xFFE3E8EC)
    }
    drawRect(sky)
    val c = Offset(size.width / 2, size.height * 0.38f)
    when (w) {
        WeatherKind.SUN -> {
            for (i in 0 until 8) {
                val a = i * PI / 4
                drawLine(Palette.Honey, c + Offset(80 * cos(a).toFloat(), 80 * sin(a).toFloat()), c + Offset(110 * cos(a).toFloat(), 110 * sin(a).toFloat()), strokeWidth = 12f, cap = StrokeCap.Round)
            }
            drawCircle(Palette.Honey, 64f, c)
            drawCircle(Palette.Ink, 64f, c, style = Stroke(5f))
        }
        WeatherKind.RAIN, WeatherKind.SNOW -> {
            drawCloud(c)
            for (i in 0 until 12) {
                val x = size.width * (0.12f + 0.08f * i)
                val y = size.height * (0.6f + 0.12f * ((i * 7) % 3))
                if (w == WeatherKind.RAIN) {
                    drawLine(Palette.Mist, Offset(x, y), Offset(x - 8f, y + 34f), strokeWidth = 8f, cap = StrokeCap.Round)
                } else {
                    drawCircle(Color.White, 10f, Offset(x, y))
                    drawCircle(Palette.Line2, 10f, Offset(x, y), style = Stroke(3f))
                }
            }
            if (w == WeatherKind.SNOW) drawRect(Color.White, topLeft = Offset(0f, size.height * 0.88f), size = Size(size.width, size.height * 0.12f))
        }
    }
    // Window bars.
    drawLine(Palette.Surface, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 14f)
    drawLine(Palette.Surface, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 14f)
}

private fun DrawScope.drawCloud(c: Offset) {
    listOf(Offset(-70f, 14f) to 52f, Offset(0f, -20f) to 70f, Offset(72f, 16f) to 50f).forEach { (o, r) ->
        drawCircle(Color.White, r, c + o)
        drawCircle(Palette.Line2, r, c + o, style = Stroke(4f))
    }
    drawRect(Color.White, topLeft = c + Offset(-70f, 14f), size = Size(142f, 52f))
}

/** The child, with what it wears drawn on top. */
private fun DrawScope.drawChild(wearing: Set<Garment>) {
    val w = size.width
    val h = size.height
    val head = Offset(w * 0.5f, h * 0.2f)
    val r = h * 0.11f
    val handL = Offset(w * 0.22f, h * 0.6f)
    val handR = Offset(w * 0.78f, h * 0.6f)
    val footL = Offset(w * 0.42f, h * 0.93f)
    val footR = Offset(w * 0.58f, h * 0.93f)
    if (Garment.UMBRELLA in wearing) drawUmbrella(Rect(w * 0.5f, 0f, w, h * 0.6f), handle = handR)
    // Legs and body.
    drawRoundRect(Palette.Mist, topLeft = Offset(w * 0.38f, h * 0.62f), size = Size(w * 0.1f, h * 0.28f), cornerRadius = CornerRadius(12f))
    drawRoundRect(Palette.Mist, topLeft = Offset(w * 0.52f, h * 0.62f), size = Size(w * 0.1f, h * 0.28f), cornerRadius = CornerRadius(12f))
    drawLine(Palette.Accent, Offset(w * 0.36f, h * 0.38f), handL, strokeWidth = 26f, cap = StrokeCap.Round)
    drawLine(Palette.Accent, Offset(w * 0.64f, h * 0.38f), handR, strokeWidth = 26f, cap = StrokeCap.Round)
    val body = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(w * 0.32f, h * 0.32f, w * 0.68f, h * 0.68f, 30f, 30f)) }
    outline(body, Palette.Accent)
    // Head.
    drawCircle(Skin, r, head)
    drawArc(Hair, 180f, 180f, true, topLeft = head - Offset(r, r), size = Size(r * 2, r * 2))
    drawCircle(Palette.Ink, r, head, style = Stroke(5f))
    drawCircle(Palette.Ink, 5f, head + Offset(-r * 0.38f, r * 0.15f))
    drawCircle(Palette.Ink, 5f, head + Offset(r * 0.38f, r * 0.15f))
    drawArc(Palette.Ink, 20f, 140f, false, topLeft = head + Offset(-r * 0.35f, r * 0.15f), size = Size(r * 0.7f, r * 0.45f), style = Stroke(4f, cap = StrokeCap.Round))
    // Hands and feet, or what covers them.
    if (Garment.MITTENS in wearing) {
        drawMitten(handL, 34f, mirror = true)
        drawMitten(handR, 34f, mirror = false)
    } else {
        drawCircle(Skin, 20f, handL)
        drawCircle(Skin, 20f, handR)
    }
    if (Garment.BOOTS in wearing) {
        drawBoot(footL, 44f)
        drawBoot(footR, 44f)
    } else {
        drawOval(Palette.Ink2, topLeft = footL - Offset(30f, 14f), size = Size(60f, 28f))
        drawOval(Palette.Ink2, topLeft = footR - Offset(30f, 14f), size = Size(60f, 28f))
    }
    if (Garment.CAP in wearing) drawCap(Rect(head.x - r * 1.1f, head.y - r * 1.25f, head.x + r * 1.1f, head.y - r * 0.05f), brimRight = true)
    if (Garment.HAT in wearing) drawHat(Rect(head.x - r * 1.08f, head.y - r * 1.45f, head.x + r * 1.08f, head.y + r * 0.05f))
    if (Garment.GLASSES in wearing) drawGlasses(Rect(head.x - r * 0.9f, head.y - r * 0.05f, head.x + r * 0.9f, head.y + r * 0.45f))
}

/** A thing alone, centred in its tray card. */
private fun DrawScope.drawGarmentIcon(g: Garment) {
    val w = size.width
    val h = size.height
    when (g) {
        Garment.CAP -> drawCap(Rect(w * 0.08f, h * 0.2f, w * 0.78f, h * 0.75f), brimRight = true)
        Garment.HAT -> drawHat(Rect(w * 0.12f, h * 0.05f, w * 0.88f, h * 0.9f))
        Garment.GLASSES -> drawGlasses(Rect(0f, h * 0.3f, w, h * 0.7f))
        Garment.UMBRELLA -> drawUmbrella(Rect(0f, 0f, w, h * 0.7f), handle = Offset(w * 0.5f, h * 0.95f))
        Garment.BOOTS -> {
            drawBoot(Offset(w * 0.3f, h * 0.82f), w * 0.28f)
            drawBoot(Offset(w * 0.72f, h * 0.82f), w * 0.28f)
        }
        Garment.MITTENS -> {
            drawMitten(Offset(w * 0.3f, h * 0.5f), w * 0.26f, mirror = true)
            drawMitten(Offset(w * 0.7f, h * 0.5f), w * 0.26f, mirror = false)
        }
    }
}

private fun DrawScope.drawCap(r: Rect, brimRight: Boolean) {
    val dome = Path().apply {
        moveTo(r.left, r.bottom)
        cubicTo(r.left, r.top - r.height * 0.1f, r.right, r.top - r.height * 0.1f, r.right, r.bottom)
        close()
    }
    outline(dome, Palette.Paints[0])
    val brim = Path().apply {
        val y = r.bottom
        if (brimRight) {
            moveTo(r.center.x, y)
            lineTo(r.right + r.width * 0.45f, y)
            quadraticTo(r.right + r.width * 0.45f, y + r.height * 0.22f, r.right, y + r.height * 0.18f)
            lineTo(r.center.x, y + r.height * 0.12f)
        }
        close()
    }
    outline(brim, Palette.Paints[0])
}

private fun DrawScope.drawHat(r: Rect) {
    val dome = Path().apply {
        moveTo(r.left, r.bottom - r.height * 0.18f)
        cubicTo(r.left, r.top + r.height * 0.1f, r.right, r.top + r.height * 0.1f, r.right, r.bottom - r.height * 0.18f)
        close()
    }
    outline(dome, Palette.Paints[3])
    outline(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(r.left - 4f, r.bottom - r.height * 0.24f, r.right + 4f, r.bottom, 12f, 12f)) }, Palette.Honey)
    drawCircle(Color.White, r.width * 0.12f, Offset(r.center.x, r.top + r.width * 0.08f))
    drawCircle(Palette.Ink, r.width * 0.12f, Offset(r.center.x, r.top + r.width * 0.08f), style = Stroke(4f))
}

private fun DrawScope.drawGlasses(r: Rect) {
    val rad = r.height / 2
    val left = Offset(r.left + rad, r.center.y)
    val right = Offset(r.right - rad, r.center.y)
    drawCircle(Palette.Ink.copy(alpha = 0.75f), rad, left)
    drawCircle(Palette.Ink.copy(alpha = 0.75f), rad, right)
    drawCircle(Palette.Ink, rad, left, style = Stroke(5f))
    drawCircle(Palette.Ink, rad, right, style = Stroke(5f))
    drawLine(Palette.Ink, left + Offset(rad, 0f), right - Offset(rad, 0f), strokeWidth = 6f)
    drawArc(Color.White, 200f, 50f, false, topLeft = left - Offset(rad * 0.7f, rad * 0.7f), size = Size(rad * 1.4f, rad * 1.4f), alpha = 0.6f, style = Stroke(4f))
}

private fun DrawScope.drawUmbrella(r: Rect, handle: Offset) {
    val top = Offset(r.center.x, r.top + r.height * 0.1f)
    drawLine(Palette.Ink, Offset(r.center.x, r.top + r.height * 0.45f), handle, strokeWidth = 7f, cap = StrokeCap.Round)
    drawArc(Palette.Ink, 0f, 180f, false, topLeft = handle - Offset(18f, 0f), size = Size(18f, 22f), style = Stroke(6f))
    val canopy = Path().apply {
        moveTo(r.left + r.width * 0.05f, r.top + r.height * 0.5f)
        cubicTo(r.left + r.width * 0.05f, top.y - r.height * 0.2f, r.right - r.width * 0.05f, top.y - r.height * 0.2f, r.right - r.width * 0.05f, r.top + r.height * 0.5f)
        close()
    }
    outline(canopy, Palette.Paints[4])
}

private fun DrawScope.drawBoot(foot: Offset, s: Float) {
    val boot = Path().apply {
        moveTo(foot.x - s * 0.45f, foot.y - s * 1.3f)
        lineTo(foot.x + s * 0.15f, foot.y - s * 1.3f)
        lineTo(foot.x + s * 0.15f, foot.y - s * 0.4f)
        quadraticTo(foot.x + s * 0.75f, foot.y - s * 0.4f, foot.x + s * 0.75f, foot.y + s * 0.15f)
        lineTo(foot.x - s * 0.45f, foot.y + s * 0.15f)
        close()
    }
    outline(boot, Palette.Honey)
}

private fun DrawScope.drawMitten(c: Offset, s: Float, mirror: Boolean) {
    val dir = if (mirror) -1f else 1f
    drawOval(Palette.Paints[0], topLeft = c + Offset(dir * s * 0.35f - s * 0.22f, -s * 0.25f), size = Size(s * 0.44f, s * 0.36f))
    drawOval(Palette.Ink, topLeft = c + Offset(dir * s * 0.35f - s * 0.22f, -s * 0.25f), size = Size(s * 0.44f, s * 0.36f), style = Stroke(4f))
    drawOval(Palette.Paints[0], topLeft = c - Offset(s * 0.4f, s * 0.5f), size = Size(s * 0.8f, s))
    drawOval(Palette.Ink, topLeft = c - Offset(s * 0.4f, s * 0.5f), size = Size(s * 0.8f, s), style = Stroke(5f))
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun WeatherPreview() {
    KidTheme {
        WeatherScreen(
            WeatherUiState(loading = false, totalRounds = 3, weather = WeatherKind.RAIN, tray = listOf(Garment.BOOTS, Garment.CAP, Garment.MITTENS), wearing = setOf(Garment.UMBRELLA)),
            onEvent = {},
            onDrop = { _, _ -> false },
        )
    }
}
