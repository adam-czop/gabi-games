package com.familygames.presentation.games.halves

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.familygames.R
import com.familygames.domain.game.round.HalvesLevel
import com.familygames.domain.game.round.HalvesRoundGenerator
import com.familygames.domain.game.round.Round
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.Photo
import com.familygames.domain.model.PhotoSource
import com.familygames.domain.repository.GameResultRepository
import com.familygames.presentation.common.PhotoImage
import com.familygames.presentation.common.VoicePrompts
import com.familygames.presentation.common.kidPressable
import com.familygames.presentation.common.label
import com.familygames.presentation.common.phrase
import com.familygames.presentation.common.softShadow
import com.familygames.presentation.games.common.EmptyGameNote
import com.familygames.presentation.games.common.GamePhotos
import com.familygames.presentation.games.common.OptionUi
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

/** M25 · "Połówki" (S34): the left half of a photo — find its right half. */
class HalvesViewModel(
    levelKey: String,
    private val gamePhotos: GamePhotos,
    private val prompts: VoicePrompts,
    private val resources: Resources,
    private val generator: HalvesRoundGenerator,
    results: GameResultRepository,
    clock: Clock,
) : RoundGameViewModel<Photo>(GameKind.HALVES, HalvesLevel.fromKey(levelKey).key, results, clock) {
    private val level = HalvesLevel.fromKey(levelKey)

    init {
        begin()
    }

    override suspend fun makeRounds(): List<Round<Photo>> {
        prompts.awaitReady()
        return generator.generate(gamePhotos.preferFamily(resources.getString(R.string.family_category_name)), level)
    }

    override fun optionUi(item: Photo, index: Int) = OptionUi(item.id, item.thumb, item.label)

    override fun roundState(round: Round<Photo>, base: RoundUiState): RoundUiState = base.copy(subject = round.correct.thumb)

    override suspend fun ask(round: Round<Photo>) = prompts.play(prompts.phrase(resources.getString(R.string.halves_question)))

    override suspend fun praise(round: Round<Photo>) = prompts.play(prompts.label(round.correct))

    override val feedbackMs: Long = 1_500L
}

private enum class Half { LEFT, RIGHT }

/** One half of a square-cropped photo, [size] being the height (the half is size / 2 wide). */
@Composable
private fun HalfPhoto(source: PhotoSource?, half: Half, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.width(size / 2).height(size).clipToBounds(), contentAlignment = Alignment.Center) {
        // The full photo is centred in the half-wide box; shift it so the wanted half fills the box.
        val shift = if (half == Half.LEFT) size / 4 else -size / 4
        PhotoImage(source, null, Modifier.requiredSize(size).offset(x = shift))
    }
}

/** S34 · the half photo with an empty place on top, right halves to choose from below. */
@Composable
fun HalvesScreen(state: RoundUiState, onEvent: (RoundEvent) -> Unit, onStarShown: (Int) -> Unit = {}) {
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
        val subject = state.subject ?: return@TaskGameScaffold
        Column(Modifier.fillMaxWidth().padding(top = 104.dp * scale), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp * scale)) {
                SpeakerButton(scale) { onEvent(RoundEvent.Replay) }
                Whole(subject, solved = state.solved != null, size = 290.dp * scale)
                Spacer(Modifier.size(Dimens.TouchSecondary * scale))
            }
            Spacer(Modifier.height(30.dp * scale))
            Row(horizontalArrangement = Arrangement.spacedBy(44.dp * scale)) {
                state.options.forEachIndexed { i, option ->
                    key(state.game, state.round, i) {
                        val description = stringResource(R.string.cd_option, i + 1)
                        val shape = RoundedCornerShape(Dimens.RadiusL)
                        Box(
                            Modifier
                                .wiggle(state.wiggles[i] ?: 0)
                                .pulse(state.hint == i)
                                .semantics { contentDescription = description }
                                .kidPressable(enabled = state.solved == null) { onEvent(RoundEvent.Tap(i)) }
                                .softShadow(shape)
                                .background(Palette.Surface, shape)
                                .border(if (state.solved == i) Dimens.StrokeSelected else Dimens.Stroke, if (state.solved == i) Palette.Accent else Palette.Line, shape)
                                .padding(10.dp * scale)
                                .clip(RoundedCornerShape(Dimens.RadiusM)),
                        ) {
                            HalfPhoto(option.image, Half.RIGHT, 260.dp * scale)
                        }
                    }
                }
            }
        }
    }
}

/** The left half and, once found, the right half next to it — the whole photo again. */
@Composable
private fun Whole(subject: PhotoSource, solved: Boolean, size: Dp) {
    val shape = RoundedCornerShape(Dimens.RadiusL)
    val description = stringResource(R.string.cd_halves_whole)
    Box(
        Modifier
            .semantics { contentDescription = description }
            .softShadow(shape)
            .background(Palette.Surface, shape)
            .border(if (solved) Dimens.StrokeSelected else Dimens.Stroke, if (solved) Palette.Accent else Palette.Line, shape)
            .padding(12.dp)
            .clip(RoundedCornerShape(Dimens.RadiusM)),
    ) {
        Row(Modifier.size(size)) {
            HalfPhoto(subject, Half.LEFT, size)
            if (solved) {
                HalfPhoto(subject, Half.RIGHT, size)
            } else {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(size / 2)
                        .padding(start = 6.dp)
                        .border(Dimens.StrokeSelected, Palette.Line2, RoundedCornerShape(Dimens.RadiusM)),
                ) { Box(Modifier.fillMaxSize().background(Palette.Bg2.copy(alpha = 0.6f))) }
            }
        }
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun HalvesPreview() {
    val names = listOf("zwierzeta_kot", "zabawki_mis", "pojazdy_autobus")
    KidTheme {
        HalvesScreen(
            RoundUiState(
                loading = false, round = 1, totalRounds = 5, subject = PhotoSource.Asset("builtin/thumbs/${names[0]}.webp"),
                options = names.mapIndexed { i, n -> OptionUi(i.toLong(), PhotoSource.Asset("builtin/thumbs/$n.webp")) },
            ),
            onEvent = {},
        )
    }
}
