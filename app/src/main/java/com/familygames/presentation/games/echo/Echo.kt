package com.familygames.presentation.games.echo

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.R
import com.familygames.domain.game.echo.EchoGame
import com.familygames.domain.game.echo.EchoLevel
import com.familygames.domain.game.echo.EchoOutcome
import com.familygames.domain.game.round.RoundScoring
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.GameResult
import com.familygames.domain.model.PhotoSource
import com.familygames.domain.repository.GameResultRepository
import com.familygames.presentation.common.NotePlayer
import com.familygames.presentation.common.Sfx
import com.familygames.presentation.common.VoicePrompts
import com.familygames.presentation.common.kidPressable
import com.familygames.presentation.common.phrase
import com.familygames.presentation.games.common.GameEffect
import com.familygames.presentation.games.common.GamePhotos
import com.familygames.presentation.games.common.TaskGameScaffold
import com.familygames.presentation.games.xylophone.XylophoneBar
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

data class EchoUiState(
    val loading: Boolean = true,
    /** Xylophone bar index of every bar on screen. */
    val bars: List<Int> = emptyList(),
    val photos: List<PhotoSource?> = emptyList(),
    /** Position (in [bars]) lit by the playback. */
    val lit: Int? = null,
    val strikes: Map<Int, Int> = emptyMap(),
    /** The child's turn: the ear sign turns into a hand. */
    val listening: Boolean = false,
    val tuneLength: Int = 0,
    val target: Int = 0,
    val winStars: Int? = null,
    val game: Int = 0,
)

sealed interface EchoEvent {
    data class Tap(val position: Int) : EchoEvent
    data object Replay : EchoEvent
    data object Restart : EchoEvent
    data object Home : EchoEvent
}

/** M27 · "Powtórz za mną" (S36): the xylophone plays a tune that grows; the child plays it back. */
class EchoViewModel(
    levelKey: String,
    private val gamePhotos: GamePhotos,
    private val notes: NotePlayer,
    private val prompts: VoicePrompts,
    private val resources: Resources,
    private val results: GameResultRepository,
    private val clock: Clock,
    private val random: Random,
) : ViewModel() {
    private val level = EchoLevel.fromKey(levelKey)
    private val state = MutableStateFlow(EchoUiState())
    val uiState: StateFlow<EchoUiState> = state.asStateFlow()
    private val effects = Channel<GameEffect>(Channel.BUFFERED)
    val effect: Flow<GameEffect> = effects.receiveAsFlow()

    private var game = EchoGame(level, random)
    private var startedAt = 0L
    private var playback: Job? = null

    init {
        viewModelScope.launch {
            val photos = gamePhotos.preferFamily(resources.getString(R.string.family_category_name))
            state.update {
                it.copy(
                    loading = false, bars = level.bars, target = level.target,
                    photos = level.bars.map { bar -> photos.takeIf { p -> p.isNotEmpty() }?.let { p -> p[bar % p.size].thumb } },
                )
            }
            prompts.awaitReady()
            newGame()
        }
    }

    fun onEvent(event: EchoEvent) {
        when (event) {
            is EchoEvent.Tap -> tap(event.position)
            EchoEvent.Replay -> if (state.value.listening) play(game.tune)
            EchoEvent.Restart -> newGame()
            EchoEvent.Home -> {
                playback?.cancel()
                effects.trySend(GameEffect.Home)
            }
        }
    }

    private fun newGame() {
        game = EchoGame(level, random)
        startedAt = clock.now()
        state.update { it.copy(winStars = null, tuneLength = 0, listening = false, game = it.game + 1) }
        play(game.start(), intro = true)
    }

    /** Plays the tune on the bars (lit one by one), then hands over to the child. */
    private fun play(tune: List<Int>, intro: Boolean = false) {
        playback?.cancel()
        playback = viewModelScope.launch {
            state.update { it.copy(listening = false, lit = null, tuneLength = tune.size) }
            if (intro) prompts.play(prompts.phrase(resources.getString(R.string.echo_listen)))
            delay(PAUSE_MS)
            tune.forEach { pos ->
                state.update { it.copy(lit = pos) }
                strike(pos)
                delay(NOTE_MS)
                state.update { it.copy(lit = null) }
                delay(GAP_MS)
            }
            game.heard()
            state.update { it.copy(listening = true) }
            prompts.play(prompts.phrase(resources.getString(R.string.echo_your_turn)))
        }
    }

    private fun strike(pos: Int) {
        notes.play(level.bars[pos])
        state.update { it.copy(strikes = it.strikes + (pos to (it.strikes[pos] ?: 0) + 1)) }
    }

    private fun tap(pos: Int) {
        if (!state.value.listening) return
        when (game.tap(pos)) {
            EchoOutcome.Ignored -> Unit
            is EchoOutcome.Good -> {
                strike(pos)
                if (!game.listening) {
                    // The whole tune was right: a little cheer, then one note more.
                    effects.trySend(GameEffect.Sound(Sfx.STAR, 1f + 0.1f * game.tune.size))
                    state.update { it.copy(listening = false) }
                    playback = viewModelScope.launch {
                        delay(CHEER_MS)
                        play(game.extend())
                    }
                }
            }
            EchoOutcome.Wrong -> {
                // A soft sound, then the same tune again — never shorter, never a "no".
                effects.trySend(GameEffect.Sound(Sfx.SOFT))
                state.update { it.copy(listening = false) }
                playback = viewModelScope.launch {
                    delay(CHEER_MS)
                    play(game.tune)
                }
            }
            EchoOutcome.Won -> {
                strike(pos)
                state.update { it.copy(listening = false, tuneLength = game.tune.size) }
                viewModelScope.launch { finish() }
            }
        }
    }

    private suspend fun finish() {
        delay(CHEER_MS)
        val stars = RoundScoring.stars(level.target, game.mistakes)
        results.save(
            GameResult(
                game = GameKind.ECHO, levelKey = level.key, stars = stars, moves = level.target, mistakes = game.mistakes,
                hintsUsed = 0, durationMs = clock.now() - startedAt, finishedAt = clock.now(),
            ),
        )
        state.update { it.copy(winStars = stars) }
        effects.send(GameEffect.Sound(Sfx.WIN))
    }

    private companion object {
        const val PAUSE_MS = 500L
        const val NOTE_MS = 520L
        const val GAP_MS = 180L
        const val CHEER_MS = 900L
    }
}

/** S36 · a few xylophone bars; the ear (listen) or the hand (your turn) on top, dots for the tune length. */
@Composable
fun EchoScreen(state: EchoUiState, onEvent: (EchoEvent) -> Unit, onStarShown: (Int) -> Unit = {}) {
    TaskGameScaffold(
        onHome = { onEvent(EchoEvent.Home) },
        onRestart = { onEvent(EchoEvent.Restart) },
        progress = if (state.target > 0) (state.tuneLength - 1).coerceAtLeast(0) + (if (state.winStars != null) 1 else 0) to state.target else null,
        winStars = state.winStars,
        onStarShown = onStarShown,
    ) { scale ->
        if (state.bars.isEmpty()) return@TaskGameScaffold
        val description = stringResource(if (state.listening) R.string.cd_echo_your_turn else R.string.cd_echo_listen)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 104.dp * scale)
                .size(84.dp * scale)
                .semantics { contentDescription = description }
                .kidPressable(enabled = state.listening) { onEvent(EchoEvent.Replay) }
                .background(if (state.listening) Palette.Accent else Palette.AccentSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (state.listening) R.drawable.ic_note else R.drawable.ic_volume_on), null,
                tint = if (state.listening) Palette.Surface else Palette.AccentInk, modifier = Modifier.size(40.dp * scale),
            )
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp * scale),
            horizontalArrangement = Arrangement.spacedBy(30.dp * scale),
            verticalAlignment = Alignment.Bottom,
        ) {
            state.bars.forEachIndexed { pos, bar ->
                XylophoneBar(
                    index = bar,
                    photo = state.photos.getOrNull(pos),
                    lit = state.lit == pos,
                    strikes = state.strikes[pos] ?: 0,
                    scale = scale * 0.92f,
                    modifier = Modifier.kidPressable(enabled = state.listening) { onEvent(EchoEvent.Tap(pos)) },
                )
            }
        }
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun EchoPreview() {
    KidTheme { EchoScreen(EchoUiState(loading = false, bars = listOf(0, 2, 4, 5), photos = List(4) { null }, lit = 2, tuneLength = 2, target = 4), onEvent = {}) }
}
