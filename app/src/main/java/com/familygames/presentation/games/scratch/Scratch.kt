package com.familygames.presentation.games.scratch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.R
import com.familygames.domain.game.board.FreshPicker
import com.familygames.domain.game.board.ScratchGrid
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.Photo
import com.familygames.domain.model.PhotoSource
import com.familygames.domain.repository.SettingsRepository
import com.familygames.presentation.common.PhotoImage
import com.familygames.presentation.common.Sfx
import com.familygames.presentation.common.VoicePrompts
import com.familygames.presentation.common.kidPressable
import com.familygames.presentation.common.label
import com.familygames.presentation.common.softShadow
import com.familygames.presentation.games.common.EmptyGameNote
import com.familygames.presentation.games.common.FreePlayClock
import com.familygames.presentation.games.common.FreePlayScaffold
import com.familygames.presentation.games.common.GameEffect
import com.familygames.presentation.games.common.GamePhotos
import com.familygames.presentation.games.common.isToddler
import com.familygames.presentation.games.peekaboo.FreePlayEvent
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class ScratchUiState(
    val loading: Boolean = true,
    val empty: Boolean = false,
    val photo: PhotoSource? = null,
    val revealed: Boolean = false,
    val showNext: Boolean = false,
    /** Toddlers see a faint outline of the picture under the mist (docs/12 M12b option). */
    val ghost: Boolean = false,
    val picture: Int = 0,
)

/** M12b · "Zdrapka" (S20): free play. */
class ScratchViewModel(
    private val gamePhotos: GamePhotos,
    private val settings: SettingsRepository,
    private val prompts: VoicePrompts,
    private val freePlay: FreePlayClock,
    random: Random,
) : ViewModel() {
    private val state = MutableStateFlow(ScratchUiState())
    val uiState: StateFlow<ScratchUiState> = state.asStateFlow()
    private val effects = Channel<GameEffect>(Channel.BUFFERED)
    val effect: Flow<GameEffect> = effects.receiveAsFlow()

    private val picker = FreshPicker<Photo>(random)
    private var pool: List<Photo> = emptyList()
    private var current: Photo? = null
    private var after: Job? = null

    init {
        viewModelScope.launch {
            pool = gamePhotos.all()
            prompts.awaitReady()
            state.value = ScratchUiState(loading = false, empty = pool.isEmpty(), ghost = settings.settings.first().isToddler)
            next()
            freePlay.start()
        }
    }

    fun onEvent(event: FreePlayEvent) {
        when (event) {
            FreePlayEvent.Revealed -> revealed()
            FreePlayEvent.Next -> next()
            FreePlayEvent.Home -> {
                after?.cancel()
                viewModelScope.launch {
                    withContext(NonCancellable) { freePlay.stop(GameKind.SCRATCH) }
                    effects.send(GameEffect.Home)
                }
            }
            FreePlayEvent.Tap -> Unit
        }
    }

    private fun next() {
        after?.cancel()
        val photo = picker.next(pool) ?: return
        current = photo
        state.update { it.copy(photo = photo.image, revealed = false, showNext = false, picture = it.picture + 1) }
    }

    private fun revealed() {
        if (state.value.revealed) return
        state.update { it.copy(revealed = true) }
        val photo = current ?: return
        after = viewModelScope.launch {
            val name = prompts.label(photo)
            if (name.isEmpty()) effects.send(GameEffect.Sound(Sfx.MATCH))
            prompts.play(name)
            delay(2_000)
            state.update { it.copy(showNext = true) }
            delay(3_000)
            next()
        }
    }
}

private const val BRUSH_RADIUS_DP = 32f

/** S20 · picture under a layer of mist that fingers wipe away (up to three fingers at once). */
@Composable
fun ScratchScreen(state: ScratchUiState, onEvent: (FreePlayEvent) -> Unit) {
    FreePlayScaffold(onHome = { onEvent(FreePlayEvent.Home) }, singlePointer = false) { scale ->
        if (state.empty) {
            EmptyGameNote()
            return@FreePlayScaffold
        }
        val frame = RoundedCornerShape(32.dp)
        Box(
            Modifier
                .align(Alignment.Center)
                .size(920.dp * scale, 690.dp * scale)
                .softShadow(frame)
                .background(Palette.Surface, frame)
                .border(1.dp, Palette.Line, frame)
                .padding(18.dp * scale)
                .clip(RoundedCornerShape(20.dp)),
        ) {
            androidx.compose.runtime.key(state.picture) {
                PhotoImage(state.photo, null, Modifier.fillMaxSize())
                Mist(state, onRevealed = { onEvent(FreePlayEvent.Revealed) })
            }
        }
        if (state.showNext) {
            val description = stringResource(R.string.cd_next_picture)
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 36.dp)
                    .size(96.dp)
                    .semantics { contentDescription = description }
                    .kidPressable { onEvent(FreePlayEvent.Next) }
                    .background(Palette.AccentSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_next), null, tint = Palette.AccentInk, modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun Mist(state: ScratchUiState, onRevealed: () -> Unit) {
    val points = remember { mutableStateListOf<Offset>() }
    val grid = remember { ScratchGrid() }
    val version = remember { mutableIntStateOf(0) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(state.revealed) { if (state.revealed) fade.animateTo(0f, tween(400)) }
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = fade.value
            }
            .pointerInput(Unit) {
                val radius = BRUSH_RADIUS_DP * density
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.filter { it.pressed }.take(3).forEach { change ->
                            val p = change.position
                            val previous = change.previousPosition
                            // Fill the gap of a fast swipe, but skip points that add nothing new.
                            val steps = ((p - previous).getDistance() / (radius / 2)).toInt().coerceIn(0, 12)
                            val wiped = (1..steps).map { i -> previous + (p - previous) * (i / (steps + 1f)) } + p
                            // Count every wiped point, not only the last one: the reveal needs the whole picture.
                            wiped.forEach { w -> grid.scratch(w.x / size.width, w.y / size.height, radius / size.width, radius / size.height) }
                            points += wiped.dropLast(1)
                            if (points.isEmpty() || (p - points.last()).getDistance() > radius / 3) points += p
                            change.consume()
                        }
                        version.intValue++
                        if (grid.fraction >= ScratchGrid.REVEAL_AT) onRevealed()
                    }
                }
            },
    ) {
        version.intValue // redraw on every scratch
        drawMist(ghost = state.ghost)
        val r = BRUSH_RADIUS_DP.dp.toPx()
        points.forEach { drawCircle(Palette.Bg2, r, it, blendMode = BlendMode.Clear) }
    }
}

/**
 * Scratch-card layer, clearly darker than the page and the white frame (owner, 2026-11-11): warm grey with a dot
 * grid and a few white glints. Toddlers get it slightly see-through, so the picture's outline shows (ADR-023).
 */
private fun DrawScope.drawMist(ghost: Boolean) {
    val alpha = if (ghost) 0.88f else 1f
    drawRect(Palette.Line2, alpha = alpha)
    val step = 30.dp.toPx()
    var row = 0
    var y = step / 2
    while (y < size.height) {
        var x = if (row % 2 == 0) step / 2 else step
        while (x < size.width) {
            drawCircle(Palette.Ink3, 3.dp.toPx(), Offset(x, y), alpha = alpha)
            x += step
        }
        y += step
        row++
    }
    val glint = 9.dp.toPx()
    GLINTS.forEach { (gx, gy) ->
        val c = Offset(gx * size.width, gy * size.height)
        drawLine(Color.White, c - Offset(glint, 0f), c + Offset(glint, 0f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round, alpha = 0.8f * alpha)
        drawLine(Color.White, c - Offset(0f, glint), c + Offset(0f, glint), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round, alpha = 0.8f * alpha)
    }
}

/** Fixed glint positions (fractions of the picture), so the layer looks the same every time. */
private val GLINTS = listOf(0.12f to 0.18f, 0.78f to 0.12f, 0.45f to 0.42f, 0.2f to 0.76f, 0.86f to 0.7f, 0.6f to 0.88f)

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun ScratchPreview() {
    KidTheme { ScratchScreen(ScratchUiState(loading = false, photo = PhotoSource.Asset("builtin/zwierzeta_kot.webp")), onEvent = {}) }
}
