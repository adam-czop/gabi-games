package com.familygames.presentation.games.missing

import android.content.res.Resources
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.familygames.R
import com.familygames.domain.game.round.MissingLevel
import com.familygames.domain.game.round.MissingRoundGenerator
import com.familygames.domain.game.round.MissingTask
import com.familygames.domain.game.round.Round
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.Photo
import com.familygames.domain.model.PhotoSource
import com.familygames.domain.repository.GameResultRepository
import com.familygames.presentation.common.VoicePrompts
import com.familygames.presentation.common.kidPressable
import com.familygames.presentation.common.label
import com.familygames.presentation.common.phrase
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
import com.familygames.presentation.theme.Dimens
import com.familygames.presentation.theme.KidTheme
import com.familygames.presentation.theme.Palette
import com.familygames.util.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update

/** M24 · "Czego brakuje?" (S33): remember the stage, the curtain falls, one photo is gone — which one? */
class MissingViewModel(
    levelKey: String,
    private val gamePhotos: GamePhotos,
    private val prompts: VoicePrompts,
    private val resources: Resources,
    private val generator: MissingRoundGenerator,
    results: GameResultRepository,
    clock: Clock,
) : RoundGameViewModel<Photo>(GameKind.MISSING, MissingLevel.fromKey(levelKey).key, results, clock) {
    private val level = MissingLevel.fromKey(levelKey)
    private var tasks: List<MissingTask> = emptyList()

    init {
        begin()
    }

    override suspend fun makeRounds(): List<Round<Photo>> {
        prompts.awaitReady()
        val dealt = generator.generate(gamePhotos.preferFamily(resources.getString(R.string.family_category_name)), level)
        tasks = dealt.map { it.first }
        return dealt.map { it.second }
    }

    override fun optionUi(item: Photo, index: Int) = OptionUi(item.id, item.thumb, item.label)

    override fun roundState(round: Round<Photo>, base: RoundUiState): RoundUiState {
        val task = tasks.getOrNull(base.round) ?: return base
        return base.copy(stage = task.shown.map { OptionUi(it.id, it.thumb, it.label) }, stageGap = null, curtain = false)
    }

    /** First time in a round: show the stage, close and open the curtain, then ask; later only the question. */
    override suspend fun ask(round: Round<Photo>) {
        val task = tasks.getOrNull(state.value.round) ?: return
        if (state.value.stageGap == null) {
            state.update { it.copy(curtain = false) }
            prompts.play(prompts.phrase(resources.getString(R.string.missing_look)))
            delay(LOOK_MS)
            state.update { it.copy(curtain = true) }
            delay(CURTAIN_MS)
            state.update { it.copy(curtain = false, stageGap = task.gap) }
            delay(CURTAIN_MS)
        }
        prompts.play(prompts.phrase(resources.getString(R.string.missing_question)))
    }

    /** The photo comes back to its place and its name is said. */
    override suspend fun praise(round: Round<Photo>) {
        state.update { it.copy(stageGap = null) }
        prompts.play(prompts.label(round.correct))
    }

    override val feedbackMs: Long = 1_600L

    private companion object {
        const val LOOK_MS = 2_600L
        const val CURTAIN_MS = 700L
    }
}

/** S33 · the stage with its curtain on top, three photos to choose from below (shown once the curtain opened). */
@Composable
fun MissingScreen(state: RoundUiState, onEvent: (RoundEvent) -> Unit, onStarShown: (Int) -> Unit = {}) {
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
        if (state.stage.isEmpty()) return@TaskGameScaffold
        Column(Modifier.fillMaxWidth().padding(top = 110.dp * scale), horizontalAlignment = Alignment.CenterHorizontally) {
            val card = if (state.stage.size >= 4) 200.dp else 230.dp
            Stage(state, card, scale)
            Box(Modifier.height(56.dp * scale), contentAlignment = Alignment.Center) {
                if (state.stageGap != null || state.solved != null) SpeakerButton(scale) { onEvent(RoundEvent.Replay) }
            }
            if (state.stageGap != null || state.solved != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp * scale)) {
                    state.options.forEachIndexed { i, option ->
                        key(state.game, state.round, i) {
                            val description = stringResource(R.string.cd_option, i + 1)
                            Box(
                                Modifier
                                    .size(200.dp * scale)
                                    .wiggle(state.wiggles[i] ?: 0)
                                    .pulse(state.hint == i)
                                    .semantics { contentDescription = description }
                                    .kidPressable(enabled = state.solved == null) { onEvent(RoundEvent.Tap(i)) },
                            ) {
                                PhotoCard(option.image, Modifier.matchParentSize(), ring = if (state.solved == i) Palette.Accent else null, padding = 10.dp * scale)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stage(state: RoundUiState, card: androidx.compose.ui.unit.Dp, scale: Float) {
    val gap = 28.dp
    val width = (card + gap) * state.stage.size - gap
    val curtain by animateFloatAsState(if (state.curtain) 1f else 0f, tween(600), label = "curtain")
    val shape = RoundedCornerShape(Dimens.RadiusXL)
    Box(
        Modifier
            .width(width * scale + 40.dp * scale)
            .height(card * scale + 40.dp * scale)
            .clip(shape)
            .background(Palette.Bg2, shape)
            .padding(20.dp * scale),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap * scale)) {
            state.stage.forEachIndexed { i, photo ->
                if (i == state.stageGap) {
                    val description = stringResource(R.string.cd_missing_gap)
                    Box(
                        Modifier
                            .size(card * scale)
                            .semantics { contentDescription = description }
                            .border(Dimens.StrokeSelected, Palette.Line2, RoundedCornerShape(Dimens.RadiusL)),
                    )
                } else {
                    PhotoCard(photo.image, Modifier.size(card * scale), padding = 10.dp * scale)
                }
            }
        }
        // The curtain slides down over the stage and back up.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationY = -(1f - curtain) * size.height
                    alpha = if (curtain > 0f) 1f else 0f
                }
                .background(Palette.Mist, RoundedCornerShape(bottomStart = Dimens.RadiusL, bottomEnd = Dimens.RadiusL)),
        )
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun MissingPreview() {
    val names = listOf("zwierzeta_kot", "zabawki_mis", "pojazdy_autobus")
    val photos = names.mapIndexed { i, n -> OptionUi(i.toLong(), PhotoSource.Asset("builtin/thumbs/$n.webp")) }
    KidTheme {
        MissingScreen(
            RoundUiState(
                loading = false, round = 1, totalRounds = 5, stage = photos, stageGap = 1,
                options = listOf(photos[1], OptionUi(7, PhotoSource.Asset("builtin/thumbs/zwierzeta_pies.webp")), OptionUi(8, PhotoSource.Asset("builtin/thumbs/dom_lampa.webp"))),
            ),
            onEvent = {},
        )
    }
}
