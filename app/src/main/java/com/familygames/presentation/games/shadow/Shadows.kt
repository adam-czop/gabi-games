package com.familygames.presentation.games.shadow

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.R
import com.familygames.domain.game.board.DropOutcome
import com.familygames.domain.game.round.RoundScoring
import com.familygames.domain.game.shadow.ShadowBoard
import com.familygames.domain.game.shadow.ShadowDealer
import com.familygames.domain.game.shadow.ShadowLevel
import com.familygames.domain.game.shadow.ShadowRound
import com.familygames.domain.game.shadow.Silhouette
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.GameResult
import com.familygames.domain.model.Photo
import com.familygames.domain.model.PhotoSource
import com.familygames.domain.repository.GameResultRepository
import com.familygames.presentation.common.PhotoImage
import com.familygames.presentation.common.Sfx
import com.familygames.presentation.common.ThumbLoader
import com.familygames.presentation.common.VoicePrompts
import com.familygames.presentation.common.label
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
import com.familygames.presentation.theme.Dimens
import com.familygames.presentation.theme.KidTheme
import com.familygames.presentation.theme.Palette
import com.familygames.util.Clock
import com.familygames.util.DispatcherProvider
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
import kotlinx.coroutines.withContext

// MARK: Silhouettes

/** Shadow pictures of the built-in illustrations (ADR-031). */
fun interface SilhouetteLoader {
    suspend fun load(photo: Photo): ImageBitmap?
}

/** Cuts the shadow from the 400 px thumbnail off the main thread and keeps the last ones in memory. */
class ThumbSilhouetteLoader(private val thumbs: ThumbLoader, private val dispatchers: DispatcherProvider) : SilhouetteLoader {
    private val cache = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > CACHE_SIZE
    }

    override suspend fun load(photo: Photo): ImageBitmap? {
        val asset = (photo.thumb as? PhotoSource.Asset)?.path ?: return null
        synchronized(cache) { cache[asset] }?.let { return it }
        val image = thumbs.load(photo.thumb, SIZE_PX) ?: return null
        val shadow = withContext(dispatchers.default) { cut(image, asset) }
        synchronized(cache) { cache[asset] = shadow }
        return shadow
    }

    private fun cut(image: ImageBitmap, asset: String): ImageBitmap {
        val w = image.width
        val h = image.height
        val pixels = IntArray(w * h).also { image.readPixels(it) }
        val mask = Silhouette.mask(pixels, w, h, Silhouette.backgroundsFor(asset))
        val shadow = IntArray(w * h) { if (mask[it]) SHADOW else 0 }
        return Bitmap.createBitmap(shadow, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    private companion object {
        const val SIZE_PX = 400
        const val CACHE_SIZE = 40

        /** Palette.Ink2: a soft warm grey, the same family as the UI (docs/08). */
        const val SHADOW = 0xFF8B867F.toInt()
    }
}

// MARK: State

data class ShadowSlotUi(val key: Long, val shadow: ImageBitmap?, val picture: PhotoSource, val filled: Boolean)

data class ShadowsUiState(
    val loading: Boolean = true,
    val empty: Boolean = false,
    val round: Int = 0,
    val totalRounds: Int = 0,
    val slots: List<ShadowSlotUi> = emptyList(),
    val tray: List<OptionUi> = emptyList(),
    /** Slot that pulses after two wrong drops of the same picture. */
    val hint: Int? = null,
    val roundDone: Boolean = false,
    val winStars: Int? = null,
    val game: Int = 0,
)

sealed interface ShadowsEvent {
    data object Restart : ShadowsEvent
    data object Home : ShadowsEvent
}

// MARK: ViewModel

/** M21 · "Cienie" (S30): drag each picture onto its shadow; three rounds, then S04. */
class ShadowsViewModel(
    levelKey: String,
    private val gamePhotos: GamePhotos,
    private val dealer: ShadowDealer,
    private val silhouettes: SilhouetteLoader,
    private val prompts: VoicePrompts,
    private val results: GameResultRepository,
    private val clock: Clock,
) : ViewModel() {
    private val level = ShadowLevel.fromKey(levelKey)
    private val state = MutableStateFlow(ShadowsUiState())
    val uiState: StateFlow<ShadowsUiState> = state.asStateFlow()
    private val effects = Channel<GameEffect>(Channel.BUFFERED)
    val effect: Flow<GameEffect> = effects.receiveAsFlow()

    private var rounds: List<ShadowRound> = emptyList()
    private var shadows: Map<Long, ImageBitmap?> = emptyMap()
    private var board: ShadowBoard? = null
    private val wrongDrops = mutableMapOf<Long, Int>()
    private var mistakes = 0
    private var startedAt = 0L
    private var pending: Job? = null
    private var talking: Job? = null

    init {
        viewModelScope.launch { newGame() }
    }

    fun onEvent(event: ShadowsEvent) {
        when (event) {
            ShadowsEvent.Restart -> viewModelScope.launch { newGame() }
            ShadowsEvent.Home -> {
                pending?.cancel()
                talking?.cancel()
                effects.trySend(GameEffect.Home)
            }
        }
    }

    private suspend fun newGame() {
        pending?.cancel()
        talking?.cancel()
        rounds = dealer.deal(gamePhotos.byCategory(), level)
        if (rounds.isEmpty()) {
            state.value = ShadowsUiState(loading = false, empty = true)
            return
        }
        shadows = rounds.flatMap { it.shadows }.distinctBy { it.id }.associate { it.id to silhouettes.load(it) }
        prompts.awaitReady()
        mistakes = 0
        startedAt = clock.now()
        state.update { ShadowsUiState(loading = false, totalRounds = rounds.size, game = it.game + 1) }
        showRound(0)
    }

    private fun showRound(index: Int) {
        val round = rounds[index]
        board = ShadowBoard(round.shadows)
        wrongDrops.clear()
        state.update {
            it.copy(
                round = index,
                slots = round.shadows.map { p -> ShadowSlotUi(p.id, shadows[p.id], p.thumb, filled = false) },
                tray = round.tray.map { p -> OptionUi(p.id, p.thumb, p.label) },
                hint = null,
                roundDone = false,
            )
        }
    }

    /** Returns true when the picture stays on the shadow. */
    fun drop(photoId: Long, slot: Int?): Boolean {
        val b = board ?: return false
        if (slot == null || state.value.roundDone || state.value.winStars != null) return false
        return when (val outcome = b.drop(photoId, slot)) {
            DropOutcome.Ignored -> false
            DropOutcome.Wrong -> {
                mistakes++
                val wrong = (wrongDrops[photoId] ?: 0) + 1
                wrongDrops[photoId] = wrong
                effects.trySend(GameEffect.Sound(Sfx.SOFT))
                if (wrong >= 2) state.update { it.copy(hint = b.slotOf(photoId)) }
                false
            }
            is DropOutcome.Correct -> {
                effects.trySend(GameEffect.Sound(Sfx.MATCH))
                val photo = rounds[state.value.round].shadows.first { it.id == photoId }
                state.update { s ->
                    s.copy(
                        slots = s.slots.map { if (it.key == photoId) it.copy(filled = true) else it },
                        tray = s.tray.filterNot { it.key == photoId },
                        hint = if (s.hint == slot) null else s.hint,
                        roundDone = outcome.won,
                    )
                }
                talking?.cancel()
                talking = viewModelScope.launch { prompts.play(prompts.label(photo)) }
                if (outcome.won) {
                    pending = viewModelScope.launch {
                        delay(ROUND_PAUSE_MS)
                        talking?.join()
                        val next = state.value.round + 1
                        if (next < rounds.size) showRound(next) else finish()
                    }
                }
                true
            }
        }
    }

    private suspend fun finish() {
        val total = rounds.sumOf { it.shadows.size }
        val stars = RoundScoring.stars(total, mistakes)
        results.save(
            GameResult(
                game = GameKind.SHADOWS, levelKey = level.key, stars = stars, moves = total, mistakes = mistakes,
                hintsUsed = 0, durationMs = clock.now() - startedAt, finishedAt = clock.now(),
            ),
        )
        state.update { it.copy(round = rounds.size, winStars = stars) }
        effects.send(GameEffect.Sound(Sfx.WIN))
    }

    private companion object {
        const val ROUND_PAUSE_MS = 1_400L
    }
}

// MARK: Screen

/** S30 · shadows in a row on top, the pictures to drag below. */
@Composable
fun ShadowsScreen(state: ShadowsUiState, onEvent: (ShadowsEvent) -> Unit, onDrop: (Long, Int?) -> Boolean, onStarShown: (Int) -> Unit = {}) {
    val zones = rememberDropZones()
    TaskGameScaffold(
        onHome = { onEvent(ShadowsEvent.Home) },
        onRestart = { onEvent(ShadowsEvent.Restart) },
        progress = if (state.totalRounds > 0) state.round + (if (state.roundDone) 1 else 0) to state.totalRounds else null,
        winStars = state.winStars,
        onStarShown = onStarShown,
    ) { scale ->
        if (state.empty) {
            EmptyGameNote()
            return@TaskGameScaffold
        }
        if (state.slots.isEmpty()) return@TaskGameScaffold
        val (slotSize, cardSize) = sizesFor(state.slots.size)
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 124.dp * scale),
            horizontalArrangement = Arrangement.spacedBy(36.dp * scale),
        ) {
            state.slots.forEachIndexed { i, slot ->
                ShadowSlot(slot, i, slotSize * scale, pulse = state.hint == i, modifier = Modifier.dropZone(zones, i))
            }
        }
        // The tray is composed last so a dragged picture is drawn above the shadows.
        key(state.game, state.round) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp * scale),
                horizontalArrangement = Arrangement.spacedBy(36.dp * scale),
            ) {
                state.tray.forEach { item ->
                    key(item.key) {
                        DraggableItem(zones = zones, modifier = Modifier.size(cardSize * scale), onDrop = { zone -> onDrop(item.key, zone) }) {
                            PhotoCard(item.image, Modifier.matchParentSize(), radius = Dimens.RadiusM, padding = 8.dp * scale)
                        }
                    }
                }
            }
        }
    }
}

private fun sizesFor(count: Int): Pair<Dp, Dp> = when (count) {
    2 -> 260.dp to 200.dp
    3 -> 240.dp to 184.dp
    else -> 210.dp to 168.dp
}

@Composable
private fun ShadowSlot(slot: ShadowSlotUi, index: Int, size: Dp, pulse: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(Dimens.RadiusL)
    val description = stringResource(if (slot.filled) R.string.cd_shadow_filled else R.string.cd_shadow, index + 1)
    Box(
        modifier
            .size(size)
            .pulse(pulse)
            .semantics { contentDescription = description }
            .softShadow(shape)
            .background(Palette.Surface, shape)
            .border(if (slot.filled || pulse) Dimens.StrokeSelected else Dimens.Stroke, if (slot.filled || pulse) Palette.Accent else Palette.Line, shape)
            .padding(14.dp)
            .clip(RoundedCornerShape(Dimens.RadiusM)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            slot.filled -> PhotoImage(slot.picture, null, Modifier.fillMaxSize())
            slot.shadow != null -> Image(slot.shadow, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            // Without a cut shadow the picture shows faintly, so the round can still be played.
            else -> PhotoImage(slot.picture, null, Modifier.fillMaxSize().alpha(0.25f))
        }
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun ShadowsPreview() {
    val names = listOf("zwierzeta_kot", "pojazdy_traktor", "zabawki_kaczuszka")
    KidTheme {
        ShadowsScreen(
            ShadowsUiState(
                loading = false, totalRounds = 3, round = 1,
                slots = names.mapIndexed { i, n -> ShadowSlotUi(i.toLong(), null, PhotoSource.Asset("builtin/thumbs/$n.webp"), filled = i == 1) },
                tray = listOf(OptionUi(2, PhotoSource.Asset("builtin/thumbs/${names[2]}.webp")), OptionUi(0, PhotoSource.Asset("builtin/thumbs/${names[0]}.webp"))),
            ),
            onEvent = {},
            onDrop = { _, _ -> false },
        )
    }
}
