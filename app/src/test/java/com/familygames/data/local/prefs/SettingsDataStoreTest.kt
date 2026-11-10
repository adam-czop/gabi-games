package com.familygames.data.local.prefs

import com.familygames.data.testSettingsStore
import com.familygames.domain.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsDataStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    private val store by lazy { testSettingsStore(tmp.root.resolve("settings.preferences_pb"), scope) }

    @After fun tearDown() = scope.cancel()

    @Test
    fun `defaults match docs`() = runTest {
        assertEquals(AppSettings(), store.settings.first())
        assertEquals(0, store.seedVersion())
    }

    @Test
    fun `values round-trip and nullable keys can be cleared`() = runTest {
        store.update { it.copy(soundEnabled = false, lastMemoryCategory = 7, pinHash = "h", pinSalt = "s", updateEtag = "e") }
        val saved = store.settings.first()
        assertEquals(false, saved.soundEnabled)
        assertEquals(7L, saved.lastMemoryCategory)
        assertNotNull(saved.pinHash)

        store.update { it.copy(lastMemoryCategory = null, pinHash = null, pinSalt = null, updateEtag = null) }
        val cleared = store.settings.first()
        assertNull(cleared.lastMemoryCategory)
        assertNull(cleared.pinHash)
        assertNull(cleared.updateEtag)
        assertEquals(false, cleared.soundEnabled)
    }

    @Test
    fun `seed version is stored separately`() = runTest {
        store.setSeedVersion(2)
        assertEquals(2, store.seedVersion())
        assertEquals(AppSettings(), store.settings.first())
    }
}
