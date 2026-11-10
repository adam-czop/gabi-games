package com.familygames.domain.parent

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.math.min
import kotlin.random.Random

/** "a × b = ?" with four distinct positive answers, one of them correct (docs/06 §4). */
data class Challenge(val a: Int, val b: Int, val options: List<Int>) {
    val correct: Int get() = a * b
}

class ParentChallengeGenerator(private val random: Random) {
    fun next(): Challenge {
        val a = random.nextInt(3, 10)
        val b = random.nextInt(3, 10)
        val answer = a * b
        val distractors = linkedSetOf(a * (b + 1), a * (b - 1), (a + 1) * b, (a - 1) * b, answer + 2, answer - 2)
            .filter { it > 0 && it != answer }
            .shuffled(random)
            .take(3)
        return Challenge(a, b, (distractors + answer).shuffled(random))
    }
}

/** PBKDF2-HMAC-SHA256, 120 000 iterations, 16 B salt, 32 B hash, Base64 (docs/06 §4). */
class PinHasher(private val iterations: Int = 120_000) {
    private val secureRandom = SecureRandom()

    fun newSalt(): String = Base64.getEncoder().encodeToString(ByteArray(16).also(secureRandom::nextBytes))

    fun hash(secret: String, salt: String): String {
        val spec = PBEKeySpec(secret.toCharArray(), Base64.getDecoder().decode(salt), iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return Base64.getEncoder().encodeToString(bytes)
        } finally {
            spec.clearPassword()
        }
    }

    /** Constant-time comparison. */
    fun verify(secret: String, salt: String?, expectedHash: String?): Boolean {
        if (salt == null || expectedHash == null) return false
        return MessageDigest.isEqual(hash(secret, salt).toByteArray(), expectedHash.toByteArray())
    }

    companion object {
        fun isValidPin(pin: String): Boolean = pin.length == 4 && pin.all { it.isDigit() }
    }
}

/** One-time 12-character recovery code `XXXX-XXXX-XXXX`, without the look-alikes 0/O/1/I (docs/06 §4). */
object RecoveryCode {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generate(random: java.util.Random = SecureRandom()): String =
        (1..12).map { ALPHABET[random.nextInt(ALPHABET.length)] }.chunked(4).joinToString("-") { it.joinToString("") }

    /** What is hashed and compared: upper case, no separators or spaces. */
    fun normalize(input: String): String = input.uppercase().filter { it in ALPHABET }
}

/** Persisted gate state: failures in the current series, how many lockouts happened, and until when. */
data class GateState(val failures: Int = 0, val lockoutLevel: Int = 0, val lockedUntil: Long = 0) {
    fun isLocked(now: Long): Boolean = now < lockedUntil
}

/** 3 wrong answers → 60 s lock; every further series doubles it, up to 15 min. Success resets (docs/06 §4). */
object GateLockoutPolicy {
    const val ATTEMPTS = 3
    const val BASE_LOCK_MS = 60_000L
    const val MAX_LOCK_MS = 15 * 60_000L

    fun onFailure(state: GateState, now: Long): GateState {
        val failures = state.failures + 1
        if (failures < ATTEMPTS) return state.copy(failures = failures)
        val lock = min(BASE_LOCK_MS shl state.lockoutLevel.coerceAtMost(10), MAX_LOCK_MS)
        return GateState(failures = 0, lockoutLevel = state.lockoutLevel + 1, lockedUntil = now + lock)
    }

    fun onSuccess(): GateState = GateState()

    fun attemptsLeft(state: GateState): Int = ATTEMPTS - state.failures
}
