package com.familygames.presentation.games.memory

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import com.familygames.presentation.common.ConfettiCanvas
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.familygames.R
import com.familygames.domain.game.memory.MemoryPhase
import com.familygames.domain.game.memory.MemoryTimings
import com.familygames.domain.model.PhotoSource
import com.familygames.presentation.common.KidFrame
import com.familygames.presentation.common.PhotoImage
import com.familygames.presentation.common.RoundIconButton
import com.familygames.presentation.common.WinOverlay
import com.familygames.presentation.common.kidPressable
import com.familygames.presentation.common.softShadow
import com.familygames.presentation.theme.Dimens
import com.familygames.presentation.theme.KidTheme
import com.familygames.presentation.theme.KidType
import com.familygames.presentation.theme.Palette

/** Card proportions from the mockup (232 × 270 dp). */
private const val CARD_RATIO = 232f / 270f

/** S03 · Memorki. */
@Composable
fun MemoryScreen(
    state: MemoryUiState,
    onEvent: (MemoryEvent) -> Unit,
    onStarShown: (Int) -> Unit = {},
) {
    BackHandler { onEvent(MemoryEvent.Home) } // back in a game = back to the menu (docs/01 §4)
    val winStars = state.stars.takeIf { state.phase == MemoryPhase.Won }
    val won = winStars != null

    KidFrame { scale ->
        Box(Modifier.fillMaxSize().then(if (won) Modifier.blur(6.dp) else Modifier)) {
            RoundIconButton(
                icon = R.drawable.ic_home,
                contentDescription = stringResource(R.string.cd_home),
                onClick = { onEvent(MemoryEvent.Home) },
                modifier = Modifier.padding(start = Dimens.ScreenPaddingH, top = Dimens.ScreenPaddingTop),
            )
            RoundIconButton(
                icon = R.drawable.ic_restart,
                contentDescription = stringResource(R.string.cd_restart),
                onClick = { onEvent(MemoryEvent.Restart) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = Dimens.ScreenPaddingH, top = Dimens.ScreenPaddingTop),
            )
            PairsCounter(
                found = state.pairsFound,
                total = state.totalPairs,
                scale = scale,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 66.dp * scale),
            )
            if (state.notEnoughPhotos) {
                Text(
                    text = stringResource(R.string.memory_not_enough_photos),
                    style = KidType.Label,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else if (state.cards.isNotEmpty()) {
                CardGrid(
                    state = state,
                    scale = scale,
                    onTap = { onEvent(MemoryEvent.CardTapped(it)) },
                    modifier = Modifier.padding(start = 64.dp * scale, end = 64.dp * scale, top = 160.dp * scale, bottom = 40.dp * scale),
                )
            }
        }
        if (winStars != null) {
            WinOverlay(
                stars = winStars,
                onPlayAgain = { onEvent(MemoryEvent.Restart) },
                onMenu = { onEvent(MemoryEvent.Home) },
                scale = scale,
                onStarShown = onStarShown,
            )
        }
    }
}

@Composable
private fun PairsCounter(found: Int, total: Int, scale: Float, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.cd_pairs, found, total)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(14.dp * scale),
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(20.dp * scale)
                    .background(if (i < found) Palette.Accent else Palette.Line, CircleShape),
            )
        }
    }
}

/** Fixed grid sized from the available space (ADR-005): no scrolling, always centred. */
@Composable
private fun CardGrid(state: MemoryUiState, scale: Float, onTap: (Long) -> Unit, modifier: Modifier = Modifier) {
    val centers = remember { mutableStateMapOf<Long, Offset>() }
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { gridOrigin = it.positionInRoot() },
        contentAlignment = Alignment.Center,
    ) {
        val gap = Dimens.GridGapMemory * scale
        val byWidth = (maxWidth - gap * (state.cols - 1)) / state.cols
        val byHeight = (maxHeight - gap * (state.rows - 1)) / state.rows * CARD_RATIO
        val cardWidth = min(min(byWidth, byHeight), 232.dp * 1.25f)
        val cardHeight = cardWidth / CARD_RATIO
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            state.cards.chunked(state.cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { card ->
                        // key = deal + card: a new deal starts every card face down without a reverse flip
                        androidx.compose.runtime.key(state.dealNumber, card.id) {
                            MemoryCardView(
                                card = card,
                                width = cardWidth,
                                height = cardHeight,
                                showLabel = state.showLabels,
                                enabled = (!state.inputLocked && !card.faceUp && !card.matched) || (card.sound && (card.faceUp || card.matched)),
                                onTap = { onTap(card.id) },
                                modifier = Modifier.onGloballyPositioned { c ->
                                    centers[card.id] = c.positionInRoot() + Offset(c.size.width / 2f, c.size.height / 2f)
                                },
                            )
                        }
                    }
                }
            }
        }
        // Small burst from both cards of a pair that has just been found (docs/04 §5).
        val matched = state.cards.filter { it.highlight == CardHighlight.Match }
        if (matched.isNotEmpty()) {
            ConfettiCanvas(
                key = "${state.dealNumber}-${matched.first().pairKey}",
                modifier = Modifier.fillMaxSize(),
                particleCount = 12,
                durationMs = 900,
                origins = matched.mapNotNull { centers[it.id]?.minus(gridOrigin) },
                density = LocalDensity.current.density,
            )
        }
    }
}

@Composable
private fun MemoryCardView(
    card: CardUi,
    width: Dp,
    height: Dp,
    showLabel: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (card.faceUp || card.matched) 180f else 0f,
        animationSpec = tween(MemoryTimings.FLIP_MS, easing = FastOutSlowInEasing),
        label = "flip",
    )
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(card.highlight == CardHighlight.Match) {
        if (card.highlight == CardHighlight.Match) {
            pulse.animateTo(1.04f, spring(dampingRatio = 0.6f, stiffness = 600f))
            pulse.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 400f))
        }
    }
    val sizeFactor = (width / 232.dp).coerceIn(0.6f, 1.2f)
    val shape = RoundedCornerShape(Dimens.RadiusL * sizeFactor.coerceAtMost(1f))
    val description = if (card.sound && (card.faceUp || card.matched)) {
        stringResource(R.string.cd_sound_card)
    } else if (card.faceUp || card.matched) {
        card.label?.let { stringResource(R.string.cd_card_photo, it) } ?: stringResource(R.string.cd_card_open)
    } else {
        stringResource(R.string.cd_card_hidden)
    }

    Box(
        modifier = modifier
            .size(width, height)
            .graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
            }
            .semantics { contentDescription = description }
            .kidPressable(enabled = enabled, onClick = onTap),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                },
        ) {
            if (rotation <= 90f) {
                CardBack(shape, sizeFactor)
            } else {
                CardFront(card, shape, showLabel, sizeFactor, Modifier.graphicsLayer { rotationY = 180f })
            }
        }
        if (card.matched && rotation > 90f) {
            MatchBadge(
                sizeFactor,
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 16.dp * sizeFactor, y = (-16).dp * sizeFactor),
            )
        }
    }
}

/** Back: light neutral dot pattern with an outlined star (mockup `.card .back`). */
@Composable
private fun CardBack(shape: RoundedCornerShape, sizeFactor: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .softShadow(shape)
            .clip(shape)
            .background(Palette.Bg2)
            .border(Dimens.Stroke, Palette.Line, shape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 22.dp.toPx()
            val radius = 1.6.dp.toPx()
            var y = step / 2
            while (y < size.height) {
                var x = step / 2
                while (x < size.width) {
                    drawCircle(Palette.CardDot, radius, Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        Box(
            modifier = Modifier
                .size(108.dp * sizeFactor)
                .background(Palette.Bg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_star), null, tint = Palette.Ink3, modifier = Modifier.size(64.dp * sizeFactor))
        }
    }
}

@Composable
private fun CardFront(card: CardUi, shape: RoundedCornerShape, showLabel: Boolean, sizeFactor: Float, modifier: Modifier) {
    val ring: Color? = when {
        card.highlight == CardHighlight.Soft -> Palette.Honey
        card.matched -> Palette.Accent
        else -> null
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .softShadow(shape)
            .background(Palette.Surface, shape)
            .then(if (ring != null) Modifier.border(4.dp, ring, shape) else Modifier.border(Dimens.Stroke, Palette.Line, shape))
            .padding(12.dp * sizeFactor),
        verticalArrangement = Arrangement.spacedBy(10.dp * sizeFactor),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (card.sound) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Box(Modifier.size(132.dp * sizeFactor).background(Palette.AccentSoft, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_volume_on), null, tint = Palette.AccentInk, modifier = Modifier.size(80.dp * sizeFactor))
                }
            }
            return@Column
        }
        PhotoImage(
            source = card.image,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp * sizeFactor)),
        )
        if (showLabel && !card.label.isNullOrBlank()) {
            Text(
                text = card.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = KidType.Label.copy(fontSize = KidType.Label.fontSize * (22f / 24f) * sizeFactor),
            )
        }
    }
}

@Composable
private fun MatchBadge(sizeFactor: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp * sizeFactor)
            .background(Palette.Bg, CircleShape)
            .padding(4.dp * sizeFactor)
            .background(Palette.Accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_check), null, tint = Color.White, modifier = Modifier.size(26.dp * sizeFactor))
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun MemoryScreenPreview() {
    val photos = listOf("zwierzeta_kot", "dom_kanapa", "zwierzeta_pies", "zabawki_mis")
    val cards = (photos + photos).mapIndexed { i, name ->
        CardUi(
            id = i.toLong(),
            pairKey = photos.indexOf(name).toLong(),
            image = PhotoSource.Asset("builtin/thumbs/$name.webp"),
            label = name.substringAfter('_'),
            faceUp = i % 3 == 0,
            matched = i == 0 || i == 4,
            highlight = CardHighlight.None,
        )
    }
    KidTheme {
        MemoryScreen(
            state = MemoryUiState(loading = false, rows = 2, cols = 4, cards = cards, pairsFound = 1, totalPairs = 4),
            onEvent = {},
        )
    }
}
