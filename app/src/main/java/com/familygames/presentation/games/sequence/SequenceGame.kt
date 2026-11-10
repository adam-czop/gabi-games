package com.familygames.presentation.games.sequence

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.R
import com.familygames.domain.game.board.DropOutcome
import com.familygames.domain.game.board.SequenceBoard
import com.familygames.domain.game.round.RoundScoring
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.GameResult
import com.familygames.domain.model.PhotoSequence
import com.familygames.domain.model.PhotoSource
import com.familygames.domain.repository.GameResultRepository
import com.familygames.domain.repository.SequenceRepository
import com.familygames.domain.repository.SettingsRepository
import com.familygames.presentation.common.KidFrame
import com.familygames.presentation.common.RoundIconButton
import com.familygames.presentation.common.Sfx
import com.familygames.presentation.common.VoicePrompts
import com.familygames.presentation.common.kidPressable
import com.familygames.presentation.common.label
import com.familygames.presentation.common.softShadow
import com.familygames.presentation.games.common.DraggableItem
import com.familygames.presentation.games.common.EmptyGameNote
import com.familygames.presentation.games.common.GameEffect
import com.familygames.presentation.games.common.OptionUi
import com.familygames.presentation.games.common.PhotoCard
import com.familygames.presentation.games.common.TaskGameScaffold
import com.familygames.presentation.games.common.dropZone
import com.familygames.presentation.games.common.isToddler
import com.familygames.presentation.games.common.pulse
import com.familygames.presentation.games.common.rememberDropZones
import com.familygames.presentation.theme.Dimens
import com.familygames.presentation.theme.KidTheme
import com.familygames.presentation.theme.KidType
import com.familygames.presentation.theme.Palette
import com.familygames.util.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Sequences the child can choose from (only those with 3–5 steps left). */
class SequencePickViewModel(sequences: SequenceRepository) : ViewModel() {
    val sequences: StateFlow<List<PhotoSequence>?> = sequences.observeAll()
        .map { list -> list.filter { it.steps.size >= PhotoSequence.MIN_STEPS } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Picker shown when the parent made more than one sequence: each tile is the row of its pictures. */
@Composable
fun SequencePickScreen(sequences: List<PhotoSequence>, onPick: (Long) -> Unit, onBack: () -> Unit) {
    KidFrame { scale ->
        RoundIconButton(
            icon = R.drawable.ic_back,
            contentDescription = stringResource(R.string.cd_back),
            onClick = onBack,
            modifier = Modifier.padding(start = Dimens.ScreenPaddingH, top = Dimens.ScreenPaddingTop),
        )
        Text(
            stringResource(R.string.sequence_pick_title),
            style = KidType.Title.copy(fontSize = KidType.Title.fontSize * scale),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 50.dp * scale),
        )
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 160.dp * scale),
            verticalArrangement = Arrangement.spacedBy(28.dp * scale),
        ) {
            sequences.take(4).forEach { s ->
                val shape = RoundedCornerShape(28.dp)
                Row(
                    Modifier
                        .kidPressable { onPick(s.id) }
                        .semantics(mergeDescendants = true) { contentDescription = s.name }
                        .softShadow(shape)
                        .background(Palette.Surface, shape)
                        .border(1.dp, Palette.Line, shape)
                        .padding(16.dp * scale),
                    horizontalArrangement = Arrangement.spacedBy(14.dp * scale),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    s.steps.forEach { PhotoCard(it.thumb, Modifier.size(110.dp * scale), radius = 16.dp, padding = 4.dp) }
                    Text(s.name, style = KidType.Label.copy(fontSize = KidType.Label.fontSize * scale), modifier = Modifier.padding(start = 12.dp * scale, end = 20.dp * scale))
                }
            }
        }
    }
}

data class SequenceUiState(
    val loading: Boolean = true,
    val empty: Boolean = false,
    val slots: List<OptionUi?> = emptyList(),
    val tray: List<OptionUi> = emptyList(),
    /** Slots that accept a drop now (easy mode: only the first free one). */
    val active: Set<Int> = emptySet(),
    /** Step highlighted while the finished sequence is played back. */
    val playing: Int? = null,
    val winStars: Int? = null,
    val game: Int = 0,
)

sealed interface SequenceEvent {
    data object Restart : SequenceEvent
    data object Home : SequenceEvent
}

/** M16 · "Po kolei" (S26). */
class SequenceViewModel(
    private val sequenceId: Long,
    private val sequences: SequenceRepository,
    private val settings: SettingsRepository,
    private val prompts: VoicePrompts,
    private val results: GameResultRepository,
    private val clock: Clock,
    private val random: Random,
) : ViewModel() {
    private val state = MutableStateFlow(SequenceUiState())
    val uiState: StateFlow<SequenceUiState> = state.asStateFlow()
    private val effects = Channel<GameEffect>(Channel.BUFFERED)
    val effect: Flow<GameEffect> = effects.receiveAsFlow()

    private var board: SequenceBoard? = null
    private var order: List<Long> = emptyList()
    private var startedAt = 0L

    init {
        viewModelScope.launch { deal() }
    }

    private suspend fun deal() {
        val sequence = sequences.get(sequenceId)
        if (sequence == null || sequence.steps.size < PhotoSequence.MIN_STEPS) {
            state.value = SequenceUiState(loading = false, empty = true)
            return
        }
        prompts.awaitReady()
        val easy = settings.settings.first().isToddler
        board = SequenceBoard(sequence.steps, easy)
        // Shuffled, but never already in the right order.
        var shuffled = sequence.steps.shuffled(random)
        if (shuffled == sequence.steps) shuffled = sequence.steps.reversed()
        order = shuffled.map { it.id }
        startedAt = clock.now()
        state.update { SequenceUiState(loading = false, game = it.game + 1) }
        publish()
    }

    private fun publish() {
        val b = board ?: return
        val placed = b.slots.mapNotNull { it?.id }.toSet()
        val byId = b.steps.associateBy { it.id }
        state.update { s ->
            s.copy(
                slots = b.slots.map { p -> p?.let { OptionUi(it.id, it.thumb, it.label) } },
                tray = order.filter { it !in placed }.mapNotNull { byId[it] }.map { OptionUi(it.id, it.thumb, it.label) },
                active = b.steps.indices.filter { b.isActive(it) }.toSet(),
            )
        }
    }

    fun drop(photoId: Long, slot: Int?): Boolean {
        val b = board ?: return false
        if (slot == null || state.value.winStars != null) return false
        return when (val outcome = b.drop(photoId, slot)) {
            DropOutcome.Ignored -> false
            DropOutcome.Wrong -> {
                effects.trySend(GameEffect.Sound(Sfx.SOFT))
                false
            }
            is DropOutcome.Correct -> {
                effects.trySend(GameEffect.Sound(Sfx.SNAP))
                publish()
                if (outcome.won) viewModelScope.launch { finish(b) }
                true
            }
        }
    }

    /** The finished sequence is played back step by step before the stars (docs/12 M16). */
    private suspend fun finish(b: SequenceBoard) {
        delay(400)
        b.steps.forEachIndexed { i, photo ->
            state.update { it.copy(playing = i) }
            val start = clock.now()
            prompts.play(prompts.label(photo))
            val left = 800 - (clock.now() - start)
            if (left > 0) delay(left)
        }
        state.update { it.copy(playing = null) }
        val stars = RoundScoring.stars(b.steps.size, b.mistakes)
        results.save(
            GameResult(
                game = GameKind.SEQUENCE, levelKey = "sequence_${b.steps.size}", stars = stars, moves = b.steps.size,
                mistakes = b.mistakes, hintsUsed = 0, durationMs = clock.now() - startedAt, finishedAt = clock.now(),
            ),
        )
        state.update { it.copy(winStars = stars) }
        effects.send(GameEffect.Sound(Sfx.WIN))
    }

    fun onEvent(event: SequenceEvent) {
        when (event) {
            SequenceEvent.Restart -> viewModelScope.launch { deal() }
            SequenceEvent.Home -> effects.trySend(GameEffect.Home)
        }
    }
}

private val SlotSize = 190.dp
private val TraySize = 170.dp

/** S26 · numbered slots on a time line, shuffled pictures below. */
@Composable
fun SequenceScreen(state: SequenceUiState, onEvent: (SequenceEvent) -> Unit, onDrop: (Long, Int?) -> Boolean, onStarShown: (Int) -> Unit = {}) {
    val zones = rememberDropZones()
    TaskGameScaffold(
        onHome = { onEvent(SequenceEvent.Home) },
        onRestart = { onEvent(SequenceEvent.Restart) },
        progress = null,
        winStars = state.winStars,
        onStarShown = onStarShown,
    ) { scale ->
        if (state.empty) {
            EmptyGameNote()
            return@TaskGameScaffold
        }
        val gap = 36.dp
        Box(Modifier.align(Alignment.TopCenter).padding(top = 150.dp * scale)) {
            val count = state.slots.size
            Canvas(Modifier.width((SlotSize * count + gap * (count - 1)) * scale).height(SlotSize * scale)) {
                val y = size.height / 2
                drawLine(Palette.Line2, Offset(SlotSize.toPx() * scale / 2, y), Offset(size.width - SlotSize.toPx() * scale / 2, y), strokeWidth = 3.dp.toPx())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap * scale)) {
                state.slots.forEachIndexed { i, photo -> Slot(i, photo, i in state.active, state.playing == i, scale, Modifier.dropZone(zones, i)) }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 70.dp * scale)) {
            androidx.compose.runtime.key(state.game) {
                Row(horizontalArrangement = Arrangement.spacedBy(30.dp * scale)) {
                    state.tray.forEach { item ->
                        androidx.compose.runtime.key(item.key) {
                            DraggableItem(zones = zones, modifier = Modifier.size(TraySize * scale), onDrop = { onDrop(item.key, it) }) {
                                PhotoCard(item.image, Modifier.matchParentSize(), radius = 22.dp, padding = 8.dp * scale)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Slot(index: Int, photo: OptionUi?, active: Boolean, playing: Boolean, scale: Float, modifier: Modifier) {
    val shape = RoundedCornerShape(24.dp)
    val grow by animateFloatAsState(if (playing) 1.1f else 1f, label = "grow")
    val description = stringResource(R.string.cd_slot, index + 1)
    Box(
        modifier
            .size(SlotSize * scale)
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
            }
            .pulse(active && photo == null, amount = 0.03f)
            .semantics { contentDescription = description }
            .background(if (photo == null) Palette.Bg2 else Palette.Surface, shape)
            .border(if (active) 4.dp else 1.dp, if (active || playing) Palette.Accent else Palette.Line2, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            PhotoCard(photo.image, Modifier.matchParentSize(), ring = if (playing) Palette.Accent else null, radius = 24.dp, padding = 8.dp * scale)
            // The step number stays visible on a filled slot.
            Box(
                Modifier.align(Alignment.TopStart).padding(14.dp * scale).size(40.dp * scale).background(Palette.Surface, CircleShape).border(1.dp, Palette.Line, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text((index + 1).toString(), style = KidType.Label.copy(fontSize = KidType.Label.fontSize * 0.85f * scale))
            }
        } else {
            Text((index + 1).toString(), style = KidType.Display.copy(color = Palette.Ink3, fontSize = KidType.Display.fontSize * 0.8f * scale))
        }
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun SequencePreview() {
    val names = listOf("dom_lozko", "dom_wanna", "dom_stol", "dom_drzwi")
    val options = names.mapIndexed { i, n -> OptionUi(i.toLong(), PhotoSource.Asset("builtin/thumbs/$n.webp")) }
    KidTheme {
        SequenceScreen(
            SequenceUiState(loading = false, slots = listOf(options[0], null, null, null), tray = options.drop(1), active = setOf(1)),
            onEvent = {},
            onDrop = { _, _ -> false },
        )
    }
}
