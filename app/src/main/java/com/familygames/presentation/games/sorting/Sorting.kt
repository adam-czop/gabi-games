package com.familygames.presentation.games.sorting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.R
import com.familygames.domain.game.board.DropOutcome
import com.familygames.domain.game.board.SortingBoard
import com.familygames.domain.game.board.SortingLevel
import com.familygames.domain.game.round.RoundScoring
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.GameResult
import com.familygames.domain.model.PhotoSource
import com.familygames.domain.repository.GameResultRepository
import com.familygames.presentation.common.PhotoImage
import com.familygames.presentation.common.Sfx
import com.familygames.presentation.common.softShadow
import com.familygames.presentation.games.common.DraggableItem
import com.familygames.presentation.games.common.EmptyGameNote
import com.familygames.presentation.games.common.GameEffect
import com.familygames.presentation.games.common.GamePhotos
import com.familygames.presentation.games.common.OptionUi
import com.familygames.presentation.games.common.PhotoCard
import com.familygames.presentation.games.common.TaskGameScaffold
import com.familygames.presentation.games.common.dropZone
import com.familygames.presentation.games.common.pulse
import com.familygames.presentation.games.common.rememberDropZones
import com.familygames.presentation.theme.KidTheme
import com.familygames.presentation.theme.KidType
import com.familygames.presentation.theme.Palette
import com.familygames.util.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class BasketUi(val id: Long, val name: String, val cover: PhotoSource?, val filled: Int, val capacity: Int)

data class SortingUiState(
    val loading: Boolean = true,
    val empty: Boolean = false,
    val baskets: List<BasketUi> = emptyList(),
    val tray: List<OptionUi> = emptyList(),
    /** Basket index that pulses after a wrong drop, showing where the photo belongs. */
    val pulseBasket: Int? = null,
    val done: Int = 0,
    val total: Int = 0,
    val winStars: Int? = null,
    val game: Int = 0,
)

sealed interface SortingEvent {
    data object Restart : SortingEvent
    data object Home : SortingEvent
}

/** M13a · "Do koszyczków" (S21). */
class SortingViewModel(
    levelKey: String,
    private val gamePhotos: GamePhotos,
    private val results: GameResultRepository,
    private val clock: Clock,
    private val random: Random,
) : ViewModel() {
    private val level = SortingLevel.fromKey(levelKey)
    private val state = MutableStateFlow(SortingUiState())
    val uiState: StateFlow<SortingUiState> = state.asStateFlow()
    private val effects = Channel<GameEffect>(Channel.BUFFERED)
    val effect: Flow<GameEffect> = effects.receiveAsFlow()

    private var board: SortingBoard? = null
    private var baskets: List<BasketUi> = emptyList()
    private var startedAt = 0L
    private var pulse: Job? = null

    init {
        viewModelScope.launch { deal() }
    }

    private suspend fun deal() {
        val groups = gamePhotos.byCategory()
        val categories = gamePhotos.visibleCategories().associateBy { it.id }
        val b = SortingBoard.deal(groups, level, random)
        if (b == null) {
            state.value = SortingUiState(loading = false, empty = true)
            return
        }
        board = b
        baskets = b.baskets.map { id ->
            val c = categories.getValue(id)
            val photos = groups[id].orEmpty()
            BasketUi(id, c.name, (photos.firstOrNull { it.id == c.coverPhotoId } ?: photos.first()).thumb, 0, level.perBasket)
        }
        startedAt = clock.now()
        state.update { SortingUiState(loading = false, total = b.total, game = it.game + 1) }
        publish()
    }

    private fun publish() {
        val b = board ?: return
        state.update { s ->
            s.copy(
                baskets = baskets.map { it.copy(filled = b.sorted[it.id] ?: 0) },
                tray = b.trayItems.map { OptionUi(it.photo.id, it.photo.thumb, it.photo.label) },
                done = b.total - b.trayItems.size,
            )
        }
    }

    /** Returns true when the photo went into the right basket. */
    fun drop(photoId: Long, basketIndex: Int?): Boolean {
        val b = board ?: return false
        if (basketIndex == null || state.value.winStars != null) return false
        val basket = baskets.getOrNull(basketIndex)?.id ?: return false
        val right = b.trayItems.firstOrNull { it.photo.id == photoId }?.basket
        return when (val outcome = b.drop(photoId, basket)) {
            DropOutcome.Ignored -> false
            DropOutcome.Wrong -> {
                effects.trySend(GameEffect.Sound(Sfx.SOFT))
                pulse?.cancel()
                pulse = viewModelScope.launch {
                    state.update { it.copy(pulseBasket = baskets.indexOfFirst { bk -> bk.id == right }) }
                    delay(1_000)
                    state.update { it.copy(pulseBasket = null) }
                }
                false
            }
            is DropOutcome.Correct -> {
                effects.trySend(GameEffect.Sound(Sfx.MATCH))
                publish()
                if (outcome.won) viewModelScope.launch { finish(b) }
                true
            }
        }
    }

    private suspend fun finish(b: SortingBoard) {
        delay(600)
        val stars = RoundScoring.stars(b.total, b.mistakes)
        results.save(
            GameResult(
                game = GameKind.SORTING, levelKey = level.key, stars = stars, moves = b.total, mistakes = b.mistakes,
                hintsUsed = 0, durationMs = clock.now() - startedAt, finishedAt = clock.now(),
            ),
        )
        state.update { it.copy(winStars = stars) }
        effects.send(GameEffect.Sound(Sfx.WIN))
    }

    fun onEvent(event: SortingEvent) {
        when (event) {
            SortingEvent.Restart -> viewModelScope.launch { deal() }
            SortingEvent.Home -> effects.trySend(GameEffect.Home)
        }
    }
}

/** S21 · tray of photos on top, baskets with category covers below. */
@Composable
fun SortingScreen(state: SortingUiState, onEvent: (SortingEvent) -> Unit, onDrop: (Long, Int?) -> Boolean, onStarShown: (Int) -> Unit = {}) {
    val zones = rememberDropZones()
    TaskGameScaffold(
        onHome = { onEvent(SortingEvent.Home) },
        onRestart = { onEvent(SortingEvent.Restart) },
        progress = null,
        winStars = state.winStars,
        onStarShown = onStarShown,
    ) { scale ->
        if (state.empty) {
            EmptyGameNote()
            return@TaskGameScaffold
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp * scale),
            horizontalArrangement = Arrangement.spacedBy(40.dp * scale),
        ) {
            state.baskets.forEachIndexed { i, basket -> Basket(basket, scale, state.pulseBasket == i, Modifier.dropZone(zones, i)) }
        }
        // The tray is composed last so a dragged photo is drawn above the baskets.
        val tile = if (state.total > 6) 112.dp else 128.dp
        val cols = if (state.total > 6) 5 else 6
        val gap = 22.dp
        Box(Modifier.align(Alignment.TopCenter).padding(top = 140.dp * scale)) {
            androidx.compose.runtime.key(state.game) {
                state.tray.forEachIndexed { i, item ->
                    androidx.compose.runtime.key(item.key) {
                        val row = i / cols
                        val col = i % cols
                        val inRow = minOf(cols, state.tray.size - row * cols)
                        val shift = (tile + gap) * (cols - inRow) / 2
                        DraggableItem(
                            zones = zones,
                            modifier = Modifier
                                .offset(x = (shift + (tile + gap) * col) * scale, y = (tile + gap) * row * scale)
                                .size(tile * scale),
                            onDrop = { zone -> onDrop(item.key, zone) },
                        ) {
                            PhotoCard(item.image, Modifier.matchParentSize(), radius = 18.dp, padding = 6.dp * scale)
                        }
                    }
                }
            }
            Box(Modifier.size(((tile + gap) * cols - gap) * scale, 1.dp))
        }
    }
}

@Composable
private fun Basket(basket: BasketUi, scale: Float, pulse: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(28.dp)
    val description = stringResource(R.string.cd_basket, basket.name)
    Column(
        modifier
            .size(280.dp * scale, 200.dp * scale)
            .pulse(pulse)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .softShadow(shape)
            .background(Palette.Surface, shape)
            .border(if (pulse) 4.dp else 1.dp, if (pulse) Palette.Accent else Palette.Line, shape)
            .padding(16.dp * scale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp * scale, Alignment.CenterVertically),
    ) {
        PhotoImage(basket.cover, null, Modifier.size(96.dp * scale).clip(CircleShape))
        Text(basket.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = KidType.Label.copy(fontSize = KidType.Label.fontSize * scale))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
            repeat(basket.capacity) { i -> Box(Modifier.size(14.dp * scale).background(if (i < basket.filled) Palette.Accent else Palette.Line, CircleShape)) }
        }
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun SortingPreview() {
    val names = listOf("zwierzeta_kot", "zabawki_pilka", "zwierzeta_pies", "zabawki_mis", "zwierzeta_krowa", "zabawki_lalka")
    KidTheme {
        SortingScreen(
            SortingUiState(
                loading = false, total = 6,
                baskets = listOf(
                    BasketUi(1, "Zwierzęta", PhotoSource.Asset("builtin/thumbs/zwierzeta_kot.webp"), 1, 3),
                    BasketUi(2, "Zabawki", PhotoSource.Asset("builtin/thumbs/zabawki_mis.webp"), 0, 3),
                ),
                tray = names.mapIndexed { i, n -> OptionUi(i.toLong(), PhotoSource.Asset("builtin/thumbs/$n.webp")) },
            ),
            onEvent = {},
            onDrop = { _, _ -> false },
        )
    }
}
