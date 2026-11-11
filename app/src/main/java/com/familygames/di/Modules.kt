package com.familygames.di

import android.content.Context
import android.os.Build
import com.familygames.BuildConfig
import com.familygames.data.updater.AndroidPackageArchiveReader
import com.familygames.data.updater.ApkInstaller
import com.familygames.data.updater.ApkVerifier
import com.familygames.data.updater.GitHubReleaseApi
import com.familygames.data.updater.PackageArchiveReader
import com.familygames.data.updater.PackageInstallerApkInstaller
import com.familygames.data.updater.UpdateManager
import com.familygames.data.updater.UpdateScheduler
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.familygames.data.AppInitializer
import com.familygames.data.local.db.FamilyGamesDatabase
import com.familygames.data.local.files.MediaStorage
import com.familygames.data.local.files.PhotoImporter
import com.familygames.domain.parent.ParentAuthenticator
import com.familygames.domain.parent.ParentChallengeGenerator
import com.familygames.domain.parent.PinHasher
import com.familygames.kiosk.KioskManager
import com.familygames.presentation.parent.categories.CategoriesViewModel
import com.familygames.presentation.parent.common.ParentSession
import com.familygames.presentation.parent.gate.GatePurpose
import com.familygames.presentation.parent.gate.ParentGateViewModel
import com.familygames.presentation.parent.onboarding.FirstRunViewModel
import com.familygames.presentation.parent.photos.PhotoEditViewModel
import com.familygames.presentation.parent.photos.PhotoManagerViewModel
import com.familygames.presentation.parent.settings.SettingsViewModel
import com.familygames.presentation.parent.progress.ProgressViewModel
import com.familygames.presentation.parent.games.GamesViewModel
import com.familygames.presentation.parent.sequences.SequencesViewModel
import com.familygames.data.local.prefs.SettingsDataStore
import com.familygames.data.repository.CategoryRepositoryImpl
import com.familygames.data.repository.GameResultRepositoryImpl
import com.familygames.data.repository.PhotoRepositoryImpl
import com.familygames.data.repository.SettingsRepositoryImpl
import com.familygames.data.seed.DefaultContentSeeder
import com.familygames.domain.game.memory.DealMemoryCardsUseCase
import com.familygames.domain.game.puzzle.ShuffleSwapBoardUseCase
import com.familygames.presentation.games.puzzle.CoilPuzzleImageLoader
import com.familygames.presentation.games.puzzle.PuzzleImageLoader
import com.familygames.presentation.games.puzzle.PuzzleSetupViewModel
import com.familygames.presentation.games.puzzle.SnapPuzzleViewModel
import com.familygames.presentation.games.puzzle.SwapPuzzleViewModel
import com.familygames.domain.model.MemoryLevel
import com.familygames.presentation.common.SoundPlayer
import com.familygames.presentation.common.SoundPoolPlayer
import com.familygames.presentation.common.AndroidVoicePrompts
import com.familygames.presentation.common.VoicePrompts
import com.familygames.presentation.common.NotePlayer
import com.familygames.presentation.common.SoundPoolNotePlayer
import com.familygames.presentation.common.ThumbLoader
import com.familygames.presentation.common.CoilThumbLoader
import com.familygames.data.local.files.DrawingStore
import com.familygames.presentation.games.common.FreePlayClock
import com.familygames.presentation.games.common.GamePhotos
import com.familygames.presentation.games.memory.MemoryHistory
import com.familygames.presentation.games.memory.MemoryLevelViewModel
import com.familygames.presentation.games.memory.MemoryViewModel
import com.familygames.presentation.menu.MainMenuViewModel
import com.familygames.domain.model.GameId
import com.familygames.domain.game.maze.TrackFactory
import com.familygames.domain.game.shadow.ShadowDealer
import com.familygames.domain.game.round.CountingRoundGenerator
import com.familygames.domain.game.round.OddOneOutRoundGenerator
import com.familygames.domain.game.round.WhereIsRoundGenerator
import com.familygames.presentation.games.bubbles.BubblesViewModel
import com.familygames.presentation.games.coloring.ColoringViewModel
import com.familygames.presentation.games.maze.MazeViewModel
import com.familygames.presentation.games.find.FindViewModel
import com.familygames.presentation.games.marble.MarblesViewModel
import com.familygames.presentation.games.shadow.ShadowsViewModel
import com.familygames.presentation.games.shadow.SilhouetteLoader
import com.familygames.presentation.games.shadow.ThumbSilhouetteLoader
import com.familygames.presentation.games.common.LevelPickViewModel
import com.familygames.presentation.games.counting.CountingViewModel
import com.familygames.presentation.games.oddone.OddOneOutViewModel
import com.familygames.presentation.games.peekaboo.PeekabooViewModel
import com.familygames.presentation.games.scratch.ScratchViewModel
import com.familygames.presentation.games.sequence.SequencePickViewModel
import com.familygames.presentation.games.sequence.SequenceViewModel
import com.familygames.presentation.games.sorting.SortingViewModel
import com.familygames.presentation.games.whereis.WhereIsViewModel
import com.familygames.presentation.games.xylophone.XylophoneViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import kotlin.random.Random
import java.security.SecureRandom
import com.familygames.domain.repository.CategoryRepository
import com.familygames.domain.repository.GameResultRepository
import com.familygames.domain.repository.PhotoRepository
import com.familygames.domain.repository.SettingsRepository
import com.familygames.domain.repository.SequenceRepository
import com.familygames.data.repository.SequenceRepositoryImpl
import com.familygames.util.Clock
import com.familygames.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

// Koin modules (docs/01 §6). ADR-001: KoinModulesTest verifies the graph.

val APP_SCOPE = named("appScope")

val appModule = module {
    single<Clock> { Clock.System }
    single { DispatcherProvider() }
    single<CoroutineScope>(APP_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { KioskManager(androidContext()) }
}

val dataModule = module {
    single { FamilyGamesDatabase.build(androidContext()) }
    single { get<FamilyGamesDatabase>().categoryDao() }
    single { get<FamilyGamesDatabase>().photoDao() }
    single { get<FamilyGamesDatabase>().gameResultDao() }
    single { get<FamilyGamesDatabase>().sequenceDao() }
    single {
        val context: Context = androidContext()
        SettingsDataStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(SettingsDataStore.FILE_NAME) })
    }
    single { MediaStorage(androidContext().filesDir, androidContext().cacheDir, get()) }
    single { PhotoImporter(androidContext(), get(), get()) }

    single<CategoryRepository> { CategoryRepositoryImpl(get(), get(), get(), get(), get()) }
    single<PhotoRepository> { PhotoRepositoryImpl(get(), get(), get()) }
    single<GameResultRepository> { GameResultRepositoryImpl(get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    single<SequenceRepository> { SequenceRepositoryImpl(get(), get(), get(), get(), get()) }

    single { DefaultContentSeeder(androidContext().resources, get(), get(), get(), get(), get()) }
    single { AppInitializer(get(), get(), get(), get(), get(), get(APP_SCOPE), get()) }

    single { OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build() }
    single { GitHubReleaseApi(get(), BuildConfig.UPDATE_REPO_OWNER, BuildConfig.UPDATE_REPO_NAME, "FamilyGames/${BuildConfig.VERSION_NAME}") }
    single<PackageArchiveReader> { AndroidPackageArchiveReader(androidContext()) }
    single { ApkVerifier(get()) }
    single<ApkInstaller> { PackageInstallerApkInstaller(androidContext()) }
    single {
        UpdateManager(get(), get(), get(), get(), get(), File(androidContext().cacheDir, "updates"), BuildConfig.VERSION_CODE.toLong(), Build.VERSION.SDK_INT)
    }
    single { UpdateScheduler(androidContext(), get()) }
}

val domainModule = module {
    factory { DealMemoryCardsUseCase(get()) }
    factory { ShuffleSwapBoardUseCase(get()) }
    factory { ParentChallengeGenerator(get()) }
    factory { WhereIsRoundGenerator(get()) }
    factory { OddOneOutRoundGenerator(get()) }
    factory { CountingRoundGenerator(get()) }
    factory { TrackFactory(get()) }
    factory { ShadowDealer(get()) }
    single<SilhouetteLoader> { ThumbSilhouetteLoader(get(), get()) }
    // Seeded once from SecureRandom: Random.Default can repeat the same sequence in every process forked from zygote.
    single<Random> { Random(SecureRandom().nextLong()) }
    single { PinHasher() }
    single { ParentAuthenticator(get(), get(), get()) }
}

val presentationModule = module {
    single<SoundPlayer> { SoundPoolPlayer(androidContext(), get(), get(APP_SCOPE)) }
    single<VoicePrompts> { AndroidVoicePrompts(androidContext(), get()) }
    single<NotePlayer> { SoundPoolNotePlayer(androidContext(), get(), get(APP_SCOPE)) }
    single<ThumbLoader> { CoilThumbLoader(androidContext()) }
    single { DrawingStore(androidContext().filesDir, get()) }
    factory { GamePhotos(get(), get()) }
    factory { FreePlayClock(get(), get()) }
    single { MemoryHistory() }
    single { ParentSession(get()) }
    single<PuzzleImageLoader> { CoilPuzzleImageLoader(androidContext()) }
}

val viewModelModule = module {
    viewModelOf(::MainMenuViewModel)
    viewModelOf(::MemoryLevelViewModel)
    viewModel { (level: MemoryLevel, categoryId: Long) ->
        MemoryViewModel(level, categoryId, get(), get(), get(), get(), get(), get(), get())
    }
    // docs/12 · new games
    viewModel { (level: MemoryLevel, categoryId: Long, sound: Boolean) ->
        MemoryViewModel(level, categoryId, get(), get(), get(), get(), get(), get(), get(), sound, get(), androidContext().resources)
    }
    viewModel { (game: GameId) -> LevelPickViewModel(game, get(), get(), get()) }
    viewModel { (level: String, categoryId: Long) ->
        WhereIsViewModel(level, categoryId, get(), get(), androidContext().resources, get(), get(), get())
    }
    viewModel { (level: String) -> OddOneOutViewModel(level, get(), get(), androidContext().resources, get(), get(), get()) }
    viewModel { (level: String) -> CountingViewModel(level, get(), get(), androidContext().resources, get(), get(), get()) }
    viewModel { (level: String) -> SortingViewModel(level, get(), get(), get(), get()) }
    viewModelOf(::SequencePickViewModel)
    viewModel { (sequenceId: Long) -> SequenceViewModel(sequenceId, get(), get(), get(), get(), get(), get()) }
    viewModel { PeekabooViewModel(get(), get(), androidContext().resources, get(), get()) }
    viewModel { ScratchViewModel(get(), get(), get(), get(), get()) }
    viewModel { BubblesViewModel(get(), get(), get(), get(), get()) }
    viewModel { XylophoneViewModel(get(), get(), androidContext().resources, get()) }
    viewModel { ColoringViewModel(get(), get(), get(), get()) }
    viewModel { (level: String) -> MazeViewModel(level, get(), get(), androidContext().resources, get(), get(), get(), get()) }
    viewModel { (level: String) -> ShadowsViewModel(level, get(), get(), get(), get(), get(), get()) }
    viewModel { (level: String) -> MarblesViewModel(level, get(), get(), get()) }
    viewModel { (level: String) -> FindViewModel(level, get(), androidContext().resources, get(), get(), get(), get()) }
    viewModelOf(::PuzzleSetupViewModel)
    viewModel { (purpose: GatePurpose) -> ParentGateViewModel(purpose, get(), get(), get(), get()) }
    viewModel {
        val kiosk = get<KioskManager>()
        FirstRunViewModel(get(), get(), kiosk.isDeviceOwner, kiosk.provisioningCommand)
    }
    viewModelOf(::PhotoManagerViewModel)
    viewModelOf(::CategoriesViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::ProgressViewModel)
    viewModelOf(::GamesViewModel)
    viewModelOf(::SequencesViewModel)
    viewModel { (photoId: Long?, uris: List<String>, categoryId: Long?) ->
        PhotoEditViewModel(photoId, uris, categoryId, get(), get(), get(), get())
    }
    viewModel { (photoId: Long, rows: Int, cols: Int) ->
        SnapPuzzleViewModel(photoId, rows, cols, get(), get(), get(), get(), get())
    }
    viewModel { (photoId: Long, size: Int) ->
        SwapPuzzleViewModel(photoId, size, get(), get(), get(), get(), get())
    }
}

val appModules: List<Module> = listOf(appModule, dataModule, domainModule, presentationModule, viewModelModule)
