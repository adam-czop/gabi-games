package com.familygames.presentation.games.counting

import android.content.res.Resources
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familygames.R
import com.familygames.domain.game.round.CountingLevel
import com.familygames.domain.game.round.CountingRoundGenerator
import com.familygames.domain.game.round.CountingTask
import com.familygames.domain.game.round.Round
import com.familygames.domain.game.round.ScatterLayout
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.PhotoSource
import com.familygames.domain.repository.GameResultRepository
import com.familygames.domain.voice.VoiceStep
import com.familygames.presentation.common.Sfx
import com.familygames.presentation.common.VoicePrompts
import com.familygames.presentation.common.kidPressable
import com.familygames.presentation.common.question
import com.familygames.presentation.common.softShadow
import com.familygames.presentation.games.common.EmptyGameNote
import com.familygames.presentation.games.common.GamePhotos
import com.familygames.presentation.games.common.OptionUi
import com.familygames.presentation.games.common.PhotoCard
import com.familygames.presentation.games.common.RoundEvent
import com.familygames.presentation.games.common.RoundGameViewModel
import com.familygames.presentation.games.common.RoundUiState
import com.familygames.presentation.games.common.TaskGameScaffold
import com.familygames.presentation.games.common.pulse
import com.familygames.presentation.games.common.wiggle
import com.familygames.presentation.games.whereis.SpeakerButton
import com.familygames.presentation.theme.KidTheme
import com.familygames.presentation.theme.KidType
import com.familygames.presentation.theme.Palette
import com.familygames.util.Clock
import kotlinx.coroutines.delay
import kotlin.random.Random

/** M17 · "Ile jest?" (S27). Options are the numbers 1…max. */
class CountingViewModel(
    levelKey: String,
    private val gamePhotos: GamePhotos,
    private val prompts: VoicePrompts,
    private val resources: Resources,
    private val generator: CountingRoundGenerator,
    results: GameResultRepository,
    clock: Clock,
) : RoundGameViewModel<Int>(GameKind.COUNTING, CountingLevel.fromKey(levelKey).key, results, clock) {
    private val level = CountingLevel.fromKey(levelKey)
    private var tasks: List<CountingTask> = emptyList()

    init {
        begin()
    }

    override suspend fun makeRounds(): List<Round<Int>> {
        prompts.awaitReady()
        tasks = generator.generateTasks(gamePhotos.all().filter { it.countable }, level)
        return tasks.map { Round((1..level.max).toList(), it.count - 1) }
    }

    override fun optionUi(item: Int, index: Int) = OptionUi(item.toLong(), number = item)

    override fun roundState(round: Round<Int>, base: RoundUiState): RoundUiState {
        val task = tasks.getOrNull(base.round) ?: return base
        return base.copy(subject = task.photo.thumb, subjectLabel = task.photo.label, count = task.count)
    }

    override suspend fun ask(round: Round<Int>) {
        val task = tasks.getOrNull(state.value.round) ?: return
        prompts.play(prompts.question(task.photo, resources.getString(R.string.prefix_how_many)))
    }

    /** The tiles hop one by one while the numbers are said (docs/12 M17). */
    override suspend fun praise(round: Round<Int>) {
        val count = round.correctIndex + 1
        if (prompts.ttsAvailable.value) {
            val words = resources.getString(R.string.tts_numbers).split(',').take(count).joinToString(", ")
            prompts.play(listOf(VoiceStep.Speech(words)))
        } else {
            repeat(count) { i ->
                sound(Sfx.TAP, 0.9f + 0.12f * i)
                delay(HOP_MS)
            }
        }
    }

    override val feedbackMs: Long = 1_800L

    fun countTap(number: Int) = sound(Sfx.TAP, 0.8f + 0.1f * number)

    companion object {
        const val HOP_MS = 260L
    }
}

private val Tile = 140.dp

/** S27 · copies of one photo, counted by tapping them, and number buttons with dots below. */
@Composable
fun CountingScreen(state: RoundUiState, onEvent: (RoundEvent) -> Unit, onCountTap: (Int) -> Unit, onStarShown: (Int) -> Unit = {}) {
    TaskGameScaffold(
        onHome = { onEvent(RoundEvent.Home) },
        onRestart = { onEvent(RoundEvent.Restart) },
        progress = if (state.totalRounds > 0) state.round to state.totalRounds else null,
        winStars = state.winStars,
        onStarShown = onStarShown,
    ) { scale ->
        if (state.empty) {
            EmptyGameNote()
            return@TaskGameScaffold
        }
        Column(Modifier.fillMaxWidth().padding(top = 110.dp * scale), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp * scale)) {
                SpeakerButton(scale) { onEvent(RoundEvent.Replay) }
                state.subjectLabel?.let {
                    Text(
                        stringResource(R.string.counting_question, it.lowercase()),
                        style = KidType.Label.copy(color = Palette.Ink2, fontStyle = FontStyle.Italic, fontSize = KidType.Label.fontSize * scale),
                    )
                }
            }
            androidx.compose.runtime.key(state.game, state.round) {
                CountArea(state, scale, onCountTap, Modifier.fillMaxWidth().height(330.dp * scale).padding(horizontal = 120.dp * scale, vertical = 12.dp * scale))
            }
            Spacer(Modifier.height(10.dp * scale))
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp * scale)) {
                state.options.forEachIndexed { i, option -> NumberButton(option.number ?: 0, state, i, scale) { onEvent(RoundEvent.Tap(i)) } }
            }
        }
    }
}

@Composable
private fun CountArea(state: RoundUiState, scale: Float, onCountTap: (Int) -> Unit, modifier: Modifier) {
    val numbers = remember { mutableStateMapOf<Int, Int>() }
    val hops = remember { List(5) { Animatable(0f) } }
    LaunchedEffect(state.solved) {
        if (state.solved != null) {
            repeat(state.count) { i ->
                hops[i].animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 900f))
                hops[i].animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 900f))
                delay(CountingViewModel.HOP_MS / 2)
            }
        }
    }
    BoxWithConstraints(modifier) {
        val spots = remember(state.game, state.round, maxWidth, maxHeight) {
            ScatterLayout.place(state.count, maxWidth.value, maxHeight.value, Tile.value * scale, 16f, Random(state.game * 31 + state.round))
        }
        spots.forEachIndexed { i, spot ->
            Box(
                Modifier
                    .offset(spot.x.dp, spot.y.dp)
                    .size(Tile * scale)
                    .graphicsLayer { translationY = -hops[i].value * 28.dp.toPx() }
                    .kidPressable(enabled = numbers[i] == null && state.solved == null) {
                        val n = numbers.size + 1
                        numbers[i] = n
                        onCountTap(n)
                    },
            ) {
                PhotoCard(state.subject, Modifier.matchParentSize(), radius = 20.dp, padding = 8.dp * scale)
                numbers[i]?.let { n ->
                    Box(
                        Modifier.align(Alignment.TopEnd).size(44.dp * scale).background(Palette.Accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(n.toString(), style = KidType.Label.copy(color = Palette.Surface, fontSize = 24.sp * scale))
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberButton(number: Int, state: RoundUiState, index: Int, scale: Float, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    val solved = state.solved == index
    val description = stringResource(R.string.cd_number, number)
    Column(
        Modifier
            .size(112.dp * scale, 140.dp * scale)
            .wiggle(state.wiggles[index] ?: 0)
            .pulse(state.hint == index)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .kidPressable(enabled = state.solved == null, onClick = onClick)
            .softShadow(shape)
            .background(if (solved) Palette.AccentSoft else Palette.Surface, shape)
            .border(if (solved) 4.dp else 1.dp, if (solved) Palette.Accent else Palette.Line, shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(number.toString(), style = KidType.Display.copy(fontSize = 56.sp * scale))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp * scale)) {
            repeat(number) { Box(Modifier.size(10.dp * scale).background(Palette.Ink3, CircleShape)) }
        }
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun CountingPreview() {
    KidTheme {
        CountingScreen(
            RoundUiState(
                loading = false, round = 1, totalRounds = 5, count = 3, subjectLabel = "Piłka",
                subject = PhotoSource.Asset("builtin/thumbs/zabawki_pilka.webp"),
                options = (1..5).map { OptionUi(it.toLong(), number = it) },
            ),
            onEvent = {},
            onCountTap = {},
        )
    }
}
