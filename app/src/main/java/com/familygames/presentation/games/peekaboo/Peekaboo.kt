package com.familygames.presentation.games.peekaboo

import android.content.res.Resources
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.R
import com.familygames.domain.game.board.FreshPicker
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.Photo
import com.familygames.domain.model.PhotoSource
import com.familygames.presentation.common.PhotoImage
import com.familygames.presentation.common.Sfx
import com.familygames.presentation.common.VoicePrompts
import com.familygames.presentation.common.phrase
import com.familygames.presentation.common.kidPressable
import com.familygames.presentation.common.label
import com.familygames.presentation.common.softShadow
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

data class PeekabooUiState(val loading: Boolean = true, val empty: Boolean = false, val photo: PhotoSource? = null, val open: Boolean = false)

sealed interface FreePlayEvent {
    data object Tap : FreePlayEvent
    data object Next : FreePlayEvent
    data object Revealed : FreePlayEvent
    data object Home : FreePlayEvent
}

/** M12a · "A kuku" (S19): free play, no score; only the time spent is stored. */
class PeekabooViewModel(
    private val gamePhotos: GamePhotos,
    private val prompts: VoicePrompts,
    private val resources: Resources,
    private val freePlay: FreePlayClock,
    random: Random,
) : ViewModel() {
    private val state = MutableStateFlow(PeekabooUiState())
    val uiState: StateFlow<PeekabooUiState> = state.asStateFlow()
    private val effects = Channel<GameEffect>(Channel.BUFFERED)
    val effect: Flow<GameEffect> = effects.receiveAsFlow()

    private val picker = FreshPicker<Photo>(random)
    private var pool: List<Photo> = emptyList()
    private var closing: Job? = null

    init {
        viewModelScope.launch {
            pool = gamePhotos.all()
            prompts.awaitReady()
            state.value = PeekabooUiState(loading = false, empty = pool.isEmpty())
            freePlay.start()
        }
    }

    fun onEvent(event: FreePlayEvent) {
        when (event) {
            FreePlayEvent.Tap -> if (state.value.open) close() else open()
            FreePlayEvent.Home -> leave()
            else -> Unit
        }
    }

    private fun open() {
        val photo = picker.next(pool) ?: return
        state.update { it.copy(photo = photo.image, open = true) }
        closing?.cancel()
        closing = viewModelScope.launch {
            delay(OPEN_ANIM_MS)
            val name = prompts.label(photo)
            if (name.isEmpty()) effects.send(GameEffect.Sound(Sfx.MATCH))
            prompts.play(prompts.phrase(resources.getString(R.string.prefix_peekaboo)) + name)
            delay(AUTO_CLOSE_MS)
            close()
        }
    }

    private fun close() {
        closing?.cancel()
        state.update { it.copy(open = false) }
    }

    private fun leave() {
        closing?.cancel()
        viewModelScope.launch {
            withContext(NonCancellable) { freePlay.stop(GameKind.PEEKABOO) }
            effects.send(GameEffect.Home)
        }
    }

    private companion object {
        const val OPEN_ANIM_MS = 500L
        const val AUTO_CLOSE_MS = 3_000L
    }
}

/** S19 · one big window; the door swings open on tap and closes by itself after 3 s. */
@Composable
fun PeekabooScreen(state: PeekabooUiState, onEvent: (FreePlayEvent) -> Unit) {
    FreePlayScaffold(onHome = { onEvent(FreePlayEvent.Home) }) { scale ->
        if (state.empty) {
            EmptyGameNote()
            return@FreePlayScaffold
        }
        val swing by animateFloatAsState(if (state.open) 1f else 0f, tween(500), label = "door")
        val frame = RoundedCornerShape(36.dp)
        val description = stringResource(R.string.cd_peek_window)
        Box(
            Modifier
                .align(Alignment.Center)
                .size(620.dp * scale, 500.dp * scale)
                .semantics { contentDescription = description }
                .kidPressable { onEvent(FreePlayEvent.Tap) }
                .softShadow(frame)
                .background(Palette.Surface, frame)
                .border(1.dp, Palette.Line, frame)
                .padding(22.dp * scale),
        ) {
            val inner = RoundedCornerShape(22.dp)
            Box(Modifier.fillMaxSize().clip(inner).background(Palette.Bg2)) {
                if (swing > 0f) PhotoImage(state.photo, null, Modifier.fillMaxSize())
            }
            // The door: neutral panel with a handle, hinged on the left edge.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        rotationY = -75f * swing
                        cameraDistance = 60f * density
                        alpha = 1f - 0.8f * swing
                    }
                    .background(Palette.Bg2, inner)
                    .border(2.dp, Palette.Line2, inner),
            ) {
                Box(Modifier.align(Alignment.Center).padding(36.dp * scale).fillMaxSize().border(2.dp, Palette.Line2, RoundedCornerShape(16.dp)))
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 56.dp * scale)
                        .size(34.dp * scale)
                        .background(Palette.Ink3, CircleShape),
                )
            }
        }
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun PeekabooPreview() {
    KidTheme { PeekabooScreen(PeekabooUiState(loading = false), onEvent = {}) }
}
