package com.familygames.presentation.games.bubbles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.Photo
import com.familygames.presentation.common.LocalReducedMotion
import com.familygames.presentation.common.Sfx
import com.familygames.presentation.common.ThumbLoader
import com.familygames.presentation.common.VoicePrompts
import com.familygames.presentation.common.label
import com.familygames.presentation.games.common.EmptyGameNote
import com.familygames.presentation.games.common.FreePlayClock
import com.familygames.presentation.games.common.FreePlayScaffold
import com.familygames.presentation.games.common.GameEffect
import com.familygames.presentation.games.common.GamePhotos
import com.familygames.presentation.theme.KidTheme
import com.familygames.presentation.theme.Palette
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

data class BubblesUiState(val loading: Boolean = true, val empty: Boolean = false, val images: List<ImageBitmap> = emptyList())

/** M15a · "Bańki" (S24): free play. */
class BubblesViewModel(
    private val gamePhotos: GamePhotos,
    private val thumbs: ThumbLoader,
    private val prompts: VoicePrompts,
    private val freePlay: FreePlayClock,
    private val random: Random,
) : ViewModel() {
    private val state = MutableStateFlow(BubblesUiState())
    val uiState: StateFlow<BubblesUiState> = state.asStateFlow()
    private val effects = Channel<GameEffect>(Channel.BUFFERED)
    val effect: Flow<GameEffect> = effects.receiveAsFlow()

    private var photos: List<Photo> = emptyList()
    private var pops = 0
    private var talking: Job? = null

    init {
        viewModelScope.launch {
            val chosen = gamePhotos.all().shuffled(random).take(MAX_PHOTOS)
            val loaded = chosen.mapNotNull { p -> thumbs.load(p.thumb, THUMB_PX)?.let { p to it } }
            photos = loaded.map { it.first }
            prompts.awaitReady()
            state.value = BubblesUiState(loading = false, empty = loaded.isEmpty(), images = loaded.map { it.second })
            freePlay.start()
        }
    }

    /** Every third pop says the photo's name, so it never turns into a noise (docs/12 M15a). */
    fun popped(imageIndex: Int) {
        effects.trySend(GameEffect.Sound(Sfx.POP, 0.9f + random.nextFloat() * 0.3f))
        pops++
        if (pops % 3 == 0 && talking?.isActive != true) {
            val photo = photos.getOrNull(imageIndex) ?: return
            talking = viewModelScope.launch { prompts.play(prompts.label(photo)) }
        }
    }

    fun home() {
        talking?.cancel()
        viewModelScope.launch {
            withContext(NonCancellable) { freePlay.stop(GameKind.BUBBLES) }
            effects.send(GameEffect.Home)
        }
    }

    private companion object {
        const val MAX_PHOTOS = 12
        const val THUMB_PX = 320
    }
}

private class Bubble(var x: Float, var y: Float, val baseX: Float, val radius: Float, val speed: Float, val phase: Float, val image: Int) {
    var age = 0f
}

private class Drop(val x: Float, val y: Float, val dx: Float, val dy: Float) {
    var age = 0f
}

/** S24 · bubbles with family photos rise slowly; one Canvas, one frame loop (docs/12 M15a). */
@Composable
fun BubblesScreen(state: BubblesUiState, onPop: (Int) -> Unit, onHome: () -> Unit) {
    FreePlayScaffold(onHome = onHome) { _ ->
        if (state.empty) {
            EmptyGameNote()
            return@FreePlayScaffold
        }
        if (state.images.isEmpty()) return@FreePlayScaffold
        val reduced = LocalReducedMotion.current
        val density = LocalDensity.current.density
        val bubbles = remember { mutableListOf<Bubble>() }
        val drops = remember { mutableListOf<Drop>() }
        val frame = remember { mutableIntStateOf(0) }
        val random = remember { Random(System.nanoTime()) }
        val area = remember { FloatArray(2) }
        LaunchedEffect(state.images, reduced) {
            var last = 0L
            var nextSpawn = 0f
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                    last = now
                    val (w, h) = area[0] to area[1]
                    if (w <= 0f) return@withFrameNanos
                    nextSpawn -= dt
                    if (nextSpawn <= 0f && bubbles.size < MAX_BUBBLES) {
                        val r = (70 + random.nextInt(41)) * density
                        val x = r + random.nextFloat() * (w - 2 * r)
                        val y = if (reduced) r + random.nextFloat() * (h - 2 * r) else h + r
                        bubbles += Bubble(x, y, x, r, (40 + random.nextInt(31)) * density, random.nextFloat() * 6.28f, random.nextInt(state.images.size))
                        nextSpawn = 1.2f + random.nextFloat() * 0.8f
                    }
                    bubbles.forEach { b ->
                        b.age += dt
                        if (!reduced) {
                            b.y -= b.speed * dt
                            b.x = b.baseX + sin(b.age * 1.3f + b.phase) * 20 * density
                        }
                    }
                    bubbles.removeAll { it.y < -it.radius || (reduced && it.age > STILL_LIFE_S) }
                    drops.forEach { it.age += dt }
                    drops.removeAll { it.age > DROP_LIFE_S }
                    frame.intValue++
                }
            }
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state.images) {
                    detectTapGestures { p ->
                        val hit = bubbles.lastOrNull { (Offset(it.x, it.y) - p).getDistance() <= it.radius * 1.1f } ?: return@detectTapGestures
                        bubbles.remove(hit)
                        repeat(8) { i ->
                            val a = i * PI.toFloat() / 4
                            drops += Drop(hit.x + kotlin.math.cos(a) * hit.radius * 0.8f, hit.y + sin(a) * hit.radius * 0.8f, kotlin.math.cos(a), sin(a))
                        }
                        onPop(hit.image)
                    }
                },
        ) {
            frame.intValue
            area[0] = size.width
            area[1] = size.height
            bubbles.forEach { b ->
                val alpha = if (reduced) fadeFor(b.age) else 1f
                val center = Offset(b.x, b.y)
                val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, b.radius)) }
                val image = state.images[b.image]
                val side = min(image.width, image.height)
                clipPath(clip) {
                    drawImage(
                        image,
                        srcOffset = IntOffset((image.width - side) / 2, (image.height - side) / 2),
                        srcSize = IntSize(side, side),
                        dstOffset = IntOffset((b.x - b.radius).roundToInt(), (b.y - b.radius).roundToInt()),
                        dstSize = IntSize((b.radius * 2).roundToInt(), (b.radius * 2).roundToInt()),
                        alpha = 0.9f * alpha,
                    )
                }
                drawCircle(Palette.Line, b.radius, center, alpha = alpha, style = Stroke(2 * density))
                drawArc(
                    color = androidx.compose.ui.graphics.Color.White,
                    startAngle = 200f, sweepAngle = 60f, useCenter = false,
                    topLeft = center - Offset(b.radius * 0.72f, b.radius * 0.72f),
                    size = Size(b.radius * 1.44f, b.radius * 1.44f),
                    alpha = 0.5f * alpha,
                    style = Stroke(6 * density, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
            drops.forEach { d ->
                val t = d.age / DROP_LIFE_S
                drawCircle(Palette.Line2, 7 * density * (1 - t), Offset(d.x + d.dx * 60 * density * t, d.y + d.dy * 60 * density * t), alpha = 1 - t)
            }
        }
    }
}

private fun fadeFor(age: Float): Float = when {
    age < 0.5f -> age / 0.5f
    age > STILL_LIFE_S - 0.5f -> ((STILL_LIFE_S - age) / 0.5f).coerceAtLeast(0f)
    else -> 1f
}

private const val MAX_BUBBLES = 6
private const val DROP_LIFE_S = 0.25f
private const val STILL_LIFE_S = 6f

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun BubblesPreview() {
    KidTheme { BubblesScreen(BubblesUiState(loading = false, empty = true), onPop = {}, onHome = {}) }
}
