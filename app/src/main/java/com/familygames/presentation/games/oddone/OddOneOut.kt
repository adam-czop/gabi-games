package com.familygames.presentation.games.oddone

import android.content.res.Resources
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.familygames.R
import com.familygames.domain.game.round.OddLevel
import com.familygames.domain.game.round.OddOneOutRoundGenerator
import com.familygames.domain.game.round.Round
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.Photo
import com.familygames.domain.model.PhotoSource
import com.familygames.domain.repository.GameResultRepository
import com.familygames.presentation.common.VoicePrompts
import com.familygames.presentation.common.phrase
import com.familygames.presentation.common.kidPressable
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
import com.familygames.presentation.theme.Palette
import com.familygames.util.Clock

/** M13b · "Co nie pasuje?" (S22). */
class OddOneOutViewModel(
    levelKey: String,
    private val gamePhotos: GamePhotos,
    private val prompts: VoicePrompts,
    private val resources: Resources,
    private val generator: OddOneOutRoundGenerator,
    results: GameResultRepository,
    clock: Clock,
) : RoundGameViewModel<Photo>(GameKind.ODD_ONE_OUT, OddLevel.fromKey(levelKey).key, results, clock) {
    private val level = OddLevel.fromKey(levelKey)

    init {
        begin()
    }

    override suspend fun makeRounds(): List<Round<Photo>> {
        prompts.awaitReady()
        return generator.generate(gamePhotos.byCategory(), level)
    }

    override fun optionUi(item: Photo, index: Int) = OptionUi(item.id, item.thumb, item.label)

    override suspend fun ask(round: Round<Photo>) {
        prompts.play(prompts.phrase(resources.getString(R.string.prefix_odd_one)))
    }

    override val feedbackMs: Long = 1_400L
}

/** S22 · the odd photo floats away when found, the group stays together. */
@Composable
fun OddOneOutScreen(state: RoundUiState, onEvent: (RoundEvent) -> Unit, onStarShown: (Int) -> Unit = {}) {
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
        Column(Modifier.fillMaxWidth().padding(top = 130.dp * scale), horizontalAlignment = Alignment.CenterHorizontally) {
            SpeakerButton(scale) { onEvent(RoundEvent.Replay) }
            Spacer(Modifier.height(70.dp * scale))
            val size = if (state.options.size >= 4) 240.dp else 290.dp
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp * scale)) {
                state.options.forEachIndexed { i, option ->
                    androidx.compose.runtime.key(state.game, state.round, i) {
                        val gone = state.solved == i
                        val lift by animateFloatAsState(if (gone) 1f else 0f, tween(450), label = "lift")
                        val description = stringResource(R.string.cd_option, i + 1)
                        Box(
                            Modifier
                                .size(size * scale)
                                .wiggle(state.wiggles[i] ?: 0)
                                .pulse(state.hint == i)
                                .graphicsLayer {
                                    translationY = -lift * 160.dp.toPx() * scale
                                    alpha = 1f - lift
                                }
                                .semantics { contentDescription = description }
                                .kidPressable(enabled = state.solved == null) { onEvent(RoundEvent.Tap(i)) },
                        ) {
                            val grouped = state.solved != null && !gone
                            PhotoCard(option.image, Modifier.matchParentSize(), ring = if (grouped) Palette.Accent else null, padding = 12.dp * scale)
                        }
                    }
                }
            }
        }
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun OddOneOutPreview() {
    val names = listOf("zwierzeta_kot", "zwierzeta_pies", "zabawki_pilka", "zwierzeta_krowa")
    KidTheme {
        OddOneOutScreen(
            RoundUiState(loading = false, round = 2, totalRounds = 6, options = names.mapIndexed { i, n -> OptionUi(i.toLong(), PhotoSource.Asset("builtin/thumbs/$n.webp")) }),
            onEvent = {},
        )
    }
}
