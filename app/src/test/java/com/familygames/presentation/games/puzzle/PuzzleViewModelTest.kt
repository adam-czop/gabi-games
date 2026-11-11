package com.familygames.presentation.games.puzzle

import androidx.compose.ui.graphics.ImageBitmap
import com.familygames.domain.game.memory.testPhoto
import com.familygames.domain.game.puzzle.PuzzleTuning
import com.familygames.domain.game.puzzle.ShuffleSwapBoardUseCase
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.GameResult
import com.familygames.domain.model.NewPhoto
import com.familygames.domain.model.Photo
import com.familygames.domain.repository.GameResultRepository
import com.familygames.domain.repository.PhotoRepository
import com.familygames.util.Clock
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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
class PuzzleViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val saved = mutableListOf<GameResult>()
    private val image: ImageBitmap = mockk(relaxed = true)

    private val photos = object : PhotoRepository {
        override fun observeAll(): Flow<List<Photo>> = emptyFlow()
        override fun observeByCategory(categoryId: Long): Flow<List<Photo>> = emptyFlow()
        override fun observeForPuzzle(): Flow<List<Photo>> = emptyFlow()
        override fun observeMemoryCount(categoryId: Long): Flow<Int> = emptyFlow()
        override suspend fun getForMemory(categoryId: Long) = emptyList<Photo>()
        override suspend fun get(id: Long): Photo? = if (id == 1L) testPhoto(1) else null
        override suspend fun add(photo: NewPhoto) = 0L
        override suspend fun updateDetails(id: Long, label: String?, promptText: String?, categoryId: Long, useInMemory: Boolean, useInPuzzle: Boolean) = Unit
        override suspend fun moveTo(ids: Collection<Long>, categoryId: Long) = Unit
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

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.snap(photoId: Long = 1) =
        SnapPuzzleViewModel(photoId, 2, 2, photos, { image }, results, Clock { testScheduler.currentTime }, Random(1)).also { runCurrent() }

    private fun TestScope.swap() =
        SwapPuzzleViewModel(1, 2, photos, { image }, results, ShuffleSwapBoardUseCase(Random(1)), Clock { testScheduler.currentTime }).also { runCurrent() }

    @Test
    fun `snap - placing every piece wins with 3 stars and saves the result`() = runTest(dispatcher) {
        val vm = snap()
        assertEquals(4, vm.uiState.value.pieces.size)
        vm.onEvent(SnapEvent.PieceMissed(0))
        vm.uiState.value.pieces.forEach { vm.onEvent(SnapEvent.PiecePlaced(it.id)) }
        advanceTimeBy(PuzzleTuning.WIN_DELAY_MS + 1)
        runCurrent()

        val s = vm.uiState.value
        assertTrue(s.won)
        assertEquals(3, s.stars)
        assertEquals("snap_2x2", saved.single().levelKey)
        assertEquals(1, saved.single().mistakes)
    }

    @Test
    fun `snap - hints cost stars and respect the cooldown`() = runTest(dispatcher) {
        val vm = snap()
        vm.onEvent(SnapEvent.Hint)
        vm.onEvent(SnapEvent.Hint) // within cooldown: ignored
        assertEquals(1, vm.uiState.value.hintsUsed)
        assertTrue(vm.uiState.value.ghostBoost)

        advanceTimeBy(PuzzleTuning.HINT_COOLDOWN_MS + 1)
        runCurrent()
        assertFalse(vm.uiState.value.ghostBoost)
        vm.onEvent(SnapEvent.Hint)
        assertEquals(2, vm.uiState.value.hintsUsed)

        vm.uiState.value.pieces.forEach { vm.onEvent(SnapEvent.PiecePlaced(it.id)) }
        advanceUntilIdle()
        assertEquals(2 - 1, vm.uiState.value.stars) // two hints → 1 star
    }

    @Test
    fun `snap - missing photo is reported, not crashed`() = runTest(dispatcher) {
        val vm = snap(photoId = 99)
        assertTrue(vm.uiState.value.missingPhoto)
    }

    @Test
    fun `swap - select, swap neighbours and win`() = runTest(dispatcher) {
        val vm = swap()
        repeat(20) {
            val board = vm.uiState.value.board ?: return@repeat
            if (board.isSolved()) return@repeat
            // Swap the first misplaced tile towards its place, one neighbour step at a time.
            val pos = board.tiles.indices.first { !board.isCorrect(it) }
            val target = board.tiles.indexOf(pos)
            val step = board.neighbours(target).minBy { n -> kotlin.math.abs(n / 2 - pos / 2) + kotlin.math.abs(n % 2 - pos % 2) }
            vm.onEvent(SwapEvent.TileTapped(target))
            vm.onEvent(SwapEvent.TileTapped(step))
        }
        assertTrue(vm.uiState.value.board!!.isSolved())
        advanceUntilIdle()
        assertTrue(vm.uiState.value.won)
        assertEquals("swap_2x2", saved.single().levelKey)
    }

    @Test
    fun `swap - tapping the selected tile deselects, a far tile moves the selection`() = runTest(dispatcher) {
        val vm = swap()
        vm.onEvent(SwapEvent.TileTapped(0))
        assertEquals(0, vm.uiState.value.selected)
        vm.onEvent(SwapEvent.TileTapped(0))
        assertNull(vm.uiState.value.selected)
        vm.onEvent(SwapEvent.TileTapped(0))
        vm.onEvent(SwapEvent.TileTapped(3)) // diagonal: not a neighbour
        assertEquals(3, vm.uiState.value.selected)
        assertEquals(0, vm.uiState.value.moves)
    }
}
