package com.familygames.di

import android.content.Context
import android.content.res.Resources
import androidx.datastore.core.DataStore
import com.familygames.domain.model.MemoryLevel
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify
import java.io.File

/** ADR-001: Koin resolves the graph at runtime, so the whole graph is verified here. */
@OptIn(KoinExperimentalAPI::class)
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
}
