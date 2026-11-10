package com.familygames.domain.parent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ParentChallengeGeneratorTest {
    @Test
    fun `correct answer is always among 4 distinct positive options`() {
        val generator = ParentChallengeGenerator(Random(5))
        repeat(1000) {
            val c = generator.next()
            assertEquals(4, c.options.size)
            assertEquals(4, c.options.toSet().size)
            assertTrue(c.options.all { it > 0 })
            assertTrue(c.correct in c.options)
            assertTrue(c.a in 3..9 && c.b in 3..9)
        }
    }
}

class PinHasherTest {
    private val hasher = PinHasher(iterations = 1_000) // fast in tests; production uses 120 000

    @Test
    fun `same pin with different salts gives different hashes, verify works`() {
        val s1 = hasher.newSalt()
        val s2 = hasher.newSalt()
        assertNotEquals(s1, s2)
        val h1 = hasher.hash("1234", s1)
        assertNotEquals(h1, hasher.hash("1234", s2))
        assertTrue(hasher.verify("1234", s1, h1))
        assertFalse(hasher.verify("4321", s1, h1))
        assertFalse(hasher.verify("1234", null, h1))
    }

    @Test
    fun `pin format`() {
        assertTrue(PinHasher.isValidPin("0007"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("12a4"))
    }
}

class RecoveryCodeTest {
    @Test
    fun `format and alphabet`() {
        repeat(200) {
            val code = RecoveryCode.generate()
            assertTrue(code, Regex("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}").matches(code))
            assertFalse(code.any { it in "01OI" })
        }
        assertEquals("ABCD2345EFGH", RecoveryCode.normalize(" abcd-2345 efgh "))
    }
}

class GateLockoutPolicyTest {
    @Test
    fun `three failures lock for 60 s, next series doubles, capped at 15 min`() {
        var s = GateState()
        s = GateLockoutPolicy.onFailure(s, now = 0)
        s = GateLockoutPolicy.onFailure(s, now = 0)
        assertEquals(1, GateLockoutPolicy.attemptsLeft(s))
        assertFalse(s.isLocked(0))
        s = GateLockoutPolicy.onFailure(s, now = 1_000)
        assertEquals(61_000L, s.lockedUntil)
        assertTrue(s.isLocked(60_999))
        assertEquals(3, GateLockoutPolicy.attemptsLeft(s))

        repeat(3) { s = GateLockoutPolicy.onFailure(s, now = 100_000) }
        assertEquals(100_000L + 120_000L, s.lockedUntil)

        repeat(30) { s = GateLockoutPolicy.onFailure(s, now = 0) }
        assertEquals(GateLockoutPolicy.MAX_LOCK_MS, s.lockedUntil)
    }

    @Test
    fun `success resets`() {
        assertEquals(GateState(), GateLockoutPolicy.onSuccess())
    }
}
