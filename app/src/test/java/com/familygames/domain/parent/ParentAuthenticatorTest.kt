package com.familygames.domain.parent

import com.familygames.domain.model.AppSettings
import com.familygames.domain.repository.SettingsRepository
import com.familygames.util.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSettings(initial: AppSettings = AppSettings()) : SettingsRepository {
    override val settings = MutableStateFlow(initial)
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        settings.value = transform(settings.value)
    }
}

class ParentAuthenticatorTest {
    private var now = 1_000_000L
    private val settings = FakeSettings()
    private val auth = ParentAuthenticator(settings, PinHasher(iterations = 1_000), Clock { now })

    @Test
    fun `pin is stored only as hash and verifies`() = runTest {
        val code = auth.setPin("2468")
        val s = settings.settings.value
        assertNotNull(s.pinHash)
        assertTrue(s.pinHash != "2468")
        assertTrue(code.matches(Regex("....-....-....")))
        assertEquals(AuthResult.Success, auth.checkPin("2468"))
    }

    @Test
    fun `three wrong pins lock, the lock is persisted and expires`() = runTest {
        auth.setPin("2468")
        assertEquals(AuthResult.Wrong(2), auth.checkPin("0000"))
        assertEquals(AuthResult.Wrong(1), auth.checkPin("0000"))
        assertEquals(AuthResult.Locked(now + 60_000), auth.checkPin("0000"))

        // A "restarted app" (new authenticator on the same settings) is still locked, even with the right PIN.
        val again = ParentAuthenticator(settings, PinHasher(iterations = 1_000), Clock { now })
        assertTrue(again.checkPin("2468") is AuthResult.Locked)

        now += 60_001
        assertEquals(AuthResult.Success, again.checkPin("2468"))
        assertEquals(0, settings.settings.value.gateFailures)
    }

    @Test
    fun `recovery code accepts any case and separators`() = runTest {
        val code = auth.setPin("1357")
        assertEquals(AuthResult.Success, auth.checkRecoveryCode(code.lowercase().replace("-", " ")))
        assertTrue(auth.checkRecoveryCode("AAAA-BBBB-CCCC") is AuthResult.Wrong)
    }

    @Test
    fun `wrong task answers count like wrong pins`() = runTest {
        repeat(2) { auth.checkChallenge(correct = false) }
        assertTrue(auth.checkChallenge(correct = false) is AuthResult.Locked)
        assertTrue(auth.checkChallenge(correct = true) is AuthResult.Locked)
    }
}
