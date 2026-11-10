package com.familygames.presentation.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import androidx.lifecycle.repeatOnLifecycle
import com.familygames.BuildConfig
import com.familygames.kiosk.KioskManager
import com.familygames.presentation.parent.settings.CriticalAction
import com.familygames.presentation.parent.settings.CriticalActionHost
import com.familygames.presentation.parent.settings.SettingsEvent
import com.familygames.presentation.parent.settings.SettingsScreen
import com.familygames.presentation.parent.settings.SettingsUiState
import com.familygames.presentation.parent.settings.SettingsViewModel
import com.familygames.presentation.parent.settings.UpdatesCard
import com.familygames.data.updater.UpdateManager
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.familygames.R
import com.familygames.data.local.files.PhotoImporter
import com.familygames.presentation.parent.categories.CategoriesScreen
import com.familygames.presentation.parent.categories.CategoriesViewModel
import com.familygames.presentation.parent.common.ParentHeader
import com.familygames.presentation.parent.common.ParentScaffold
import com.familygames.presentation.parent.common.ParentSection
import com.familygames.presentation.parent.common.ParentSession
import com.familygames.presentation.parent.games.GamesScreen
import com.familygames.presentation.parent.games.GamesViewModel
import com.familygames.presentation.parent.sequences.SequencesScreen
import com.familygames.presentation.parent.sequences.SequencesViewModel
import com.familygames.presentation.parent.onboarding.FirstRunExit
import com.familygames.presentation.parent.onboarding.FirstRunScreen
import com.familygames.presentation.parent.onboarding.FirstRunViewModel
import com.familygames.presentation.parent.photos.PhotoEditScreen
import com.familygames.presentation.parent.photos.PhotoEditViewModel
import com.familygames.presentation.parent.photos.PhotoManagerScreen
import com.familygames.presentation.parent.photos.PhotoManagerViewModel
import com.familygames.presentation.parent.progress.ProgressScreen
import com.familygames.presentation.parent.progress.ProgressViewModel
import com.familygames.presentation.theme.Palette
import com.familygames.presentation.theme.ParentType
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Parent zone (S09–S14) and the first-run wizard (S12). Entered only through the parent gate. */
fun NavGraphBuilder.parentGraph(navController: NavController, session: ParentSession) {
    composable<FirstRunRoute> {
        val vm: FirstRunViewModel = koinViewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(vm) {
            vm.exit.collect { exit ->
                navController.navigate(MenuRoute) { popUpTo(0) { inclusive = true } }
                if (exit == FirstRunExit.AddPhotos) {
                    session.touch()
                    navController.navigate(ParentGraph)
                }
            }
        }
        FirstRunScreen(state, vm::onEvent)
    }

    navigation<ParentGraph>(startDestination = ParentPhotosRoute) {
        composable<ParentPhotosRoute> {
            ParentSessionTimeout(session, navController)
            val vm: PhotoManagerViewModel = koinViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            val importer: PhotoImporter = koinInject()
            Frame(ParentSection.Photos, navController, session) { _ ->
                PhotoManagerScreen(
                    state = state,
                    onEvent = vm::onEvent,
                    onOpenPhoto = { navController.navigateOnce(ParentPhotoEditRoute(photoId = it)) },
                    // Picker/camera results arrive before the activity is RESUMED again, so no navigateOnce here.
                    onAddPhotos = { uris, categoryId -> navController.navigate(ParentPhotoEditRoute(pendingUris = uris, categoryId = categoryId)) },
                    newCameraUri = importer::newCameraUri,
                    onClearNewCategoryError = vm::clearNewCategoryError,
                )
            }
        }
        composable<ParentPhotoEditRoute> { entry ->
            ParentSessionTimeout(session, navController)
            val route = entry.toRoute<ParentPhotoEditRoute>()
            val vm: PhotoEditViewModel = koinViewModel { parametersOf(route.photoId, route.pendingUris, route.categoryId) }
            val state by vm.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(vm) { vm.finished.collect { navController.popBackStack() } }
            PhotoEditScreen(state, vm::onEvent, onBack = { navController.popBackStack() }, onInteraction = session::touch)
        }
        composable<ParentCategoriesRoute> {
            ParentSessionTimeout(session, navController)
            val vm: CategoriesViewModel = koinViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            Frame(ParentSection.Categories, navController, session) { _ -> CategoriesScreen(state, vm::onEvent) }
        }
        composable<ParentSequencesRoute> {
            ParentSessionTimeout(session, navController)
            val vm: SequencesViewModel = koinViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            Frame(ParentSection.Sequences, navController, session) { _ -> SequencesScreen(state, vm::onEvent) }
        }
        composable<ParentGamesRoute> {
            ParentSessionTimeout(session, navController)
            val vm: GamesViewModel = koinViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            Frame(ParentSection.Games, navController, session) { _ -> GamesScreen(state, vm::onEvent) }
        }
        composable<ParentProgressRoute> {
            ParentSessionTimeout(session, navController)
            val vm: ProgressViewModel = koinViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            Frame(ParentSection.Progress, navController, session) { _ -> ProgressScreen(state, vm::deleteDrawing) }
        }
        composable<ParentSettingsRoute> {
            ParentSessionTimeout(session, navController)
            val vm: SettingsViewModel = koinViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            val updates: UpdateManager = koinInject()
            val updateState by updates.state.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
            // Kiosk statuses are read live from DevicePolicyManager whenever the screen is shown.
            LaunchedEffect(lifecycle) {
                lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) { vm.onEvent(SettingsEvent.RefreshKiosk) }
            }
            Frame(ParentSection.Settings, navController, session, onClearProgress = { vm.onEvent(SettingsEvent.ClearProgress) }) { requestCritical ->
                SettingsScreen(state, vm::onEvent, onCritical = requestCritical) {
                    UpdatesCard(
                        update = updateState,
                        state = state,
                        onEvent = vm::onEvent,
                        onCheck = { scope.launch { updates.check() } },
                        onInstall = {
                            updates.installIfReady(onMenu = false, idleMs = 0, parentRequested = true, hourOfDay = 0, appInBackground = false)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Frame(
    section: ParentSection,
    navController: NavController,
    session: ParentSession,
    onClearProgress: () -> Unit = {},
    content: @Composable (requestCritical: (CriticalAction) -> Unit) -> Unit,
) {
    val kiosk: KioskManager = koinInject()
    var critical by remember { mutableStateOf<CriticalAction?>(null) }
    ParentScaffold(
        selected = section,
        onSection = { target ->
            val route: Any = when (target) {
                ParentSection.Photos -> ParentPhotosRoute
                ParentSection.Categories -> ParentCategoriesRoute
                ParentSection.Sequences -> ParentSequencesRoute
                ParentSection.Progress -> ParentProgressRoute
                ParentSection.Games -> ParentGamesRoute
                ParentSection.Settings -> ParentSettingsRoute
            }
            // Sections replace each other instead of stacking up.
            navController.navigate(route) {
                popUpTo(ParentPhotosRoute)
                launchSingleTop = true
            }
        },
        onBackToGames = navController::backToMenu,
        onInteraction = session::touch,
        onExitKiosk = if (kiosk.isDeviceOwner) ({ critical = CriticalAction.ExitKiosk }) else null,
    ) {
        content { critical = it }
    }
    critical?.let { action ->
        CriticalActionHost(action, onClearProgress = onClearProgress, onFinished = { critical = null })
    }
}

/** Back to S01 after 5 minutes without a touch in the parent zone (docs/03 S09). */
@Composable
private fun ParentSessionTimeout(session: ParentSession, navController: NavController) {
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            if (session.idleMs() > ParentSession.TIMEOUT_MS) {
                navController.backToMenu()
                break
            }
        }
    }
}
