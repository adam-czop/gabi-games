package com.familygames.domain.parent

import com.familygames.domain.model.AppSettings
import com.familygames.domain.repository.SettingsRepository
import com.familygames.util.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

sealed interface AuthResult {
    data object Success : AuthResult
    data class Wrong(val attemptsLeft: Int) : AuthResult
    data class Locked(val until: Long) : AuthResult
}

/**
 * Parent gate rules (docs/06 §4): PIN and recovery code stored only as PBKDF2 hashes, failures and the
 * lockout persisted in settings so restarting the app does not reset them.
 */
class ParentAuthenticator(
    private val settings: SettingsRepository,
    private val hasher: PinHasher,
    private val clock: Clock,
) {
    suspend fun hasPin(): Boolean = settings.settings.first().pinHash != null

    suspend fun gateState(): GateState = settings.settings.first().let {
        GateState(it.gateFailures, it.gateLockoutLevel, it.gateLockoutUntil)
    }

    /** Stores a new PIN and returns a fresh recovery code to show once. */
    suspend fun setPin(pin: String): String {
        require(PinHasher.isValidPin(pin)) { "PIN must be 4 digits" }
        val code = RecoveryCode.generate()
        val (pinSalt, pinHash, codeSalt, codeHash) = withContext(Dispatchers.Default) {
            val ps = hasher.newSalt()
            val cs = hasher.newSalt()
            listOf(ps, hasher.hash(pin, ps), cs, hasher.hash(RecoveryCode.normalize(code), cs))
        }
        settings.update {
            it.copy(pinHash = pinHash, pinSalt = pinSalt, recoveryHash = codeHash, recoverySalt = codeSalt, gateFailures = 0, gateLockoutLevel = 0, gateLockoutUntil = 0)
        }
        return code
    }

    suspend fun checkPin(pin: String): AuthResult = attempt { s ->
        withContext(Dispatchers.Default) { hasher.verify(pin, s.pinSalt, s.pinHash) }
    }

    suspend fun checkRecoveryCode(code: String): AuthResult = attempt { s ->
        withContext(Dispatchers.Default) { hasher.verify(RecoveryCode.normalize(code), s.recoverySalt, s.recoveryHash) }
    }

    /** The multiplication task: the caller already knows whether the tapped answer was right. */
    suspend fun checkChallenge(correct: Boolean): AuthResult = attempt { correct }

    private suspend fun attempt(check: suspend (AppSettings) -> Boolean): AuthResult {
        val s = settings.settings.first()
        val now = clock.now()
        val state = GateState(s.gateFailures, s.gateLockoutLevel, s.gateLockoutUntil)
        if (state.isLocked(now)) return AuthResult.Locked(state.lockedUntil)
        val next = if (check(s)) GateLockoutPolicy.onSuccess() else GateLockoutPolicy.onFailure(state, now)
        settings.update { it.copy(gateFailures = next.failures, gateLockoutLevel = next.lockoutLevel, gateLockoutUntil = next.lockedUntil) }
        return when {
            next == GateLockoutPolicy.onSuccess() -> AuthResult.Success
            next.isLocked(now) -> AuthResult.Locked(next.lockedUntil)
            else -> AuthResult.Wrong(GateLockoutPolicy.attemptsLeft(next))
        }
    }
}
