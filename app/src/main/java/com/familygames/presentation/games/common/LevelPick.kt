package com.familygames.presentation.games.common

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.R
import com.familygames.domain.game.board.SortingLevel
import com.familygames.domain.game.maze.MazeLevel
import com.familygames.domain.game.maze.TrackShape
import com.familygames.domain.game.round.CountingLevel
import com.familygames.domain.game.round.OddLevel
import com.familygames.domain.game.round.WhereIsLevel
import com.familygames.domain.game.round.isAskable
import com.familygames.domain.model.GameId
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.MemoryLevel
import com.familygames.domain.model.Photo
import com.familygames.domain.repository.GameResultRepository
import com.familygames.presentation.common.KidFrame
import com.familygames.presentation.common.RoundIconButton
import com.familygames.presentation.common.StarsRow
import com.familygames.presentation.common.VoicePrompts
import com.familygames.presentation.common.kidPressable
import com.familygames.presentation.common.softShadow
import com.familygames.presentation.games.memory.CategoryChip
import com.familygames.presentation.games.memory.CategoryChipUi
import com.familygames.presentation.games.memory.MiniGrid
import com.familygames.presentation.games.maze.MiniTrack
import com.familygames.presentation.navigation.CountingRoute
import com.familygames.presentation.navigation.MazeRoute
import com.familygames.presentation.navigation.OddOneOutRoute
import com.familygames.presentation.navigation.SortingRoute
import com.familygames.presentation.navigation.SoundMemoRoute
import com.familygames.presentation.navigation.WhereIsRoute
import com.familygames.presentation.theme.Dimens
import com.familygames.presentation.theme.KidTheme
import com.familygames.presentation.theme.KidType
import com.familygames.presentation.theme.Palette
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How a level tile describes itself (the screen turns it into text). */
enum class TileCaption { Photos, Baskets, CountTo, Cards, Named }

data class PickTile(
    val key: String,
    val rows: Int,
    val cols: Int,
    val caption: TileCaption,
    val amount: Int,
    val enabled: Boolean,
    val bestStars: Int,
    /** "Labirynt": the tile shows this track shape instead of a grid. */
    val track: TrackShape? = null,
    /** Caption text for [TileCaption.Named]. */
    @param:StringRes val name: Int? = null,
)

data class LevelPickUiState(
    val loading: Boolean = true,
    @param:StringRes val title: Int = R.string.app_name,
    val tiles: List<PickTile> = emptyList(),
    val categories: List<CategoryChipUi> = emptyList(),
    val selectedCategoryId: Long? = null,
)

sealed interface LevelPickEvent {
    data class SelectCategory(val id: Long) : LevelPickEvent
    data class Tapped(val key: String) : LevelPickEvent
}

sealed interface LevelPickEffect {
    data class Play(val route: Any) : LevelPickEffect
    data class Refuse(val key: String) : LevelPickEffect
}

private val MAZE_LEVEL_NAMES = listOf(R.string.maze_level_1, R.string.maze_level_2, R.string.maze_level_3, R.string.maze_level_4)

/** Snapshot of what the games can use: photos per visible category plus chip data. */
private data class Content(val groups: Map<Long, List<Photo>>, val chips: List<CategoryChipUi>)

/** Level choice for the round-based games (docs/12 M11, M13, M14, M17), styled like S02. */
class LevelPickViewModel(
    private val game: GameId,
    private val gamePhotos: GamePhotos,
    results: GameResultRepository,
    prompts: VoicePrompts,
) : ViewModel() {
    private val chosen = MutableStateFlow<Long?>(null)
    private val effects = Channel<LevelPickEffect>(Channel.BUFFERED)
    val effect: Flow<LevelPickEffect> = effects.receiveAsFlow()

    private val content = flow {
        val categories = gamePhotos.visibleCategories()
        val groups = gamePhotos.byCategory()
        emit(Content(groups, categories.map { c -> CategoryChipUi(c.id, c.name, groups[c.id]?.firstOrNull { it.id == c.coverPhotoId }?.thumb ?: groups[c.id]?.firstOrNull()?.thumb) }))
    }

    val uiState: StateFlow<LevelPickUiState> = combine(content, results.observeBestStars(GameKind.valueOf(game.name)), chosen, prompts.ttsAvailable) { c, best, picked, tts ->
        build(c, best, picked, tts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LevelPickUiState())

    private fun build(c: Content, best: Map<String, Int>, picked: Long?, tts: Boolean): LevelPickUiState {
        fun tile(key: String, rows: Int, cols: Int, caption: TileCaption, amount: Int, enabled: Boolean) =
            PickTile(key, rows, cols, caption, amount, enabled, best[key] ?: 0)
        return when (game) {
            GameId.WHERE_IS -> {
                val eligible = c.chips.filter { chip -> c.groups[chip.id].orEmpty().count { it.isAskable() } >= 2 }
                val selected = eligible.firstOrNull { it.id == picked } ?: eligible.firstOrNull()
                val pool = c.groups[selected?.id].orEmpty()
                LevelPickUiState(
                    loading = false,
                    title = R.string.levels_where_title,
                    tiles = WhereIsLevel.entries.map { l -> tile(l.key, 1, l.options, TileCaption.Photos, l.options, pool.size >= l.options) },
                    categories = eligible,
                    selectedCategoryId = selected?.id,
                )
            }
            GameId.SORTING -> {
                val full = c.groups.count { it.value.size >= 3 }
                LevelPickUiState(
                    loading = false,
                    title = R.string.levels_sorting_title,
                    tiles = SortingLevel.entries.map { l -> tile(l.key, 1, l.baskets, TileCaption.Baskets, l.baskets, full >= l.baskets) },
                )
            }
            GameId.ODD_ONE_OUT -> {
                val nonEmpty = c.groups.count { it.value.isNotEmpty() }
                LevelPickUiState(
                    loading = false,
                    title = R.string.levels_odd_title,
                    tiles = OddLevel.entries.map { l ->
                        tile(l.key, 1, l.options, TileCaption.Photos, l.options, nonEmpty >= 2 && c.groups.any { it.value.size >= l.options - 1 })
                    },
                )
            }
            GameId.COUNTING -> {
                val countable = c.groups.values.flatten().any { it.countable }
                LevelPickUiState(
                    loading = false,
                    title = R.string.levels_counting_title,
                    tiles = CountingLevel.entries.map { l -> tile(l.key, 1, l.max, TileCaption.CountTo, l.max, countable) },
                )
            }
            GameId.SOUND_MEMO -> {
                val withSound = c.groups.values.flatten().count { BuiltinSounds.hasSound(it, tts) }
                LevelPickUiState(
                    loading = false,
                    title = R.string.levels_sound_memo_title,
                    tiles = MemoryLevel.entries.map { l -> tile(l.key, l.rows, l.cols, TileCaption.Cards, l.cards, withSound >= l.pairs) },
                )
            }
            GameId.MAZE -> LevelPickUiState(
                loading = false,
                title = R.string.levels_maze_title,
                tiles = MazeLevel.entries.mapIndexed { i, l ->
                    PickTile(l.key, 1, 1, TileCaption.Named, i + 1, enabled = true, bestStars = best[l.key] ?: 0, track = l.shapes.last(), name = MAZE_LEVEL_NAMES[i])
                },
            )
            else -> LevelPickUiState(loading = false)
        }
    }

    fun onEvent(event: LevelPickEvent) {
        when (event) {
            is LevelPickEvent.SelectCategory -> chosen.value = event.id
            is LevelPickEvent.Tapped -> viewModelScope.launch {
                val s = uiState.value
                val tile = s.tiles.firstOrNull { it.key == event.key }
                if (tile == null || !tile.enabled) {
                    effects.send(LevelPickEffect.Refuse(event.key))
                    return@launch
                }
                val route: Any = when (game) {
                    GameId.WHERE_IS -> WhereIsRoute(tile.key, s.selectedCategoryId ?: return@launch)
                    GameId.SORTING -> SortingRoute(tile.key)
                    GameId.ODD_ONE_OUT -> OddOneOutRoute(tile.key)
                    GameId.COUNTING -> CountingRoute(tile.key)
                    GameId.SOUND_MEMO -> SoundMemoRoute(MemoryLevel.fromKey(tile.key) ?: MemoryLevel.L1)
                    GameId.MAZE -> MazeRoute(tile.key)
                    else -> return@launch
                }
                effects.send(LevelPickEffect.Play(route))
            }
        }
    }
}

/** Level choice screen for the new task games (S02 layout: level tiles, optional category chips). */
@Composable
fun LevelPickScreen(
    state: LevelPickUiState,
    onEvent: (LevelPickEvent) -> Unit,
    onBack: () -> Unit,
    shakeRequests: Map<String, Int> = emptyMap(),
) {
    KidFrame { scale ->
        RoundIconButton(
            icon = R.drawable.ic_back,
            contentDescription = stringResource(R.string.cd_back),
            onClick = onBack,
            modifier = Modifier.padding(start = Dimens.ScreenPaddingH, top = Dimens.ScreenPaddingTop),
        )
        Text(
            text = stringResource(state.title),
            style = KidType.Title.copy(fontSize = KidType.Title.fontSize * scale),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 50.dp * scale),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 170.dp * scale),
            horizontalArrangement = Arrangement.spacedBy(28.dp * scale),
        ) {
            state.tiles.forEachIndexed { i, tile ->
                PickTileView(tile, i + 1, scale, shakeRequests[tile.key] ?: 0) { onEvent(LevelPickEvent.Tapped(tile.key)) }
            }
        }
        if (!state.loading && state.tiles.none { it.enabled }) {
            Text(
                stringResource(R.string.levels_no_content),
                style = KidType.Label.copy(color = Palette.Ink2),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (state.categories.isEmpty()) 90.dp * scale else 170.dp * scale),
            )
        }
        if (state.categories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 56.dp * scale),
                contentPadding = PaddingValues(horizontal = 64.dp * scale),
                horizontalArrangement = Arrangement.spacedBy(20.dp * scale, Alignment.CenterHorizontally),
            ) {
                items(state.categories, key = { it.id }) { chip ->
                    CategoryChip(chip, selected = chip.id == state.selectedCategoryId, scale = scale) { onEvent(LevelPickEvent.SelectCategory(chip.id)) }
                }
            }
        }
    }
}

@Composable
private fun PickTileView(tile: PickTile, number: Int, scale: Float, shakeKey: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Dimens.RadiusL)
    val shake = remember { Animatable(0f) }
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
    val caption = when (tile.caption) {
        TileCaption.Photos -> pluralStringResource(R.plurals.levels_photos, tile.amount, tile.amount)
        TileCaption.Baskets -> pluralStringResource(R.plurals.levels_baskets, tile.amount, tile.amount)
        TileCaption.CountTo -> stringResource(R.string.levels_count_to, tile.amount)
        TileCaption.Cards -> pluralStringResource(R.plurals.memory_cards, tile.amount, tile.amount)
        TileCaption.Named -> stringResource(tile.name ?: R.string.app_name)
    }
    val description = stringResource(R.string.cd_level_simple, number, caption)
    Box(
        Modifier
            .width(260.dp * scale)
            .height(340.dp * scale)
            .graphicsLayer { translationX = shake.value * density }
            .softShadow(shape)
            .kidPressable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .background(Palette.Surface, shape)
            .border(Dimens.Stroke, Palette.Line, shape),
    ) {
        Box(
            Modifier.padding(start = 20.dp * scale, top = 18.dp * scale).size(46.dp * scale).background(Palette.Bg2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), style = KidType.Label.copy(fontSize = KidType.Label.fontSize * scale))
        }
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp * scale),
        ) {
            if (tile.track != null) {
                MiniTrack(tile.track, if (tile.enabled) Palette.Line else Palette.Bg2, scale)
            } else {
                MiniGrid(tile.rows, tile.cols, if (tile.enabled) Palette.Line else Palette.Bg2, scale)
            }
            Text(caption, style = KidType.Label.copy(fontSize = KidType.Label.fontSize * (22f / 24f) * scale, color = if (tile.enabled) Palette.Ink2 else Palette.Ink3))
            if (tile.enabled) {
                StarsRow(tile.bestStars, size = 36.dp * scale, gap = 6.dp * scale)
            } else {
                Icon(painterResource(R.drawable.ic_camera), null, tint = Palette.Ink3, modifier = Modifier.size(36.dp * scale))
            }
        }
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun LevelPickPreview() {
    KidTheme {
        LevelPickScreen(
            LevelPickUiState(
                loading = false,
                title = R.string.levels_where_title,
                tiles = WhereIsLevel.entries.map { PickTile(it.key, 1, it.options, TileCaption.Photos, it.options, it != WhereIsLevel.FOUR, 2) },
            ),
            onEvent = {},
            onBack = {},
        )
    }
}
