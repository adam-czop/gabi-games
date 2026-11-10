package com.familygames.presentation.games.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.domain.game.round.AnswerOutcome
import com.familygames.domain.game.round.Round
import com.familygames.domain.game.round.RoundEngine
import com.familygames.domain.game.round.RoundPhase
import com.familygames.domain.game.round.RoundScoring
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.GameResult
import com.familygames.domain.model.PhotoSource
import com.familygames.domain.repository.GameResultRepository
import com.familygames.presentation.common.Sfx
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

/** One answer on screen: a photo card or a number button. */
data class OptionUi(val key: Long, val image: PhotoSource? = null, val label: String? = null, val number: Int? = null)

data class RoundUiState(
    val loading: Boolean = true,
    val empty: Boolean = false,
    val round: Int = 0,
    val totalRounds: Int = 0,
    val options: List<OptionUi> = emptyList(),
    /** Index of the right answer once it was found (accent ring / animation). */
    val solved: Int? = null,
    /** Index that pulses as a hint after two misses (docs/12 M11). */
    val hint: Int? = null,
    /** Per option: how many times it was tapped wrongly (drives the wiggle). */
    val wiggles: Map<Int, Int> = emptyMap(),
    val winStars: Int? = null,
    /** The thing asked about: the model photo in the voiceless mode, the counted photo in "Ile jest?". */
    val subject: PhotoSource? = null,
    val subjectLabel: String? = null,
    /** "Ile jest?": how many copies of [subject] are shown. */
    val count: Int = 0,
    /** No voice at all: "Gdzie jest…?" becomes "find the same picture" (docs/12 M10.3 point 4). */
    val voiceless: Boolean = false,
    val game: Int = 0,
)

sealed interface RoundEvent {
    data class Tap(val index: Int) : RoundEvent
    data object Replay : RoundEvent
    data object Restart : RoundEvent
    data object Home : RoundEvent
}

/**
 * Shared timing and saving for round-based task games (docs/12 M10.5). Subclasses deal the rounds and decide what
 * is said; the rules live in [RoundEngine].
 */
abstract class RoundGameViewModel<T>(
    private val kind: GameKind,
    private val levelKey: String,
    private val results: GameResultRepository,
    private val clock: Clock,
    private val categoryId: Long? = null,
) : ViewModel() {

    protected val state = MutableStateFlow(RoundUiState())
    val uiState: StateFlow<RoundUiState> = state.asStateFlow()

    private val effects = Channel<GameEffect>(Channel.BUFFERED)
    val effect: Flow<GameEffect> = effects.receiveAsFlow()

    private var engine: RoundEngine<T>? = null
    private var talking: Job? = null
    private var pending: Job? = null
    private var startedAt = 0L

    protected abstract suspend fun makeRounds(): List<Round<T>>
    protected abstract fun optionUi(item: T, index: Int): OptionUi

    /** Extra state for a new round (subject photo, count…). */
    protected open fun roundState(round: Round<T>, base: RoundUiState): RoundUiState = base

    /** Says the question; called at the start of a round, after a miss and on the speaker button. */
    protected open suspend fun ask(round: Round<T>) = Unit

    /** Praise after a right answer (label, counting…); the round moves on when it returns and the pause passed. */
    protected open suspend fun praise(round: Round<T>) = Unit

    protected open val feedbackMs: Long = 1_200L

    protected fun sound(sfx: Sfx, rate: Float = 1f) {
        effects.trySend(GameEffect.Sound(sfx, rate))
    }

    /** Call from the subclass `init` once its own fields are ready. */
    protected fun begin() {
        viewModelScope.launch { newGame() }
    }

    fun onEvent(event: RoundEvent) {
        when (event) {
            is RoundEvent.Tap -> answer(event.index)
            RoundEvent.Replay -> engine?.let { e -> if (e.phase == RoundPhase.Answering) say { ask(e.current) } }
            RoundEvent.Restart -> viewModelScope.launch { newGame() }
            RoundEvent.Home -> {
                pending?.cancel()
                talking?.cancel()
                effects.trySend(GameEffect.Home)
            }
        }
    }

    private suspend fun newGame() {
        pending?.cancel()
        talking?.cancel()
        val rounds = makeRounds()
        if (rounds.isEmpty()) {
            state.value = RoundUiState(loading = false, empty = true)
            return
        }
        engine = RoundEngine(rounds)
        startedAt = clock.now()
        state.update { RoundUiState(loading = false, totalRounds = rounds.size, game = it.game + 1) }
        showRound()
    }

    private fun showRound() {
        val e = engine ?: return
        val round = e.current
        state.update { s ->
            roundState(
                round,
                s.copy(round = e.roundIndex, options = round.options.mapIndexed { i, item -> optionUi(item, i) }, solved = null, hint = null, wiggles = emptyMap()),
            )
        }
        say { ask(round) }
    }

    private fun say(block: suspend () -> Unit) {
        talking?.cancel()
        talking = viewModelScope.launch { block() }
    }

    private fun answer(index: Int) {
        val e = engine ?: return
        val round = e.current
        when (val outcome = e.answer(index)) {
            AnswerOutcome.Ignored -> Unit
            is AnswerOutcome.Correct -> {
                state.update { it.copy(solved = index, hint = null) }
                sound(Sfx.MATCH)
                pending = viewModelScope.launch {
                    talking?.cancel()
                    val started = clock.now()
                    praise(round)
                    val left = feedbackMs - (clock.now() - started)
                    if (left > 0) delay(left)
                    if (outcome.lastRound) finish(e) else {
                        e.next()
                        showRound()
                    }
                }
            }
            is AnswerOutcome.Wrong -> {
                state.update { s ->
                    s.copy(
                        wiggles = s.wiggles + (index to (s.wiggles[index] ?: 0) + 1),
                        hint = if (outcome.mistakesInRound >= 2) round.correctIndex else s.hint,
                    )
                }
                sound(Sfx.SOFT)
                say {
                    delay(350)
                    ask(round)
                }
            }
        }
    }

    private suspend fun finish(e: RoundEngine<T>) {
        val stars = RoundScoring.stars(e.totalRounds, e.mistakes)
        results.save(
            GameResult(
                game = kind, levelKey = levelKey, categoryId = categoryId, stars = stars, moves = e.totalRounds,
                mistakes = e.mistakes, hintsUsed = 0, durationMs = clock.now() - startedAt, finishedAt = clock.now(),
            ),
        )
        state.update { it.copy(round = e.totalRounds, winStars = stars) }
        sound(Sfx.WIN)
    }
}
