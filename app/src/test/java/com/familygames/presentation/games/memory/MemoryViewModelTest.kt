package com.familygames.presentation.games.memory

import app.cash.turbine.test
import com.familygames.domain.game.memory.DealMemoryCardsUseCase
import com.familygames.domain.game.memory.MemoryPhase
import com.familygames.domain.game.memory.MemoryTimings
import com.familygames.domain.game.memory.testPhoto
import com.familygames.domain.model.AppSettings
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.GameResult
import com.familygames.domain.model.MemoryLevel
import com.familygames.domain.model.NewPhoto
import com.familygames.domain.model.Photo
import com.familygames.domain.repository.GameResultRepository
import com.familygames.domain.repository.PhotoRepository
import com.familygames.domain.repository.SettingsRepository
import com.familygames.presentation.common.Sfx
import com.familygames.util.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val saved = mutableListOf<GameResult>()
    private val played = mutableListOf<String>()

    private val photos = object : PhotoRepository {
        val pool = listOf(testPhoto(1), testPhoto(2).copy(audioPath = "/audio/2.m4a"))
        override fun observeAll(): Flow<List<Photo>> = emptyFlow()
        override fun observeByCategory(categoryId: Long): Flow<List<Photo>> = emptyFlow()
        override fun observeForPuzzle(): Flow<List<Photo>> = emptyFlow()
        override fun observeMemoryCount(categoryId: Long): Flow<Int> = emptyFlow()
        override suspend fun getForMemory(categoryId: Long) = pool
        override suspend fun get(id: Long): Photo? = pool.firstOrNull { it.id == id }
        override suspend fun add(photo: NewPhoto) = 0L
        override suspend fun updateDetails(id: Long, label: String?, categoryId: Long, useInMemory: Boolean, useInPuzzle: Boolean) = Unit
        override suspend fun setAudio(id: Long, relativeAudioPath: String?) = Unit
        override suspend fun moveTo(ids: Collection<Long>, categoryId: Long) = Unit
        override suspend fun setPromptAudio(id: Long, relativeAudioPath: String?) = Unit
        override suspend fun setCountable(id: Long, countable: Boolean) = Unit
        override suspend fun delete(ids: Collection<Long>) = 0
    }
    private val results = object : GameResultRepository {
        override suspend fun save(result: GameResult) {
            saved += result
        }
        override fun observeBestStars(game: GameKind): Flow<Map<String, Int>> = emptyFlow()
        override fun observeSince(fromEpochMs: Long): Flow<List<GameResult>> = emptyFlow()
        override suspend fun purgeOlderThan(cutoffEpochMs: Long) = 0
        override suspend fun clear() = Unit
    }
    private val settings = object : SettingsRepository {
        override val settings = MutableStateFlow(AppSettings())
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            settings.value = transform(settings.value)
        }
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): MemoryViewModel {
        val vm = MemoryViewModel(
            MemoryLevel.L1, categoryId = 1, photos, results, settings,
            DealMemoryCardsUseCase(Random(1)), MemoryHistory(), Clock { testScheduler.currentTime },
            voice = { path -> played += path; kotlinx.coroutines.delay(3_000) },
        )
        runCurrent()
        return vm
    }

    private fun MemoryViewModel.pair(key: Long) = uiState.value.cards.filter { it.pairKey == key }.map { it.id }

    @Test
    fun `deal shows 4 face-down cards`() = runTest(dispatcher) {
        val vm = viewModel()
        val s = vm.uiState.value
        assertEquals(4, s.cards.size)
        assertEquals(2 to 2, s.rows to s.cols)
        assertTrue(s.cards.none { it.faceUp })
    }

    @Test
    fun `mismatch shows the soft highlight, then covers both cards after the delay`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.effect.test {
            vm.onEvent(MemoryEvent.CardTapped(vm.pair(1)[0]))
            vm.onEvent(MemoryEvent.CardTapped(vm.pair(2)[0]))
            assertEquals(MemoryEffect.Sound(Sfx.FLIP), awaitItem())
            assertEquals(MemoryEffect.Sound(Sfx.FLIP), awaitItem())
            assertTrue(vm.uiState.value.inputLocked)

            advanceTimeBy(MemoryTimings.CHECK_DELAY_MS + 1)
            assertEquals(MemoryEffect.Sound(Sfx.SOFT), awaitItem())
            assertEquals(2, vm.uiState.value.cards.count { it.highlight == CardHighlight.Soft })

            advanceTimeBy(MemoryTimings.mismatchFor(MemoryLevel.L1))
            runCurrent()
            assertTrue(vm.uiState.value.cards.none { it.faceUp })
            assertFalse(vm.uiState.value.inputLocked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `winning saves the result before showing stars and plays the parent's voice`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.effect.test {
            vm.pair(1).forEach { vm.onEvent(MemoryEvent.CardTapped(it)) }
            advanceTimeBy(MemoryTimings.CHECK_DELAY_MS + 1)
            vm.pair(2).forEach { vm.onEvent(MemoryEvent.CardTapped(it)) }
            advanceTimeBy(10_000)

            val effects = cancelAndConsumeRemainingEvents().map { (it as app.cash.turbine.Event.Item).value }
            assertEquals(MemoryEffect.Sound(Sfx.WIN), effects.last())
        }
        val s = vm.uiState.value
        assertEquals(MemoryPhase.Won, s.phase)
        assertEquals(3, s.stars)
        assertEquals(1, saved.size)
        assertEquals("memory_2x2", saved.single().levelKey)
        assertEquals(0, saved.single().mistakes)
        assertEquals(listOf("/audio/2.m4a"), played)
    }

    @Test
    fun `restart during a mismatch cancels the pending cover and deals again`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onEvent(MemoryEvent.CardTapped(vm.pair(1)[0]))
        vm.onEvent(MemoryEvent.CardTapped(vm.pair(2)[0]))
        advanceTimeBy(MemoryTimings.CHECK_DELAY_MS + 1)

        vm.onEvent(MemoryEvent.Restart)
        advanceTimeBy(5_000)

        val s = vm.uiState.value
        assertTrue(s.cards.none { it.faceUp })
        assertFalse(s.inputLocked)
        assertNull(s.stars)
        assertEquals(2, s.dealNumber)
    }
}

class LevelTilesTest {
    @Test
    fun `selected, not enough photos and locks follow S02 rules`() {
        val tiles = buildLevelTiles(memoryPhotoCount = 3, bestStars = mapOf("memory_2x2" to 2), lastLevelKey = "memory_2x2", locksEnabled = false)
        assertEquals(
            listOf(LevelStatus.Selected, LevelStatus.Available, LevelStatus.NotEnoughPhotos, LevelStatus.NotEnoughPhotos),
            tiles.map { it.status },
        )
        assertEquals(2, tiles[0].bestStars)

        val locked = buildLevelTiles(memoryPhotoCount = 6, bestStars = mapOf("memory_2x2" to 1), lastLevelKey = "memory_2x2", locksEnabled = true)
        assertEquals(
            listOf(LevelStatus.Selected, LevelStatus.Available, LevelStatus.Locked, LevelStatus.Locked),
            locked.map { it.status },
        )
    }
}
