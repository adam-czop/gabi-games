package com.familygames.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.familygames.presentation.common.Sfx
import com.familygames.presentation.common.SoundPlayer
import com.familygames.presentation.games.bubbles.BubblesScreen
import com.familygames.presentation.games.bubbles.BubblesViewModel
import com.familygames.presentation.games.coloring.ColoringScreen
import com.familygames.presentation.games.coloring.ColoringViewModel
import com.familygames.presentation.games.common.GameEffect
import com.familygames.presentation.games.common.LevelPickEffect
import com.familygames.presentation.games.common.LevelPickScreen
import com.familygames.presentation.games.common.LevelPickViewModel
import com.familygames.presentation.games.counting.CountingScreen
import com.familygames.presentation.games.counting.CountingViewModel
import com.familygames.presentation.games.maze.MazeScreen
import com.familygames.presentation.games.maze.MazeViewModel
import com.familygames.presentation.games.find.FindScreen
import com.familygames.presentation.games.find.FindViewModel
import com.familygames.presentation.games.sorter.SorterScreen
import com.familygames.presentation.games.sorter.SorterViewModel
import com.familygames.presentation.games.halves.HalvesScreen
import com.familygames.presentation.games.halves.HalvesViewModel
import com.familygames.presentation.games.missing.MissingScreen
import com.familygames.presentation.games.missing.MissingViewModel
import com.familygames.presentation.games.marble.MarblesScreen
import com.familygames.presentation.games.marble.MarblesViewModel
import com.familygames.presentation.games.shadow.ShadowsScreen
import com.familygames.presentation.games.shadow.ShadowsViewModel
import com.familygames.presentation.games.memory.MemoryEffect
import com.familygames.presentation.games.memory.MemoryScreen
import com.familygames.presentation.games.memory.MemoryViewModel
import com.familygames.presentation.games.oddone.OddOneOutScreen
import com.familygames.presentation.games.oddone.OddOneOutViewModel
import com.familygames.presentation.games.peekaboo.PeekabooScreen
import com.familygames.presentation.games.peekaboo.PeekabooViewModel
import com.familygames.presentation.games.scratch.ScratchScreen
import com.familygames.presentation.games.scratch.ScratchViewModel
import com.familygames.presentation.games.sequence.SequencePickScreen
import com.familygames.presentation.games.sequence.SequencePickViewModel
import com.familygames.presentation.games.sequence.SequenceScreen
import com.familygames.presentation.games.sequence.SequenceViewModel
import com.familygames.presentation.games.sorting.SortingScreen
import com.familygames.presentation.games.sorting.SortingViewModel
import com.familygames.presentation.games.whereis.WhereIsScreen
import com.familygames.presentation.games.whereis.WhereIsViewModel
import com.familygames.presentation.games.xylophone.XylophoneScreen
import com.familygames.presentation.games.xylophone.XylophoneViewModel
import com.familygames.presentation.theme.KidTheme
import com.familygames.presentation.theme.Palette
import kotlinx.coroutines.flow.Flow
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Routes of the docs/12 games. */
fun NavGraphBuilder.newGamesGraph(navController: NavHostController, sound: SoundPlayer) {
    val star: (Int) -> Unit = { sound.play(Sfx.STAR, 1f + 0.12f * it) }

    composable<GameLevelsRoute> { entry ->
        val route = entry.toRoute<GameLevelsRoute>()
        val vm: LevelPickViewModel = koinViewModel { parametersOf(route.game) }
        val state by vm.uiState.collectAsStateWithLifecycle()
        val shakes = remember { mutableStateMapOf<String, Int>() }
        LaunchedEffect(vm) {
            vm.effect.collect { effect ->
                when (effect) {
                    is LevelPickEffect.Play -> {
                        sound.play(Sfx.TAP)
                        navController.navigateOnce(effect.route)
                    }
                    is LevelPickEffect.Refuse -> shakes[effect.key] = (shakes[effect.key] ?: 0) + 1
                }
            }
        }
        KidTheme { LevelPickScreen(state, vm::onEvent, onBack = navController::backToMenu, shakeRequests = shakes) }
    }

    composable<WhereIsRoute> { entry ->
        val route = entry.toRoute<WhereIsRoute>()
        val vm: WhereIsViewModel = koinViewModel { parametersOf(route.level, route.categoryId) }
        val state by vm.uiState.collectAsStateWithLifecycle()
        GameEffects(vm.effect, sound, navController)
        KidTheme { WhereIsScreen(state, vm::onEvent, star) }
    }
    composable<OddOneOutRoute> { entry ->
        val vm: OddOneOutViewModel = koinViewModel { parametersOf(entry.toRoute<OddOneOutRoute>().level) }
        val state by vm.uiState.collectAsStateWithLifecycle()
        GameEffects(vm.effect, sound, navController)
        KidTheme { OddOneOutScreen(state, vm::onEvent, star) }
    }
    composable<CountingRoute> { entry ->
        val vm: CountingViewModel = koinViewModel { parametersOf(entry.toRoute<CountingRoute>().level) }
        val state by vm.uiState.collectAsStateWithLifecycle()
        GameEffects(vm.effect, sound, navController)
        KidTheme { CountingScreen(state, vm::onEvent, vm::countTap, star) }
    }
    composable<SortingRoute> { entry ->
        val vm: SortingViewModel = koinViewModel { parametersOf(entry.toRoute<SortingRoute>().level) }
        val state by vm.uiState.collectAsStateWithLifecycle()
        GameEffects(vm.effect, sound, navController)
        KidTheme { SortingScreen(state, vm::onEvent, vm::drop, star) }
    }
    composable<SoundMemoRoute> { entry ->
        val route = entry.toRoute<SoundMemoRoute>()
        val vm: MemoryViewModel = koinViewModel(key = "sound-${route.level}") { parametersOf(route.level, SOUND_MEMO_CATEGORY, true) }
        val state by vm.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(vm) {
            vm.effect.collect { effect ->
                when (effect) {
                    is MemoryEffect.Sound -> sound.play(effect.sfx)
                    MemoryEffect.NavigateHome -> navController.backToMenu()
                }
            }
        }
        KidTheme { MemoryScreen(state = state, onEvent = vm::onEvent, onStarShown = star) }
    }
    composable<SequencePickRoute> {
        val vm: SequencePickViewModel = koinViewModel()
        val list by vm.sequences.collectAsStateWithLifecycle()
        val sequences = list
        // One sequence: straight into the game, without a choice the child cannot read.
        LaunchedEffect(sequences) {
            if (sequences != null && sequences.size == 1) {
                navController.navigate(SequencePlayRoute(sequences.first().id)) { popUpTo<SequencePickRoute> { inclusive = true } }
            }
        }
        KidTheme {
            if (sequences == null || sequences.size == 1) {
                Box(Modifier.fillMaxSize().background(Palette.Bg))
            } else {
                SequencePickScreen(sequences, onPick = { navController.navigateOnce(SequencePlayRoute(it)) }, onBack = navController::backToMenu)
            }
        }
    }
    composable<SequencePlayRoute> { entry ->
        val vm: SequenceViewModel = koinViewModel { parametersOf(entry.toRoute<SequencePlayRoute>().sequenceId) }
        val state by vm.uiState.collectAsStateWithLifecycle()
        GameEffects(vm.effect, sound, navController)
        KidTheme { SequenceScreen(state, vm::onEvent, vm::drop, star) }
    }

    composable<PeekabooRoute> {
        val vm: PeekabooViewModel = koinViewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        GameEffects(vm.effect, sound, navController)
        KidTheme { PeekabooScreen(state, vm::onEvent) }
    }
    composable<ScratchRoute> {
        val vm: ScratchViewModel = koinViewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        GameEffects(vm.effect, sound, navController)
        KidTheme { ScratchScreen(state, vm::onEvent) }
    }
    composable<BubblesRoute> {
        val vm: BubblesViewModel = koinViewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        GameEffects(vm.effect, sound, navController)
        KidTheme { BubblesScreen(state, vm::popped, vm::home) }
    }
    composable<XylophoneRoute> {
        val vm: XylophoneViewModel = koinViewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        GameEffects(vm.effect, sound, navController)
        KidTheme { XylophoneScreen(state, vm::strike, vm::toggleMelody, vm::home) }
    }
    composable<ColoringRoute> {
        val vm: ColoringViewModel = koinViewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        GameEffects(vm.effect, sound, navController)
        KidTheme { ColoringScreen(state, vm::onEvent) }
    }
    composable<MazeRoute> { entry ->
        val vm: MazeViewModel = koinViewModel { parametersOf(entry.toRoute<MazeRoute>().level) }
        val state by vm.uiState.collectAsStateWithLifecycle()
        GameEffects(vm.effect, sound, navController)
        KidTheme { MazeScreen(state, vm::onEvent, star) }
    }
    composable<ShadowsRoute> { entry ->
        val vm: ShadowsViewModel = koinViewModel { parametersOf(entry.toRoute<ShadowsRoute>().level) }
        val state by vm.uiState.collectAsStateWithLifecycle()
        GameEffects(vm.effect, sound, navController)
        KidTheme { ShadowsScreen(state, vm::onEvent, vm::drop, star) }
    }
    composable<MarblesRoute> { entry ->
        val vm: MarblesViewModel = koinViewModel { parametersOf(entry.toRoute<MarblesRoute>().level) }
        val state by vm.uiState.collectAsStateWithLifecycle()
        GameEffects(vm.effect, sound, navController)
        KidTheme { MarblesScreen(state, vm::onEvent, star) }
    }
    composable<FindRoute> { entry ->
        val vm: FindViewModel = koinViewModel { parametersOf(entry.toRoute<FindRoute>().level) }
        val state by vm.uiState.collectAsStateWithLifecycle()
        GameEffects(vm.effect, sound, navController)
        KidTheme { FindScreen(state, vm::onEvent, star) }
    }
    composable<MissingRoute> { entry ->
        val vm: MissingViewModel = koinViewModel { parametersOf(entry.toRoute<MissingRoute>().level) }
        val state by vm.uiState.collectAsStateWithLifecycle()
        GameEffects(vm.effect, sound, navController)
        KidTheme { MissingScreen(state, vm::onEvent, star) }
    }
    composable<HalvesRoute> { entry ->
        val vm: HalvesViewModel = koinViewModel { parametersOf(entry.toRoute<HalvesRoute>().level) }
        val state by vm.uiState.collectAsStateWithLifecycle()
        GameEffects(vm.effect, sound, navController)
        KidTheme { HalvesScreen(state, vm::onEvent, star) }
    }
    composable<SorterRoute> { entry ->
        val vm: SorterViewModel = koinViewModel { parametersOf(entry.toRoute<SorterRoute>().level) }
        val state by vm.uiState.collectAsStateWithLifecycle()
        GameEffects(vm.effect, sound, navController)
        KidTheme { SorterScreen(state, vm::onEvent, vm::drop, star) }
    }
}

/** "Memo dźwiękowe" takes photos from every visible category, so it has no category of its own. */
const val SOUND_MEMO_CATEGORY = -1L

@Composable
private fun GameEffects(effects: Flow<GameEffect>, sound: SoundPlayer, navController: NavController) {
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is GameEffect.Sound -> sound.play(effect.sfx, effect.rate)
                GameEffect.Home -> navController.backToMenu()
            }
        }
    }
}
