package com.familygames.presentation.games.xylophone

import android.content.res.Resources
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.R
import com.familygames.domain.game.board.Xylophone
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.PhotoSource
import com.familygames.presentation.common.NotePlayer
import com.familygames.presentation.common.PhotoImage
import com.familygames.presentation.common.kidPressable
import com.familygames.presentation.common.softShadow
import com.familygames.presentation.games.common.FreePlayClock
import com.familygames.presentation.games.common.FreePlayScaffold
import com.familygames.presentation.games.common.GameEffect
import com.familygames.presentation.games.common.GamePhotos
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

data class XylophoneUiState(
    val photos: List<PhotoSource?> = List(Xylophone.BARS) { null },
    /** Bar lit by the "Zagraj melodię" playback. */
    val lit: Int? = null,
    val playingMelody: Boolean = false,
    /** Increments with every strike so each bar can bounce again. */
    val strikes: Map<Int, Int> = emptyMap(),
)

/** M15b · "Cymbałki" (S25): free play, multi-touch. */
class XylophoneViewModel(
    private val gamePhotos: GamePhotos,
    private val notes: NotePlayer,
    private val resources: Resources,
    private val freePlay: FreePlayClock,
) : ViewModel() {
    private val state = MutableStateFlow(XylophoneUiState())
    val uiState: StateFlow<XylophoneUiState> = state.asStateFlow()
    private val effects = Channel<GameEffect>(Channel.BUFFERED)
    val effect: Flow<GameEffect> = effects.receiveAsFlow()
    private var melody: Job? = null

    init {
        viewModelScope.launch {
            // Family faces first (docs/12 M15b); fewer than 8 photos repeat.
            val photos = gamePhotos.preferFamily(resources.getString(R.string.family_category_name))
            if (photos.isNotEmpty()) state.update { s -> s.copy(photos = List(Xylophone.BARS) { photos[it % photos.size].thumb }) }
            freePlay.start()
        }
    }

    fun strike(bar: Int) {
        notes.play(bar)
        state.update { it.copy(strikes = it.strikes + (bar to (it.strikes[bar] ?: 0) + 1)) }
    }

    fun toggleMelody() {
        if (melody?.isActive == true) {
            melody?.cancel()
            state.update { it.copy(lit = null, playingMelody = false) }
            return
        }
        melody = viewModelScope.launch {
            state.update { it.copy(playingMelody = true) }
            Xylophone.MELODY.forEachIndexed { i, bar ->
                state.update { it.copy(lit = bar) }
                strike(bar)
                delay(Xylophone.beatMs(i))
            }
            state.update { it.copy(lit = null, playingMelody = false) }
        }
    }

    fun home() {
        melody?.cancel()
        viewModelScope.launch {
            withContext(NonCancellable) { freePlay.stop(GameKind.XYLOPHONE) }
            effects.send(GameEffect.Home)
        }
    }
}

/** S25 · eight bars with family photos; sliding a finger across plays every bar it crosses. */
@Composable
fun XylophoneScreen(state: XylophoneUiState, onStrike: (Int) -> Unit, onMelody: () -> Unit, onHome: () -> Unit) {
    FreePlayScaffold(onHome = onHome, singlePointer = false) { scale ->
        val bounds = remember { mutableStateMapOf<Int, Rect>() }
        Row(
            Modifier
                .align(Alignment.Center)
                .padding(top = 40.dp * scale)
                .pointerInput(Unit) {
                    val lastBar = mutableMapOf<PointerId, Int?>()
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                if (!change.pressed) {
                                    lastBar.remove(change.id)
                                    return@forEach
                                }
                                val bar = bounds.entries.firstOrNull { it.value.contains(change.position) }?.key
                                if (bar != null && bar != lastBar[change.id]) onStrike(bar)
                                lastBar[change.id] = bar
                                change.consume()
                            }
                        }
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(18.dp * scale),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(Xylophone.BARS) { i ->
                XylophoneBar(
                    index = i,
                    photo = state.photos.getOrNull(i),
                    lit = state.lit == i,
                    strikes = state.strikes[i] ?: 0,
                    scale = scale,
                    modifier = Modifier.onGloballyPositioned { bounds[i] = it.boundsInParent() },
                )
            }
        }
        val description = stringResource(R.string.cd_play_melody)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 32.dp, top = 28.dp)
                .size(96.dp)
                .semantics { contentDescription = description }
                .kidPressable(onClick = onMelody)
                .background(if (state.playingMelody) Palette.Accent else Palette.AccentSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_note), null, tint = if (state.playingMelody) Palette.Surface else Palette.AccentInk, modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
internal fun XylophoneBar(index: Int, photo: PhotoSource?, lit: Boolean, strikes: Int, scale: Float, modifier: Modifier) {
    val drop = remember { Animatable(0f) }
    LaunchedEffect(strikes) {
        if (strikes > 0) {
            drop.snapTo(1f)
            drop.animateTo(0f, tween(120))
        }
    }
    val shape = RoundedCornerShape(26.dp)
    // Neutral tones from Bg2 to Line2, graded by height rather than colour (docs/12 M15b).
    val color = lerp(Palette.Bg2, Palette.Line2, index / (Xylophone.BARS - 1f))
    val height = (580 - index * 26).dp
    val description = stringResource(R.string.cd_bar, index + 1)
    Box(
        modifier
            .width(118.dp * scale)
            .height(height * scale)
            .graphicsLayer { translationY = drop.value * 4.dp.toPx() }
            .semantics { contentDescription = description }
            .softShadow(shape)
            .background(color, shape)
            .border(if (lit) 4.dp else 1.dp, if (lit) Palette.Accent else Palette.Line2, shape),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 18.dp * scale), contentAlignment = Alignment.Center) {
            PhotoImage(
                photo,
                null,
                Modifier
                    .size(96.dp * scale)
                    .graphicsLayer {
                        val s = 1f + 0.08f * drop.value
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .border(3.dp, Palette.Surface, CircleShape),
            )
        }
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun XylophonePreview() {
    KidTheme { XylophoneScreen(XylophoneUiState(lit = 3), onStrike = {}, onMelody = {}, onHome = {}) }
}
