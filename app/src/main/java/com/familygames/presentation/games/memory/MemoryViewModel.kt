package com.familygames.presentation.games.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.domain.game.memory.CheckOutcome
import com.familygames.domain.game.memory.DealMemoryCardsUseCase
import com.familygames.domain.game.memory.MemoryEngine
import com.familygames.domain.game.memory.MemoryPhase
import com.familygames.domain.game.memory.MemoryScoring
import com.familygames.domain.game.memory.MemoryTimings
import com.familygames.domain.game.memory.TapOutcome
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.GameResult
import com.familygames.domain.model.MemoryLevel
import com.familygames.domain.model.Photo
import com.familygames.domain.model.PhotoSource
import com.familygames.domain.repository.GameResultRepository
import com.familygames.domain.repository.PhotoRepository
import com.familygames.domain.repository.SettingsRepository
import com.familygames.presentation.common.Sfx
import com.familygames.presentation.common.VoicePlayer
import com.familygames.presentation.common.VoicePrompts
import com.familygames.presentation.games.common.BuiltinSounds
import com.familygames.presentation.games.common.GamePhotos
import com.familygames.domain.voice.VoiceStep
import android.content.res.Resources
import com.familygames.util.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class CardHighlight { None, Match, Soft }

data class CardUi(
    val id: Long,
    val pairKey: Long,
    val image: PhotoSource,
    val label: String?,
    val faceUp: Boolean,
    val matched: Boolean,
    val highlight: CardHighlight,
    /** "Memo dźwiękowe": this card shows a speaker and plays the photo's sound (docs/12 M14). */
    val sound: Boolean = false,
)

data class MemoryUiState(
    val loading: Boolean = true,
    val notEnoughPhotos: Boolean = false,
    val rows: Int = 0,
    val cols: Int = 0,
    val cards: List<CardUi> = emptyList(),
    val pairsFound: Int = 0,
    val totalPairs: Int = 0,
    val inputLocked: Boolean = false,
    val phase: MemoryPhase = MemoryPhase.Idle,
    val stars: Int? = null,
    val showLabels: Boolean = true,
    /** Increments on every new deal, so the screen can reset per-game animation state. */
    val dealNumber: Int = 0,
)

sealed interface MemoryEvent {
    data class CardTapped(val id: Long) : MemoryEvent
    data object Restart : MemoryEvent
    data object Home : MemoryEvent
}

sealed interface MemoryEffect {
    data class Sound(val sfx: Sfx) : MemoryEffect
    data object NavigateHome : MemoryEffect
}

/** Photo ids of the last deal per category, so the next game prefers other photos (docs/04 §2). */
class MemoryHistory {
    private val lastDeal = mutableMapOf<Long, Set<Long>>()

    @Synchronized fun recentlyUsed(categoryId: Long): Set<Long> = lastDeal[categoryId].orEmpty()

    @Synchronized fun remember(categoryId: Long, photoIds: Set<Long>) {
        lastDeal[categoryId] = photoIds
    }
}

/** S03 · Memory game (docs/04). All timing lives here; the rules live in [MemoryEngine]. */
class MemoryViewModel(
    private val level: MemoryLevel,
    private val categoryId: Long,
    private val photos: PhotoRepository,
    private val results: GameResultRepository,
    private val settings: SettingsRepository,
    private val deal: DealMemoryCardsUseCase,
    private val history: MemoryHistory,
    private val clock: Clock,
    private val voice: VoicePlayer,
    private val soundMode: Boolean = false,
    private val gamePhotos: GamePhotos? = null,
    private val prompts: VoicePrompts? = null,
    private val resources: Resources? = null,
) : ViewModel() {

    private val state = MutableStateFlow(MemoryUiState(rows = level.rows, cols = level.cols))
    val uiState: StateFlow<MemoryUiState> = state.asStateFlow()

    private val effects = Channel<MemoryEffect>(Channel.BUFFERED)
    val effect: Flow<MemoryEffect> = effects.receiveAsFlow()

    private var engine: MemoryEngine? = null
    private var pool: List<Photo> = emptyList()
    private var pending: Job? = null
    private var startedAt = 0L
    private var softPair: Set<Long> = emptySet()
    private var justMatched: Long? = null
    /** Sound mode: ids of the speaker cards (the first card of every pair). */
    private var soundCards: Set<Long> = emptySet()
    private var soundJob: Job? = null

    init {
        viewModelScope.launch {
            // Snapshot of the category: changes in the parent zone do not affect a running game (docs/04 §7).
            pool = if (soundMode && gamePhotos != null && prompts != null) {
                prompts.awaitReady()
                gamePhotos.all().filter { BuiltinSounds.hasSound(it, prompts.ttsAvailable.value) }
            } else {
                photos.getForMemory(categoryId)
            }
            val showLabels = settings.settings.first().showCardLabels
            state.value = state.value.copy(showLabels = showLabels)
            newDeal()
        }
    }

    fun onEvent(event: MemoryEvent) {
        when (event) {
            is MemoryEvent.CardTapped -> onCardTapped(event.id)
            MemoryEvent.Restart -> newDeal()
            MemoryEvent.Home -> {
                pending?.cancel()
                effects.trySend(MemoryEffect.NavigateHome)
            }
        }
    }

    private fun newDeal() {
        pending?.cancel()
        if (pool.size < level.pairs) {
            state.value = state.value.copy(loading = false, notEnoughPhotos = true)
            return
        }
        val cards = deal(level, pool, history.recentlyUsed(categoryId))
        history.remember(categoryId, cards.map { it.pairKey }.toSet())
        engine = MemoryEngine(cards)
        soundCards = if (soundMode) cards.groupBy { it.pairKey }.values.map { pair -> pair.minOf { it.id } }.toSet() else emptySet()
        softPair = emptySet()
        justMatched = null
        startedAt = clock.now()
        publish(stars = null, newDeal = true)
    }

    private fun onCardTapped(id: Long) {
        val engine = engine ?: return
        val tapped = engine.state.cards.firstOrNull { it.card.id == id }
        // An open speaker card plays its sound again.
        if (tapped != null && id in soundCards && (tapped.faceUp || tapped.matched)) {
            playSound(tapped.card.photo)
            return
        }
        when (engine.tap(id)) {
            TapOutcome.Ignored -> return
            TapOutcome.FirstUp -> {
                justMatched = null
                publish()
                effects.trySend(MemoryEffect.Sound(Sfx.FLIP))
                if (id in soundCards) tapped?.let { playSound(it.card.photo) }
            }
            TapOutcome.SecondUp -> {
                publish()
                effects.trySend(MemoryEffect.Sound(Sfx.FLIP))
                if (id in soundCards) tapped?.let { playSound(it.card.photo) }
                pending = viewModelScope.launch {
                    delay(MemoryTimings.CHECK_DELAY_MS) // let the flip finish first
                    resolve(engine)
                }
            }
        }
    }

    private suspend fun resolve(engine: MemoryEngine) {
        val faceUp = engine.state.cards.filter { it.faceUp && !it.matched }
        when (val outcome = engine.check()) {
            is CheckOutcome.Match -> {
                justMatched = outcome.pairKey
                publish()
                effects.send(MemoryEffect.Sound(Sfx.MATCH))
                if (soundMode) {
                    delay(MemoryTimings.MATCH_TO_VOICE_MS)
                    soundJob?.join()
                    soundSteps(faceUp.first().card.photo).let { prompts?.play(it) }
                } else {
                    faceUp.first().card.photo.audioPath?.let { path ->
                        delay(MemoryTimings.MATCH_TO_VOICE_MS)
                        // Suspends until the recording ends, so it never overlaps the win melody (docs/10 §3).
                        voice.playAndAwait(path)
                    }
                }
                if (outcome.won) {
                    delay(MemoryTimings.WIN_DELAY_MS)
                    finish(engine)
                }
            }
            CheckOutcome.Mismatch -> {
                softPair = faceUp.map { it.card.id }.toSet()
                publish()
                effects.send(MemoryEffect.Sound(Sfx.SOFT))
                delay(MemoryTimings.mismatchFor(level))
                softPair = emptySet()
                engine.coverMismatch()
                publish()
            }
        }
    }

    private fun soundSteps(photo: Photo): List<VoiceStep> = when {
        photo.audioPath != null -> listOf(VoiceStep.Clip(photo.audioPath))
        else -> BuiltinSounds.soundFor(photo)?.let { res -> resources?.let { listOf(VoiceStep.Speech(it.getString(res))) } }.orEmpty()
    }

    private fun playSound(photo: Photo) {
        val p = prompts ?: return
        soundJob?.cancel()
        soundJob = viewModelScope.launch { p.play(soundSteps(photo)) }
    }

    private suspend fun finish(engine: MemoryEngine) {
        val s = engine.state
        val stars = MemoryScoring.stars(s.totalPairs, s.mistakes)
        // The result is stored before the overlay appears (docs/03 S04).
        results.save(
            GameResult(
                game = if (soundMode) GameKind.SOUND_MEMO else GameKind.MEMORY,
                levelKey = level.key,
                categoryId = categoryId,
                stars = stars,
                moves = s.moves,
                mistakes = s.mistakes,
                hintsUsed = 0,
                durationMs = clock.now() - startedAt,
                finishedAt = clock.now(),
            ),
        )
        publish(stars = stars)
        effects.send(MemoryEffect.Sound(Sfx.WIN))
    }

    private fun publish(stars: Int? = state.value.stars, newDeal: Boolean = false) {
        val s = engine?.state ?: return
        val current = state.value
        state.value = current.copy(
            loading = false,
            notEnoughPhotos = false,
            cards = s.cards.map { c ->
                CardUi(
                    id = c.card.id,
                    pairKey = c.card.pairKey,
                    image = c.card.photo.thumb,
                    label = c.card.photo.label,
                    faceUp = c.faceUp,
                    matched = c.matched,
                    highlight = when {
                        c.card.id in softPair -> CardHighlight.Soft
                        c.matched && c.card.pairKey == justMatched -> CardHighlight.Match
                        else -> CardHighlight.None
                    },
                    sound = c.card.id in soundCards,
                )
            },
            pairsFound = s.pairsFound,
            totalPairs = s.totalPairs,
            inputLocked = s.inputLocked,
            phase = s.phase,
            stars = stars,
            dealNumber = if (newDeal) current.dealNumber + 1 else current.dealNumber,
        )
    }
}
