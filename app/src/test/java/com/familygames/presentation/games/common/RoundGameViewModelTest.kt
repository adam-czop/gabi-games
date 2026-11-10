package com.familygames.presentation.games.common

import com.familygames.domain.game.round.Round
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.GameResult
import com.familygames.domain.repository.GameResultRepository
import com.familygames.util.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoundGameViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val saved = mutableListOf<GameResult>()
    private val results = object : GameResultRepository {
        override suspend fun save(result: GameResult) {
            saved += result
        }
        override fun observeBestStars(game: GameKind): Flow<Map<String, Int>> = emptyFlow()
        override fun observeSince(fromEpochMs: Long): Flow<List<GameResult>> = emptyFlow()
        override suspend fun purgeOlderThan(cutoffEpochMs: Long) = 0
        override suspend fun clear() = Unit
    }

    private class Vm(rounds: List<Round<Int>>, results: GameResultRepository, clock: Clock) :
        RoundGameViewModel<Int>(GameKind.COUNTING, "count_3", results, clock) {
        private val fixed = rounds
        var asked = 0

        init {
            begin()
        }

        override suspend fun makeRounds() = fixed
        override fun optionUi(item: Int, index: Int) = OptionUi(item.toLong(), number = item)
        override suspend fun ask(round: Round<Int>) {
            asked++
        }
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(rounds: List<Round<Int>>) = Vm(rounds, results, Clock { testScheduler.currentTime }).also { runCurrent() }

    @Test
    fun `two misses show the hint, right answers win and save the result`() = runTest(dispatcher) {
        val vm = vm(listOf(Round(listOf(1, 2, 3), 1), Round(listOf(1, 2, 3), 2)))
        assertEquals(1, vm.asked)
        vm.onEvent(RoundEvent.Tap(0))
        assertNull(vm.uiState.value.hint)
        vm.onEvent(RoundEvent.Tap(2))
        assertEquals(1, vm.uiState.value.hint)
        assertEquals(1, vm.uiState.value.wiggles[0])

        vm.onEvent(RoundEvent.Tap(1))
        assertEquals(1, vm.uiState.value.solved)
        vm.onEvent(RoundEvent.Tap(0)) // locked while the right answer is shown
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.round)
        assertNull(vm.uiState.value.solved)

        vm.onEvent(RoundEvent.Tap(2))
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.winStars) // 2 mistakes in 2 rounds: more than rounds / 2 → 1★
        assertEquals("count_3", saved.single().levelKey)
        assertEquals(2, saved.single().mistakes)
    }

    @Test
    fun `no rounds means an empty game, not a crash`() = runTest(dispatcher) {
        val vm = vm(emptyList())
        assertTrue(vm.uiState.value.empty)
    }
}
