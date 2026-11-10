package com.familygames.presentation.games.puzzle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.familygames.R
import com.familygames.domain.game.puzzle.PuzzleDifficulty
import com.familygames.domain.game.puzzle.PuzzleMode
import com.familygames.domain.model.PhotoSource
import com.familygames.presentation.common.KidFrame
import com.familygames.presentation.common.PhotoImage
import com.familygames.presentation.common.RoundIconButton
import com.familygames.presentation.common.kidPressable
import com.familygames.presentation.common.softShadow
import com.familygames.presentation.theme.Dimens
import com.familygames.presentation.theme.KidShapes
import com.familygames.presentation.theme.KidTheme
import com.familygames.presentation.theme.KidType
import com.familygames.presentation.theme.Palette

/** S05 · Puzzle: tap a picture to start with the selected difficulty. */
@Composable
fun PuzzleSetupScreen(state: PuzzleSetupUiState, onEvent: (PuzzleSetupEvent) -> Unit, onBack: () -> Unit) {
    KidFrame { scale ->
        RoundIconButton(
            icon = R.drawable.ic_back,
            contentDescription = stringResource(R.string.cd_back),
            onClick = onBack,
            modifier = Modifier.padding(start = Dimens.ScreenPaddingH, top = Dimens.ScreenPaddingTop),
        )
        Text(
            text = stringResource(R.string.puzzle_setup_title),
            style = KidType.Title.copy(fontSize = KidType.Title.fontSize * scale),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 50.dp * scale),
        )
        if (!state.loading && state.photos.isEmpty()) {
            Text(stringResource(R.string.puzzle_no_photos), style = KidType.Label, modifier = Modifier.align(Alignment.Center))
        }
        // More than 6 pictures scroll vertically — scrolling is fine here (docs/03 S05).
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 130.dp * scale, bottom = (40 + 116 + 24).dp * scale),
            contentPadding = PaddingValues(horizontal = 80.dp * scale, vertical = 10.dp * scale),
            horizontalArrangement = Arrangement.spacedBy(24.dp * scale),
            verticalArrangement = Arrangement.spacedBy(24.dp * scale),
        ) {
            items(state.photos, key = { it.id }) { photo ->
                PictureTile(photo, scale) { onEvent(PuzzleSetupEvent.PhotoTapped(photo.id)) }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp * scale),
            horizontalArrangement = Arrangement.spacedBy(20.dp * scale),
        ) {
            PuzzleDifficulty.entries.forEach { difficulty ->
                DifficultyButton(difficulty, selected = difficulty == state.difficulty, scale = scale) {
                    onEvent(PuzzleSetupEvent.SelectDifficulty(difficulty))
                }
            }
        }
    }
}

@Composable
private fun PictureTile(photo: PuzzlePhotoUi, scale: Float, onClick: () -> Unit) {
    val description = photo.label?.let { stringResource(R.string.cd_puzzle_photo, it) } ?: stringResource(R.string.cd_puzzle_photo_unlabeled)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(205.dp * scale)
            .softShadow(KidShapes.L)
            .kidPressable(onClick = onClick)
            .semantics { contentDescription = description }
            .background(Palette.Surface, KidShapes.L)
            .border(Dimens.Stroke, Palette.Line, KidShapes.L)
            .padding(10.dp * scale),
    ) {
        PhotoImage(photo.thumb, null, Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp * scale)))
    }
}

@Composable
private fun DifficultyButton(difficulty: PuzzleDifficulty, selected: Boolean, scale: Float, onClick: () -> Unit) {
    val shape = KidShapes.M
    val description = stringResource(
        if (difficulty.mode == PuzzleMode.SNAP) R.string.cd_difficulty_snap else R.string.cd_difficulty_swap,
        difficulty.pieces,
    )
    Row(
        modifier = Modifier
            .size(200.dp * scale, 116.dp * scale)
            .kidPressable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                this.selected = selected
            }
            .background(if (selected) Palette.AccentSoft else Palette.Surface, shape)
            .border(Dimens.Stroke, if (selected) Palette.Accent else Palette.Line, shape),
        horizontalArrangement = Arrangement.spacedBy(16.dp * scale, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniGrid(difficulty.rows, difficulty.cols, if (selected) Palette.Accent else Palette.Line2, scale)
        val tint = if (selected) Palette.AccentInk else Palette.Ink2
        if (difficulty.mode == PuzzleMode.SNAP) {
            Text(stringResource(R.string.puzzle_snap_label), style = KidType.Label.copy(color = tint, fontSize = KidType.Label.fontSize * scale))
        } else {
            Icon(painterResource(R.drawable.ic_swap), null, tint = tint, modifier = Modifier.size(34.dp * scale))
        }
    }
}

@Composable
private fun MiniGrid(rows: Int, cols: Int, color: Color, scale: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp * scale)) {
                repeat(cols) { Box(Modifier.size(20.dp * scale).background(color, RoundedCornerShape(5.dp * scale))) }
            }
        }
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun PuzzleSetupPreview() {
    KidTheme {
        PuzzleSetupScreen(
            state = PuzzleSetupUiState(
                loading = false,
                photos = listOf("dom_kanapa", "zwierzeta_kot", "zabawki_mis").mapIndexed { i, n ->
                    PuzzlePhotoUi(i.toLong(), PhotoSource.Asset("builtin/thumbs/$n.webp"), n)
                },
            ),
            onEvent = {},
            onBack = {},
        )
    }
}
