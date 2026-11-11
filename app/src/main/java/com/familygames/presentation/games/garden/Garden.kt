package com.familygames.presentation.games.garden

import android.content.res.Resources
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.familygames.domain.game.garden.Garden
import com.familygames.domain.game.garden.GardenOutcome
import com.familygames.domain.game.garden.Plot
import com.familygames.domain.game.garden.Stage
import com.familygames.domain.model.GameKind
import com.familygames.presentation.common.LocalReducedMotion
import com.familygames.presentation.common.Sfx
import com.familygames.presentation.common.VoicePrompts
import com.familygames.presentation.common.phrase
import com.familygames.presentation.common.softShadow
import com.familygames.presentation.games.common.FreePlayClock
import com.familygames.presentation.games.common.FreePlayScaffold
import com.familygames.presentation.games.common.GameEffect
import com.familygames.presentation.theme.Dimens
import com.familygames.presentation.theme.KidTheme
import com.familygames.presentation.theme.Palette
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// MARK: Layout (board units, 1200 × 640)

private const val BOARD_W = 1200f
private const val BOARD_H = 640f
private const val SOIL_Y = 430f
private val BED_X = listOf(330f, 520f, 710f, 900f)
private val CAN = Offset(140f, 470f)
private val SUN = Offset(1000f, 120f)
private val VASE = Offset(1100f, 560f)

// MARK: State

data class GardenUiState(
    val beds: List<Plot> = List(Garden.BEDS) { Plot() },
    val vase: List<Int> = emptyList(),
    val sunny: Boolean = false,
    /** Grows with every watering: the drops fall again. */
    val watering: Int = 0,
)

// MARK: ViewModel

/** M28 · "Ogródek" (S37): sow, water, let the sun out, pick the flowers — free play. */
class GardenViewModel(
    private val prompts: VoicePrompts,
    private val resources: Resources,
    private val freePlay: FreePlayClock,
    random: Random,
) : ViewModel() {
    private val garden = Garden(random)
    private val state = MutableStateFlow(GardenUiState())
    val uiState: StateFlow<GardenUiState> = state.asStateFlow()
    private val effects = Channel<GameEffect>(Channel.BUFFERED)
    val effect: Flow<GameEffect> = effects.receiveAsFlow()
    private var cloudBack: Job? = null
    private var talking: Job? = null

    init {
        viewModelScope.launch {
            prompts.awaitReady()
            freePlay.start()
        }
    }

    /** A tap on the board, in board units. */
    fun tap(x: Float, y: Float) {
        val p = Offset(x, y)
        when {
            (p - CAN).getDistance() <= TOUCH -> water()
            (p - SUN).getDistance() <= TOUCH + 20f -> sunshine()
            else -> BED_X.indexOfFirst { bx -> x in bx - BED_HALF..bx + BED_HALF && y in 220f..BOARD_H }.takeIf { it >= 0 }?.let { bed(it) }
        }
    }

    private fun bed(i: Int) {
        when (val outcome = garden.tapBed(i)) {
            GardenOutcome.Planted -> sound(Sfx.TAP, 0.9f)
            is GardenOutcome.Picked -> sound(Sfx.POP, 1.1f)
            // A growing plant only rustles softly: it needs water or sun, not taps.
            else -> sound(Sfx.SOFT, 1.2f)
        }
        publish()
    }

    private fun water() {
        val outcome = garden.water() as GardenOutcome.Watered
        sound(Sfx.SNAP)
        state.update { it.copy(watering = it.watering + 1) }
        if (outcome.grown > 0) viewModelScope.launch {
            delay(GROW_DELAY_MS)
            sound(Sfx.STAR, 0.8f)
            publish()
        }
    }

    private fun sunshine() {
        val outcome = garden.sun() as GardenOutcome.Sunshine
        state.update { it.copy(sunny = true) }
        if (outcome.bloomed > 0) {
            sound(Sfx.MATCH)
            talking?.cancel()
            talking = viewModelScope.launch { prompts.play(prompts.phrase(resources.getString(R.string.garden_bloomed))) }
        } else {
            sound(Sfx.TAP, 1.3f)
        }
        publish()
        cloudBack?.cancel()
        cloudBack = viewModelScope.launch {
            delay(SUN_MS)
            state.update { it.copy(sunny = false) }
        }
    }

    private fun publish() {
        state.update { it.copy(beds = garden.beds, vase = garden.vase) }
    }

    fun home() {
        talking?.cancel()
        viewModelScope.launch {
            withContext(NonCancellable) { freePlay.stop(GameKind.GARDEN) }
            effects.send(GameEffect.Home)
        }
    }

    private fun sound(sfx: Sfx, rate: Float = 1f) {
        effects.trySend(GameEffect.Sound(sfx, rate))
    }

    private companion object {
        const val TOUCH = 100f
        const val BED_HALF = 85f
        const val GROW_DELAY_MS = 700L
        const val SUN_MS = 4_000L
    }
}

// MARK: Screen

private val FlowerColors = Palette.Paints.take(Garden.COLORS)
private val Sky = Color(0xFFE6ECF0)
private val Soil = Color(0xFFC9A27E)
private val SoilDark = Color(0xFFA9825F)
private val Leaf = Color(0xFF81B29A)
private val LeafDark = Color(0xFF5E8C76)

/** S37 · sky with the cloud and the sun, four beds, the watering can on the left and the vase on the right. */
@Composable
fun GardenScreen(state: GardenUiState, onTap: (Float, Float) -> Unit, onHome: () -> Unit) {
    FreePlayScaffold(onHome = onHome) { scale ->
        val send by rememberUpdatedState(onTap)
        val reduced = LocalReducedMotion.current
        val cloud by animateFloatAsState(if (state.sunny) 1f else 0f, tween(700), label = "cloud")
        val drops = remember { Animatable(1f) }
        LaunchedEffect(state.watering) {
            if (state.watering > 0 && !reduced) {
                drops.snapTo(0f)
                drops.animateTo(1f, tween(900))
            }
        }
        val shape = RoundedCornerShape(Dimens.RadiusXL)
        val description = stringResource(R.string.cd_garden_board)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 40.dp * scale, end = 40.dp * scale, top = 124.dp * scale, bottom = 24.dp * scale)
                .aspectRatio(BOARD_W / BOARD_H)
                .softShadow(shape)
                .clip(shape)
                .semantics { contentDescription = description }
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val k = BOARD_W / size.width
                        send(p.x * k, p.y * k)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val k = size.width / BOARD_W
                scale(k, k, pivot = Offset.Zero) {
                    drawRect(Sky, size = Size(BOARD_W, SOIL_Y))
                    drawSun(SUN, glow = cloud)
                    drawCloud(SUN + Offset(-30f + 260f * cloud, 30f - 30f * cloud))
                    drawRect(Soil, topLeft = Offset(0f, SOIL_Y), size = Size(BOARD_W, BOARD_H - SOIL_Y))
                    state.beds.forEachIndexed { i, plot -> drawBed(BED_X[i], plot) }
                    drawCan(CAN)
                    if (drops.value < 1f) drawDrops(drops.value)
                    drawVase(VASE, state.vase)
                }
            }
        }
    }
}

private fun DrawScope.drawSun(c: Offset, glow: Float) {
    val r = 56f + 6f * glow
    for (i in 0 until 8) {
        val a = i * PI / 4
        val from = c + Offset((r + 14) * cos(a).toFloat(), (r + 14) * sin(a).toFloat())
        val to = c + Offset((r + 40) * cos(a).toFloat(), (r + 40) * sin(a).toFloat())
        drawLine(Palette.Honey, from, to, strokeWidth = 9f, cap = StrokeCap.Round)
    }
    drawCircle(Palette.Honey, r, c)
    drawCircle(Palette.Ink, r, c, style = Stroke(5f))
}

private fun DrawScope.drawCloud(c: Offset) {
    listOf(Offset(-60f, 10f) to 50f, Offset(0f, -20f) to 66f, Offset(64f, 12f) to 48f).forEach { (o, r) ->
        drawCircle(Color.White, r, c + o)
        drawCircle(Palette.Line2, r, c + o, style = Stroke(4f))
    }
    drawRect(Color.White, topLeft = c + Offset(-60f, 10f), size = Size(124f, 50f))
    drawLine(Palette.Line2, c + Offset(-80f, 60f), c + Offset(84f, 60f), strokeWidth = 4f)
}

private fun DrawScope.drawBed(x: Float, plot: Plot) {
    drawOval(SoilDark, topLeft = Offset(x - 80f, SOIL_Y + 10f), size = Size(160f, 46f))
    val base = Offset(x, SOIL_Y + 22f)
    val color = FlowerColors[plot.color % FlowerColors.size]
    when (plot.stage) {
        Stage.EMPTY -> Unit
        Stage.SEED -> {
            drawOval(Soil, topLeft = base + Offset(-34f, -26f), size = Size(68f, 34f))
            drawOval(SoilDark, topLeft = base + Offset(-34f, -26f), size = Size(68f, 34f), style = Stroke(4f))
        }
        else -> {
            val height = when (plot.stage) {
                Stage.SPROUT -> 50f
                Stage.LEAVES -> 100f
                else -> 160f
            }
            val top = base - Offset(0f, height)
            drawLine(LeafDark, base, top, strokeWidth = 10f, cap = StrokeCap.Round)
            val leaves = if (plot.stage == Stage.SPROUT) 1 else 2
            for (i in 0 until leaves) {
                val y = base.y - height * (0.35f + 0.3f * i)
                drawLeaf(Offset(x, y), left = true)
                drawLeaf(Offset(x, y), left = false)
            }
            when (plot.stage) {
                Stage.BUD -> {
                    drawOval(color, topLeft = top + Offset(-20f, -46f), size = Size(40f, 54f))
                    drawOval(Palette.Ink, topLeft = top + Offset(-20f, -46f), size = Size(40f, 54f), style = Stroke(4f))
                }
                Stage.FLOWER -> {
                    for (p in 0 until 6) {
                        val a = p * PI / 3
                        val c = top + Offset(34f * cos(a).toFloat(), 34f * sin(a).toFloat())
                        drawCircle(color, 26f, c)
                        drawCircle(Palette.Ink, 26f, c, style = Stroke(4f))
                    }
                    drawCircle(Palette.Honey, 22f, top)
                    drawCircle(Palette.Ink, 22f, top, style = Stroke(4f))
                }
                else -> Unit
            }
        }
    }
}

private fun DrawScope.drawLeaf(at: Offset, left: Boolean) {
    rotate(if (left) -35f else 35f, pivot = at) {
        val topLeft = if (left) at + Offset(-58f, -14f) else at + Offset(0f, -14f)
        drawOval(Leaf, topLeft = topLeft, size = Size(58f, 28f))
        drawOval(LeafDark, topLeft = topLeft, size = Size(58f, 28f), style = Stroke(3f))
    }
}

private fun DrawScope.drawCan(c: Offset) {
    drawCircle(Color.White.copy(alpha = 0.55f), 96f, c)
    val body = Path().apply {
        moveTo(c.x - 50f, c.y - 36f)
        lineTo(c.x + 30f, c.y - 36f)
        lineTo(c.x + 38f, c.y + 46f)
        lineTo(c.x - 58f, c.y + 46f)
        close()
    }
    drawLine(Palette.Mist, c + Offset(30f, 10f), c + Offset(86f, -40f), strokeWidth = 14f, cap = StrokeCap.Round)
    drawPath(body, Palette.Mist)
    drawPath(body, Palette.Ink, style = Stroke(5f))
    drawArc(Palette.Ink, 180f, 180f, false, topLeft = c + Offset(-40f, -76f), size = Size(60f, 70f), style = Stroke(6f))
    drawCircle(Palette.MistSoft, 12f, c + Offset(90f, -44f))
    drawCircle(Palette.Ink, 12f, c + Offset(90f, -44f), style = Stroke(4f))
}

/** Drops falling over all beds; [t] 0..1. */
private fun DrawScope.drawDrops(t: Float) {
    BED_X.forEach { x ->
        for (d in 0 until 4) {
            val phase = (t + d * 0.22f) % 1f
            val y = 200f + phase * (SOIL_Y - 200f)
            drawLine(Palette.Mist, Offset(x - 30f + d * 20f, y), Offset(x - 30f + d * 20f, y + 22f), strokeWidth = 7f, cap = StrokeCap.Round, alpha = 1f - t * 0.6f)
        }
    }
}

private fun DrawScope.drawVase(c: Offset, flowers: List<Int>) {
    flowers.forEachIndexed { i, color ->
        val a = (-60f + i * 15f) * PI.toFloat() / 180f
        val tip = c + Offset(120f * sin(a), -120f * cos(a)) - Offset(0f, 30f)
        drawLine(LeafDark, c - Offset(0f, 30f), tip, strokeWidth = 5f, cap = StrokeCap.Round)
        drawCircle(FlowerColors[color % FlowerColors.size], 16f, tip)
        drawCircle(Palette.Ink, 16f, tip, style = Stroke(3f))
    }
    val vase = Path().apply {
        moveTo(c.x - 34f, c.y - 40f)
        lineTo(c.x + 34f, c.y - 40f)
        lineTo(c.x + 46f, c.y + 50f)
        lineTo(c.x - 46f, c.y + 50f)
        close()
    }
    drawPath(vase, Palette.MistSoft)
    drawPath(vase, Palette.Ink, style = Stroke(5f))
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun GardenPreview() {
    KidTheme {
        GardenScreen(
            GardenUiState(
                beds = listOf(Plot(Stage.FLOWER, 0), Plot(Stage.BUD, 3), Plot(Stage.LEAVES, 2), Plot(Stage.SEED, 1)),
                vase = listOf(0, 4, 1),
                sunny = true,
            ),
            onTap = { _, _ -> },
            onHome = {},
        )
    }
}
