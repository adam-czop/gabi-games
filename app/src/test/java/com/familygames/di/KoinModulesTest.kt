package com.familygames.di

import android.content.Context
import android.content.res.Resources
import androidx.datastore.core.DataStore
import androidx.test.core.app.ApplicationProvider
import com.familygames.domain.model.MemoryLevel
import com.familygames.presentation.games.memory.MemoryViewModel
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import org.robolectric.RobolectricTestRunner
import java.io.File

/** ADR-001: Koin resolves the graph at runtime, so the whole graph is verified here. */
@OptIn(KoinExperimentalAPI::class)
@RunWith(RobolectricTestRunner::class)
class KoinModulesTest {

    @Test
    fun `all app modules resolve their dependencies`() {
        module { includes(appModules) }.verify(
            extraTypes = listOf(
                Context::class,
                Resources::class,
                File::class,
                DataStore::class,
                CoroutineDispatcher::class,
                // ViewModel parameters from the route and default constructor arguments
                MemoryLevel::class,
                Long::class,
                Int::class,
                Random::class,
                Boolean::class,
                String::class,
                okhttp3.HttpUrl::class,
                List::class,
                com.familygames.presentation.parent.gate.GatePurpose::class,
                com.familygames.domain.model.GameId::class,
            ),
        )
    }

    /**
     * Memory and Sound memo share MemoryViewModel with different parameter counts. verify() does not
     * notice when a second definition of the same type replaces the first, so both calls are made here.
     */
    @Test
    fun `memory view model resolves for memory and sound memo`() {
        val app = koinApplication {
            androidContext(ApplicationProvider.getApplicationContext())
            modules(appModules)
        }
        try {
            assertNotNull(app.koin.get<MemoryViewModel> { parametersOf(MemoryLevel.L1, 1L) })
            assertNotNull(app.koin.get<MemoryViewModel> { parametersOf(MemoryLevel.L1, -1L, true) })
        } finally {
            app.close()
        }
    }
}
