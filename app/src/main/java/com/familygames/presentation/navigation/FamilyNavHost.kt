package com.familygames.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import android.os.SystemClock
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.familygames.presentation.menu.RestartingScreen
import com.familygames.presentation.menu.rememberUpdateOnMenu
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.toRoute
import com.familygames.domain.game.puzzle.PuzzleMode
import com.familygames.domain.model.MemoryLevel
import com.familygames.domain.repository.SettingsRepository
import com.familygames.presentation.common.Sfx
import com.familygames.presentation.common.SoundPlayer
import com.familygames.presentation.games.GameRegistry
import com.familygames.presentation.games.memory.MemoryEffect
import com.familygames.presentation.games.memory.MemoryLevelEffect
import com.familygames.presentation.games.memory.MemoryLevelScreen
import com.familygames.presentation.games.memory.MemoryLevelViewModel
import com.familygames.presentation.games.memory.MemoryScreen
import com.familygames.presentation.games.memory.MemoryViewModel
import com.familygames.presentation.games.puzzle.PuzzleEffect
import com.familygames.presentation.games.puzzle.PuzzleSetupScreen
import com.familygames.presentation.games.puzzle.PuzzleSetupViewModel
import com.familygames.presentation.games.puzzle.SnapPuzzleScreen
import com.familygames.presentation.games.puzzle.SnapPuzzleViewModel
import com.familygames.presentation.games.puzzle.SwapPuzzleScreen
import com.familygames.presentation.games.puzzle.SwapPuzzleViewModel
import com.familygames.presentation.menu.MainMenuEvent
import com.familygames.presentation.menu.MainMenuScreen
import com.familygames.presentation.menu.MainMenuViewModel
import com.familygames.presentation.parent.common.ParentSession
import com.familygames.presentation.parent.gate.GatePurpose
import com.familygames.presentation.parent.gate.ParentGateHost
import com.familygames.presentation.theme.KidTheme
import com.familygames.presentation.theme.Palette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** App navigation graph (docs/01 §4). Starts with the first-run wizard (S12) until it was completed. */
@Composable
fun FamilyNavHost(navController: NavHostController = rememberNavController()) {
    val settings: SettingsRepository = koinInject()
    val firstRunDone by produceState<Boolean?>(null) { value = settings.settings.first().firstRunCompleted }
    val done = firstRunDone
    if (done == null) {
        Box(Modifier.fillMaxSize().background(Palette.Bg))
        return
    }
    val sound: SoundPlayer = koinInject()
    val session: ParentSession = koinInject()
    // Picker and camera are allowed in lock task only while a parent-zone screen is shown.
    val entry by navController.currentBackStackEntryAsState()
    val inParentZone = entry?.destination?.hierarchy?.any { it.hasRoute<ParentGraph>() } == true
    LaunchedEffect(inParentZone) { if (inParentZone) session.begin() else session.end() }
    NavHost(navController = navController, startDestination = if (done) MenuRoute else FirstRunRoute) {
        childGraph(navController, sound, session)
        newGamesGraph(navController, sound)
        parentGraph(navController, session)
    }
}

private fun NavGraphBuilder.childGraph(navController: NavHostController, sound: SoundPlayer, session: ParentSession) {
    composable<MenuRoute> {
        val vm: MainMenuViewModel = koinViewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        // Every opening gets a new key, so the gate never reuses a finished ViewModel (it was "enter-1" again after 0).
        var gateOpen by rememberSaveable { mutableStateOf(false) }
        var gateKey by rememberSaveable { mutableLongStateOf(0L) }
        var lastTouch by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
        val restarting = rememberUpdateOnMenu(lastTouch = { lastTouch }, gateOpen = { gateOpen })
        KidTheme {
            MainMenuScreen(
                modifier = Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            lastTouch = SystemClock.elapsedRealtime()
                        }
                    }
                },
                games = state.games,
                showComingSoon = !state.loading && GameRegistry.showComingSoonTile(state.games.size),
                soundEnabled = state.soundEnabled,
                onGameClick = {
                    sound.play(Sfx.TAP)
                    navController.navigateOnce(it.entryRoute)
                },
                onToggleSound = { vm.onEvent(MainMenuEvent.ToggleSound) },
                onParentGate = {
                    if (!gateOpen) {
                        gateKey = SystemClock.elapsedRealtimeNanos()
                        gateOpen = true
                    }
                },
            )
        }
        if (restarting) KidTheme { RestartingScreen() }
        if (gateOpen) {
            ParentGateHost(
                purpose = GatePurpose.EnterParentZone,
                key = "enter-$gateKey",
                onPassed = {
                    gateOpen = false
                    session.touch()
                    navController.navigateOnce(ParentGraph)
                },
                onClosed = { gateOpen = false },
            )
        }
    }

    composable<MemoryLevelsRoute> {
        val vm: MemoryLevelViewModel = koinViewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        val shakes = remember { mutableStateMapOf<MemoryLevel, Int>() }
        LaunchedEffect(vm) {
            vm.effect.collect { effect ->
                when (effect) {
                    is MemoryLevelEffect.Play -> {
                        sound.play(Sfx.TAP)
                        navController.navigateOnce(MemoryPlayRoute(effect.level, effect.categoryId))
                    }
                    is MemoryLevelEffect.Refuse -> shakes[effect.level] = (shakes[effect.level] ?: 0) + 1
                }
            }
        }
        KidTheme { MemoryLevelScreen(state = state, onEvent = vm::onEvent, onBack = navController::backToMenu, shakeRequests = shakes) }
    }
    composable<MemoryPlayRoute> { entry ->
        val route = entry.toRoute<MemoryPlayRoute>()
        val vm: MemoryViewModel = koinViewModel { parametersOf(route.level, route.categoryId) }
        val state by vm.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(vm) {
            vm.effect.collect { effect ->
                when (effect) {
                    is MemoryEffect.Sound -> sound.play(effect.sfx)
                    MemoryEffect.NavigateHome -> navController.backToMenu()
                }
            }
        }
        KidTheme { MemoryScreen(state = state, onEvent = vm::onEvent, onStarShown = { sound.play(Sfx.STAR, 1f + 0.12f * it) }) }
    }

    composable<PuzzleSetupRoute> {
        val vm: PuzzleSetupViewModel = koinViewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(vm) {
            vm.effect.collect { play ->
                sound.play(Sfx.TAP)
                val d = play.difficulty
                navController.navigateOnce(
                    if (d.mode == PuzzleMode.SNAP) SnapPuzzleRoute(play.photoId, d.rows, d.cols) else SwapPuzzleRoute(play.photoId, d.rows),
                )
            }
        }
        KidTheme { PuzzleSetupScreen(state = state, onEvent = vm::onEvent, onBack = navController::backToMenu) }
    }
    composable<SnapPuzzleRoute> { entry ->
        val route = entry.toRoute<SnapPuzzleRoute>()
        val vm: SnapPuzzleViewModel = koinViewModel { parametersOf(route.photoId, route.rows, route.cols) }
        val state by vm.uiState.collectAsStateWithLifecycle()
        PuzzleEffects(vm.effect, sound, navController)
        KidTheme { SnapPuzzleScreen(state, vm::onEvent, onStarShown = { sound.play(Sfx.STAR, 1f + 0.12f * it) }) }
    }
    composable<SwapPuzzleRoute> { entry ->
        val route = entry.toRoute<SwapPuzzleRoute>()
        val vm: SwapPuzzleViewModel = koinViewModel { parametersOf(route.photoId, route.size) }
        val state by vm.uiState.collectAsStateWithLifecycle()
        PuzzleEffects(vm.effect, sound, navController)
        KidTheme { SwapPuzzleScreen(state, vm::onEvent, onStarShown = { sound.play(Sfx.STAR, 1f + 0.12f * it) }) }
    }
}

@Composable
private fun PuzzleEffects(effects: Flow<PuzzleEffect>, sound: SoundPlayer, navController: NavController) {
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is PuzzleEffect.Sound -> sound.play(effect.sfx)
                PuzzleEffect.NavigateHome -> navController.backToMenu()
            }
        }
    }
}
