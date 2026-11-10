package com.familygames.presentation.games.puzzle

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.familygames.R
import com.familygames.domain.game.puzzle.PuzzleTuning
import com.familygames.domain.game.puzzle.SwapBoard
import com.familygames.presentation.common.KidFrame
import com.familygames.presentation.common.RoundIconButton
import com.familygames.presentation.common.WinOverlay
import com.familygames.presentation.common.kidPressable
import com.familygames.presentation.common.softShadow
import com.familygames.presentation.theme.Dimens
import com.familygames.presentation.theme.KidShapes
import com.familygames.presentation.theme.KidType
import com.familygames.presentation.theme.Palette

/** S07 · swap neighbouring tiles until the picture is whole; the reference picture stays visible. */
@Composable
fun SwapPuzzleScreen(state: SwapUiState, onEvent: (SwapEvent) -> Unit, onStarShown: (Int) -> Unit = {}) {
    BackHandler { onEvent(SwapEvent.Home) }
    KidFrame { scale ->
        Box(Modifier.fillMaxSize().then(if (state.won) Modifier.blur(6.dp) else Modifier)) {
            RoundIconButton(
                icon = R.drawable.ic_home,
                contentDescription = stringResource(R.string.cd_home),
                onClick = { onEvent(SwapEvent.Home) },
                modifier = Modifier.padding(start = Dimens.ScreenPaddingH, top = Dimens.ScreenPaddingTop),
            )
            RoundIconButton(
                icon = R.drawable.ic_restart,
                contentDescription = stringResource(R.string.cd_restart),
                onClick = { onEvent(SwapEvent.Restart) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = Dimens.ScreenPaddingH, top = Dimens.ScreenPaddingTop),
            )
            val image = state.image
            val board = state.board
            if (state.missingPhoto) {
                Text(stringResource(R.string.puzzle_photo_missing), style = KidType.Label, modifier = Modifier.align(Alignment.Center))
            } else if (image != null && board != null) {
                SwapGrid(state, board, image, scale, onEvent, Modifier.offset(120.dp * scale, 150.dp * scale))
                Reference(image, scale, Modifier.align(Alignment.TopEnd).padding(top = 150.dp * scale, end = 88.dp * scale))
            }
        }
        val stars = state.stars
        if (state.won && stars != null) {
            WinOverlay(stars = stars, onPlayAgain = { onEvent(SwapEvent.Restart) }, onMenu = { onEvent(SwapEvent.Home) }, scale = scale, onStarShown = onStarShown)
        }
    }
}

@Composable
private fun SwapGrid(state: SwapUiState, board: SwapBoard, image: ImageBitmap, scale: Float, onEvent: (SwapEvent) -> Unit, modifier: Modifier) {
    val n = board.size
    val padding = 14.dp * scale
    val innerW = 692.dp * scale
    val innerH = innerW * 3f / 4f
    val gap by animateDpAsState(if (state.merged) 0.dp else Dimens.GridGapSwap * scale, tween(300), label = "gap")
    val radius by animateDpAsState(if (state.merged) 0.dp else 10.dp, tween(300), label = "radius")
    val tileW = (innerW - gap * (n - 1)) / n
    val tileH = (innerH - gap * (n - 1)) / n
    val neighbours = state.selected?.let { board.neighbours(it) }.orEmpty()

    Box(
        modifier
            .softShadow(KidShapes.M)
            .background(Palette.Surface, KidShapes.M)
            .border(Dimens.Stroke, Palette.Line, KidShapes.M)
            .padding(padding)
            .size(innerW, innerH),
    ) {
        for (tile in 0 until n * n) {
            val pos = board.tiles.indexOf(tile)
            val x by animateDpAsState((tileW + gap) * (pos % n), tween(PuzzleTuning.SWAP_MS), label = "x$tile")
            val y by animateDpAsState((tileH + gap) * (pos / n), tween(PuzzleTuning.SWAP_MS), label = "y$tile")
            val selected = state.selected == pos
            val lift by animateFloatAsState(if (selected) 1.03f else 1f, tween(150), label = "lift$tile")
            SwapTile(
                image = image,
                tile = tile,
                size = n,
                width = tileW,
                height = tileH,
                radius = radius,
                selected = selected,
                neighbour = pos in neighbours,
                correct = board.isCorrect(pos) && !state.merged,
                modifier = Modifier
                    .offset { IntOffset(x.roundToPx(), y.roundToPx()) }
                    .zIndex(if (selected) 1f else 0f)
                    .graphicsLayer {
                        scaleX = lift
                        scaleY = lift
                    },
                onTap = { onEvent(SwapEvent.TileTapped(pos)) },
            )
        }
    }
}

@Composable
private fun SwapTile(
    image: ImageBitmap,
    tile: Int,
    size: Int,
    width: Dp,
    height: Dp,
    radius: Dp,
    selected: Boolean,
    neighbour: Boolean,
    correct: Boolean,
    modifier: Modifier,
    onTap: () -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val description = stringResource(R.string.cd_tile, tile + 1)
    Box(
        modifier
            .size(width, height)
            .kidPressable(onClick = onTap)
            .semantics {
                contentDescription = description
                this.selected = selected
            }
            .then(
                when {
                    selected -> Modifier.border(4.dp, Palette.Accent, shape)
                    neighbour -> Modifier.border(3.dp, Palette.AccentSoft, shape)
                    else -> Modifier
                },
            ),
    ) {
        PuzzlePieceImage(
            image, tile / size, tile % size, size, size,
            Modifier
                .fillMaxSize()
                .padding(if (selected) 4.dp else if (neighbour) 3.dp else 0.dp)
                .clip(shape),
        )
        if (correct) {
            // Small accent dot: the child sees the progress (docs/05 §3).
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(22.dp)
                    .background(Palette.Surface, CircleShape)
                    .padding(3.dp)
                    .background(Palette.Accent, CircleShape),
            )
        }
    }
}

@Composable
private fun Reference(image: ImageBitmap, scale: Float, modifier: Modifier) {
    Column(
        modifier
            .width(300.dp * scale)
            .background(Palette.Surface, KidShapes.M)
            .border(Dimens.Stroke, Palette.Line, KidShapes.M)
            .padding(12.dp * scale),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PuzzleWholeImage(image, Modifier.fillMaxWidth().height(207.dp * scale).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.height(10.dp * scale))
        Text(stringResource(R.string.puzzle_reference), style = KidType.Label.copy(fontSize = KidType.Label.fontSize * (20f / 24f) * scale))
    }
}
