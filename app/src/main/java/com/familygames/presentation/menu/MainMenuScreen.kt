package com.familygames.presentation.menu

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.familygames.R
import com.familygames.presentation.common.HoldToActivateButton
import com.familygames.presentation.common.RoundIconButton
import com.familygames.presentation.common.firstPointerOnly
import com.familygames.presentation.common.kidPressable
import com.familygames.presentation.common.softShadow
import com.familygames.presentation.games.GameEntry
import com.familygames.presentation.games.GameRegistry
import com.familygames.presentation.theme.Dimens
import com.familygames.presentation.theme.KidShapes
import com.familygames.presentation.theme.KidTheme
import com.familygames.presentation.theme.KidType
import com.familygames.presentation.theme.Palette
import kotlinx.coroutines.launch

// Mockup frame is 1280×800 dp (docs/03); everything is laid out on that frame and scaled down on smaller tablets.
private val FrameWidth = 1280.dp
private val FrameHeight = 800.dp
private val TileWidth = 330.dp
private val TileHeight = 380.dp
private val TileGap = 40.dp
private val IconCircle = 176.dp
private val IconSize = 104.dp
private val GridTileWidth = 300.dp
private val GridTileHeight = 228.dp
private val GridGap = 32.dp
private const val GRID_PAGE = 6

/** S01 · Main menu. */
@Composable
fun MainMenuScreen(
    games: List<GameEntry>,
    showComingSoon: Boolean,
    soundEnabled: Boolean,
    onGameClick: (GameEntry) -> Unit,
    onToggleSound: () -> Unit,
    onParentGate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // S01 is the root of the child zone: system back does nothing here (docs/01 §4).
    BackHandler(enabled = true) {}

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .firstPointerOnly()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val scale = minOf(1f, maxWidth / FrameWidth, maxHeight / FrameHeight)
        fun Dp.s(): Dp = this * scale

        HoldToActivateButton(
            icon = R.drawable.ic_lock,
            contentDescription = stringResource(R.string.cd_parent_lock),
            onActivated = onParentGate,
            modifier = Modifier.padding(start = Dimens.ScreenPaddingH, top = Dimens.ScreenPaddingTop),
        )
        RoundIconButton(
            icon = if (soundEnabled) R.drawable.ic_volume_on else R.drawable.ic_volume_off,
            contentDescription = stringResource(if (soundEnabled) R.string.cd_sound_on else R.string.cd_sound_off),
            onClick = onToggleSound,
            size = Dimens.TouchSecondary,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = Dimens.ScreenPaddingH, top = Dimens.ScreenPaddingTop),
        )

        if (games.size <= 3) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 128.dp.s()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Logo(scale)
                Spacer(Modifier.height(69.dp.s()))
                Row(horizontalArrangement = Arrangement.spacedBy(TileGap.s())) {
                    games.forEachIndexed { index, game ->
                        GameTile(game, index, scale, onClick = { onGameClick(game) })
                    }
                    if (showComingSoon) ComingSoonTile(scale)
                }
            }
        } else {
            // S16: 3×2 grid; more than six games are split into pages swiped sideways, never a scrolling list.
            val pages = games.chunked(GRID_PAGE)
            val pagerState = rememberPagerState { pages.size }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 88.dp.s()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Logo(scale)
                Spacer(Modifier.height(56.dp.s()))
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.Top) { page ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(GridGap.s()),
                    ) {
                        pages[page].chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(GridGap.s())) {
                                row.forEach { game ->
                                    GridTile(game, games.indexOf(game), scale, onClick = { onGameClick(game) })
                                }
                            }
                        }
                    }
                }
                if (pages.size > 1) {
                    val description = stringResource(R.string.cd_menu_page, pagerState.currentPage + 1, pages.size)
                    Row(
                        modifier = Modifier
                            .padding(bottom = 22.dp.s(), top = 8.dp.s())
                            .semantics { contentDescription = description },
                        horizontalArrangement = Arrangement.spacedBy(14.dp.s()),
                    ) {
                        repeat(pages.size) { i ->
                            Box(
                                Modifier
                                    .size(20.dp.s())
                                    .background(if (i == pagerState.currentPage) Palette.Accent else Palette.Line, CircleShape),
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(40.dp.s()))
                }
            }
        }
    }
}

@Composable
private fun Logo(scale: Float) {
    val family = stringResource(R.string.logo_family)
    val games = stringResource(R.string.logo_games)
    Text(
        text = buildAnnotatedString {
            append(family)
            append(' ')
            withStyle(SpanStyle(color = Palette.AccentInk)) { append(games) }
        },
        style = KidType.Display.copy(fontSize = KidType.Display.fontSize * scale),
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun GameTile(game: GameEntry, index: Int, scale: Float, onClick: () -> Unit) {
    val (circle, stroke) = GameRegistry.circleColors(index)
    TileFrame(scale, Modifier.kidPressable(onClick = onClick).softShadow(KidShapes.XL)) {
        IconCircle(game.icon, circle, stroke, scale)
        Spacer(Modifier.height(26.dp * scale))
        Text(stringResource(game.title), style = KidType.Tile.copy(fontSize = KidType.Tile.fontSize * scale))
    }
}

/** S16 grid tile: 300×228 dp, icon in a 118 dp circle, name 30 sp. */
@Composable
private fun GridTile(game: GameEntry, index: Int, scale: Float, onClick: () -> Unit) {
    val (circle, stroke) = GameRegistry.circleColors(index)
    Column(
        modifier = Modifier
            .width(GridTileWidth * scale)
            .height(GridTileHeight * scale)
            .kidPressable(onClick = onClick)
            .softShadow(KidShapes.XL)
            .background(Palette.Surface, KidShapes.XL)
            .border(Dimens.Stroke, Palette.Line, KidShapes.XL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(118.dp * scale).background(circle, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(game.icon), contentDescription = null, tint = stroke, modifier = Modifier.size(64.dp * scale))
        }
        Spacer(Modifier.height(18.dp * scale))
        Text(stringResource(game.title), maxLines = 1, style = KidType.Tile.copy(fontSize = 30.sp * scale))
    }
}

/** Inactive placeholder for future games: only a gentle shake on tap (docs/03 S01, Q11). */
@Composable
private fun ComingSoonTile(scale: Float) {
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dash = 2.dp
    TileFrame(
        scale = scale,
        background = Color.Transparent,
        modifier = Modifier
            .graphicsLayer { translationX = shake.value * density }
            .kidPressable {
                scope.launch {
                    shake.animateTo(0f, keyframes {
                        durationMillis = 360
                        -8f at 60
                        8f at 140
                        -5f at 220
                        3f at 290
                    })
                }
            }
            .drawBehind {
                val stroke = dash.toPx()
                drawRoundRect(
                    color = Palette.Line2,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(Dimens.RadiusXL.toPx()),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx()))),
                )
            },
        bordered = false,
    ) {
        IconCircle(R.drawable.ic_game_soon, Palette.Bg2, Palette.Ink3, scale)
        Spacer(Modifier.height(26.dp * scale))
        Text(
            stringResource(R.string.game_soon),
            style = KidType.Tile.copy(color = Palette.Ink3, fontSize = 30.sp * scale),
        )
    }
}

@Composable
private fun TileFrame(
    scale: Float,
    modifier: Modifier = Modifier,
    background: Color = Palette.Surface,
    bordered: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .width(TileWidth * scale)
            .height(TileHeight * scale)
            .background(background, KidShapes.XL)
            .then(if (bordered) Modifier.border(Dimens.Stroke, Palette.Line, KidShapes.XL) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun IconCircle(@DrawableRes icon: Int, circle: Color, stroke: Color, scale: Float) {
    Box(
        modifier = Modifier
            .size(IconCircle * scale)
            .background(circle, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = stroke, modifier = Modifier.size(min(IconSize * scale, IconSize)))
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun MainMenuGridPreview() {
    KidTheme {
        MainMenuScreen(games = GameRegistry.games, showComingSoon = false, soundEnabled = true, onGameClick = {}, onToggleSound = {}, onParentGate = {})
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun MainMenuScreenPreview() {
    KidTheme {
        MainMenuScreen(
            games = GameRegistry.games.take(2),
            showComingSoon = true,
            soundEnabled = true,
            onGameClick = {},
            onToggleSound = {},
            onParentGate = {},
        )
    }
}
