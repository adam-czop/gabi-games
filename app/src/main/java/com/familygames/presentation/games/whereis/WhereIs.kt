package com.familygames.presentation.games.whereis

import android.content.res.Resources
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.familygames.R
import com.familygames.domain.game.round.Round
import com.familygames.domain.game.round.WhereIsLevel
import com.familygames.domain.game.round.WhereIsRoundGenerator
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.Photo
import com.familygames.domain.model.PhotoSource
import com.familygames.domain.repository.GameResultRepository
import com.familygames.domain.voice.PromptPrefix
import com.familygames.presentation.common.VoicePrompts
import com.familygames.presentation.common.kidPressable
import com.familygames.presentation.common.label
import com.familygames.presentation.common.question
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
import com.familygames.presentation.theme.KidTheme
import com.familygames.presentation.theme.KidType
import com.familygames.presentation.theme.Palette
import com.familygames.util.Clock

/** M11 · "Gdzie jest…?" (S18). */
class WhereIsViewModel(
    levelKey: String,
    private val categoryId: Long,
    private val gamePhotos: GamePhotos,
    private val prompts: VoicePrompts,
    private val resources: Resources,
    private val generator: WhereIsRoundGenerator,
    results: GameResultRepository,
    clock: Clock,
) : RoundGameViewModel<Photo>(GameKind.WHERE_IS, WhereIsLevel.fromKey(levelKey).key, results, clock, categoryId) {
    private val level = WhereIsLevel.fromKey(levelKey)
    private var withVoice: Set<Long> = emptySet()

    init {
        begin()
    }

    override suspend fun makeRounds(): List<Round<Photo>> {
        prompts.awaitReady()
        val rounds = generator.generate(gamePhotos.byCategory()[categoryId].orEmpty(), level)
        withVoice = rounds.map { it.correct }.filter { prompts.question(it, PromptPrefix.WHERE_IS, prefix()).isNotEmpty() }.map { it.id }.toSet()
        return rounds
    }

    private fun prefix() = resources.getString(R.string.prefix_where_is)

    override fun optionUi(item: Photo, index: Int) = OptionUi(item.id, item.thumb, item.label)

    override fun roundState(round: Round<Photo>, base: RoundUiState): RoundUiState {
        val target = round.correct
        return base.copy(subject = target.thumb, subjectLabel = target.label, voiceless = target.id !in withVoice)
    }

    override suspend fun ask(round: Round<Photo>) = prompts.play(prompts.question(round.correct, PromptPrefix.WHERE_IS, prefix()))

    override suspend fun praise(round: Round<Photo>) = prompts.play(prompts.label(round.correct))
}

/** S18 · speaker (or the model picture without voice) above 2–4 photo cards. */
@Composable
fun WhereIsScreen(state: RoundUiState, onEvent: (RoundEvent) -> Unit, onStarShown: (Int) -> Unit = {}) {
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
        Column(Modifier.fillMaxWidth().padding(top = 120.dp * scale), horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.voiceless) {
                val model = stringResource(R.string.cd_model_photo)
                PhotoCard(
                    state.subject,
                    Modifier.size(160.dp * scale).semantics { contentDescription = model },
                    padding = 8.dp * scale,
                )
                Spacer(Modifier.height(30.dp * scale))
            } else {
                SpeakerButton(scale) { onEvent(RoundEvent.Replay) }
                Spacer(Modifier.height(14.dp * scale))
                Text(
                    state.subjectLabel?.let { stringResource(R.string.where_question, it.lowercase()) }.orEmpty(),
                    style = KidType.Label.copy(color = Palette.Ink2, fontStyle = FontStyle.Italic, fontSize = KidType.Label.fontSize * scale),
                )
                Spacer(Modifier.height(36.dp * scale))
            }
            ChoiceRow(state, scale, cardHeight = if (state.options.size >= 4) 270.dp else 326.dp) { onEvent(RoundEvent.Tap(it)) }
        }
    }
}

/** Big "say it again" button (96 dp, docs/12 M11). */
@Composable
fun SpeakerButton(scale: Float, onClick: () -> Unit) {
    val description = stringResource(R.string.cd_replay_question)
    Box(
        Modifier
            .size(116.dp * scale)
            .kidPressable(onClick = onClick)
            .semantics { contentDescription = description }
            .background(Palette.AccentSoft, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_volume_on), null, tint = Palette.AccentInk, modifier = Modifier.size(52.dp * scale))
    }
}

/** Row of answer cards: accent ring and 1.06 scale when right, wiggle when wrong, pulse as a hint. */
@Composable
fun ChoiceRow(state: RoundUiState, scale: Float, cardHeight: Dp, onTap: (Int) -> Unit) {
    val count = state.options.size
    val width = if (count >= 4) 230.dp else 278.dp
    Row(horizontalArrangement = Arrangement.spacedBy(36.dp * scale)) {
        state.options.forEachIndexed { i, option ->
            androidx.compose.runtime.key(state.game, state.round, i) {
                val solved = state.solved == i
                val grow by animateFloatAsState(if (solved) 1.06f else 1f, spring(dampingRatio = 0.6f), label = "grow")
                val description = stringResource(R.string.cd_option, i + 1)
                Box(
                    Modifier
                        .size(width * scale, cardHeight * scale)
                        .wiggle(state.wiggles[i] ?: 0)
                        .pulse(state.hint == i)
                        .graphicsLayer {
                            scaleX = grow
                            scaleY = grow
                        }
                        .semantics { contentDescription = description }
                        .kidPressable(enabled = state.solved == null) { onTap(i) },
                ) {
                    PhotoCard(option.image, Modifier.matchParentSize(), ring = if (solved) Palette.Accent else null, padding = 14.dp * scale)
                    if (solved) CheckBadge(scale, Modifier.align(Alignment.TopEnd).offset(x = 16.dp * scale, y = (-16).dp * scale))
                }
            }
        }
    }
}

@Composable
fun CheckBadge(scale: Float, modifier: Modifier = Modifier) {
    Box(
        modifier.size(52.dp * scale).background(Palette.Bg, CircleShape).padding(4.dp * scale).background(Palette.Accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_check), null, tint = Color.White, modifier = Modifier.size(28.dp * scale))
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun WhereIsPreview() {
    val names = listOf("zwierzeta_kot", "zwierzeta_pies", "zwierzeta_krowa")
    KidTheme {
        WhereIsScreen(
            RoundUiState(
                loading = false, round = 1, totalRounds = 6, solved = 1, subjectLabel = "Pies",
                options = names.mapIndexed { i, n -> OptionUi(i.toLong(), PhotoSource.Asset("builtin/thumbs/$n.webp")) },
            ),
            onEvent = {},
        )
    }
}
