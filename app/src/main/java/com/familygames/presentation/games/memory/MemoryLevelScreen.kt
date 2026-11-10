package com.familygames.presentation.games.memory

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.familygames.R
import com.familygames.domain.model.MemoryLevel
import com.familygames.domain.model.PhotoSource
import com.familygames.presentation.common.KidFrame
import com.familygames.presentation.common.PhotoImage
import com.familygames.presentation.common.RoundIconButton
import com.familygames.presentation.common.StarsRow
import com.familygames.presentation.common.kidPressable
import com.familygames.presentation.common.softShadow
import com.familygames.presentation.theme.Dimens
import com.familygames.presentation.theme.KidTheme
import com.familygames.presentation.theme.KidType
import com.familygames.presentation.theme.Palette

/** S02 · level and category choice for Memorki. Tapping a level starts the game straight away. */
@Composable
fun MemoryLevelScreen(
    state: MemoryLevelUiState,
    onEvent: (MemoryLevelEvent) -> Unit,
    onBack: () -> Unit,
    shakeRequests: Map<MemoryLevel, Int> = emptyMap(),
) {
    KidFrame { scale ->
        RoundIconButton(
            icon = R.drawable.ic_back,
            contentDescription = stringResource(R.string.cd_back),
            onClick = onBack,
            modifier = Modifier.padding(start = Dimens.ScreenPaddingH, top = Dimens.ScreenPaddingTop),
        )
        Text(
            text = stringResource(R.string.memory_levels_title),
            style = KidType.Title.copy(fontSize = KidType.Title.fontSize * scale),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 50.dp * scale),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 64.dp * scale, end = 64.dp * scale, top = 170.dp * scale),
            horizontalArrangement = Arrangement.spacedBy(28.dp * scale),
        ) {
            state.levels.forEach { tile ->
                LevelTile(
                    tile = tile,
                    scale = scale,
                    shakeKey = shakeRequests[tile.level] ?: 0,
                    onClick = { onEvent(MemoryLevelEvent.LevelTapped(tile.level)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (!state.loading && state.categories.isEmpty()) {
            Text(
                text = stringResource(R.string.memory_no_categories),
                style = KidType.Label,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp * scale),
            )
        }
        LazyRow(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 56.dp * scale),
            contentPadding = PaddingValues(horizontal = 64.dp * scale),
            horizontalArrangement = Arrangement.spacedBy(20.dp * scale, Alignment.CenterHorizontally),
        ) {
            items(state.categories, key = { it.id }) { chip ->
                CategoryChip(chip, selected = chip.id == state.selectedCategoryId, scale = scale) {
                    onEvent(MemoryLevelEvent.SelectCategory(chip.id))
                }
            }
        }
    }
}

@Composable
private fun LevelTile(tile: LevelTileUi, scale: Float, shakeKey: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Dimens.RadiusL)
    val shake = remember { Animatable(0f) }
    // Not playable (locked / too few photos): a gentle shake instead of any negative feedback.
    LaunchedEffect(shakeKey) {
        if (shakeKey > 0) {
            shake.animateTo(0f, keyframes {
                durationMillis = 360
                -8f at 60
                8f at 140
                -5f at 220
                3f at 290
            })
        }
    }
    val number = MemoryLevel.entries.indexOf(tile.level) + 1
    val cardsText = pluralStringResource(R.plurals.memory_cards, tile.level.cards, tile.level.cards)
    val description = when (tile.status) {
        LevelStatus.Locked -> stringResource(R.string.cd_level_locked, number)
        LevelStatus.NotEnoughPhotos -> stringResource(R.string.cd_level_not_enough, number)
        else -> stringResource(R.string.cd_level, number, cardsText)
    }
    val locked = tile.status == LevelStatus.Locked
    val dimmed = tile.status == LevelStatus.NotEnoughPhotos
    Box(
        modifier = modifier
            .height(380.dp * scale)
            .graphicsLayer { translationX = shake.value * density }
            .then(if (locked) Modifier.dashedBorder(Dimens.RadiusL) else Modifier.softShadow(shape))
            .kidPressable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                selected = tile.status == LevelStatus.Selected
            }
            .then(if (locked) Modifier else Modifier.background(Palette.Surface, shape))
            .then(
                when (tile.status) {
                    LevelStatus.Selected -> Modifier.border(3.dp, Palette.Accent, shape)
                    LevelStatus.Locked -> Modifier
                    else -> Modifier.border(Dimens.Stroke, Palette.Line, shape)
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .padding(start = 20.dp * scale, top = 18.dp * scale)
                .size(46.dp * scale)
                .background(Palette.Bg2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), style = KidType.Label.copy(fontSize = KidType.Label.fontSize * scale))
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp * scale),
        ) {
            MiniGrid(
                rows = tile.level.rows,
                cols = tile.level.cols,
                color = when (tile.status) {
                    LevelStatus.Selected -> Palette.Accent
                    LevelStatus.Locked, LevelStatus.NotEnoughPhotos -> Palette.Bg2
                    LevelStatus.Available -> Palette.Line
                },
                scale = scale,
            )
            Text(
                text = cardsText,
                style = KidType.Label.copy(
                    fontSize = KidType.Label.fontSize * (22f / 24f) * scale,
                    color = if (dimmed) Palette.Ink3 else Palette.Ink2,
                ),
            )
            when (tile.status) {
                LevelStatus.Locked -> Unit
                LevelStatus.NotEnoughPhotos -> Icon(
                    painterResource(R.drawable.ic_camera),
                    contentDescription = null,
                    tint = Palette.Ink3,
                    modifier = Modifier.size(36.dp * scale),
                )
                else -> StarsRow(tile.bestStars, size = 36.dp * scale, gap = 6.dp * scale)
            }
        }
        if (locked) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Palette.Bg, CircleShape)
                    .padding(10.dp * scale),
            ) {
                Icon(painterResource(R.drawable.ic_lock), null, tint = Palette.Ink3, modifier = Modifier.size(64.dp * scale))
            }
        }
    }
}

@Composable
internal fun MiniGrid(rows: Int, cols: Int, color: Color, scale: Float) {
    val cell = 32.dp * scale
    val gap = 8.dp * scale
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(cols) { Box(Modifier.size(cell).background(color, RoundedCornerShape(8.dp * scale))) }
            }
        }
    }
}

@Composable
internal fun CategoryChip(chip: CategoryChipUi, selected: Boolean, scale: Float, onClick: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = Modifier
            .kidPressable(onClick = onClick)
            .semantics { this.selected = selected }
            .background(if (selected) Palette.AccentSoft else Palette.Surface, shape)
            .border(Dimens.Stroke, if (selected) Palette.Accent else Palette.Line, shape)
            .padding(start = 10.dp * scale, end = 28.dp * scale, top = 10.dp * scale, bottom = 10.dp * scale),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp * scale),
    ) {
        PhotoImage(
            source = chip.cover,
            contentDescription = null,
            modifier = Modifier
                .size(76.dp * scale)
                .clip(CircleShape),
        )
        Text(
            text = chip.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = KidType.Label.copy(
                fontSize = KidType.Label.fontSize * (26f / 24f) * scale,
                color = if (selected) Palette.AccentInk else Palette.Ink,
            ),
        )
    }
}

private fun Modifier.dashedBorder(radius: Dp): Modifier = drawBehind {
    val stroke = 2.dp.toPx()
    drawRoundRect(
        color = Palette.Line2,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx()))),
    )
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun MemoryLevelScreenPreview() {
    KidTheme {
        MemoryLevelScreen(
            state = MemoryLevelUiState(
                loading = false,
                levels = buildLevelTiles(5, mapOf("memory_2x2" to 3, "memory_2x3" to 2), "memory_2x3", locksEnabled = false),
                categories = listOf(
                    CategoryChipUi(1, "Nasz dom", PhotoSource.Asset("builtin/thumbs/dom_kanapa.webp")),
                    CategoryChipUi(2, "Zwierzęta", PhotoSource.Asset("builtin/thumbs/zwierzeta_kot.webp")),
                ),
                selectedCategoryId = 1,
            ),
            onEvent = {},
            onBack = {},
        )
    }
}
