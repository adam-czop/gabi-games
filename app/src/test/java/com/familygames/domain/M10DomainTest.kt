package com.familygames.domain

import com.familygames.domain.game.round.AnswerOutcome
import com.familygames.domain.game.round.Round
import com.familygames.domain.game.round.RoundEngine
import com.familygames.domain.game.round.RoundPhase
import com.familygames.domain.game.round.RoundScoring
import com.familygames.domain.model.AgeBand
import com.familygames.domain.model.AgeRange
import com.familygames.domain.model.GameId
import com.familygames.domain.model.GameVisibility
import com.familygames.domain.voice.PromptSources
import com.familygames.domain.voice.VoicePlan
import com.familygames.domain.voice.VoiceStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameVisibilityTest {
    private val memory = AgeRange(2f, 5f)
    private val peekaboo = AgeRange(1f, 2f)
    private val sorting = AgeRange(2.5f, 4f)

    @Test
    fun `age ranges touching the band count as fitting`() {
        assertTrue(memory.fits(AgeBand.TODDLER) && memory.fits(AgeBand.PRESCHOOL))
        assertTrue(peekaboo.fits(AgeBand.TODDLER))
        assertFalse(peekaboo.fits(AgeBand.PRESCHOOL))
        assertTrue(sorting.fits(AgeBand.TODDLER)) // 2,5 touches the toddler band like in the S17 mockup
    }

    @Test
    fun `hidden wins, forced overrides age, otherwise age decides`() {
        assertFalse(GameVisibility.isVisible(GameId.MEMORY, memory, AgeBand.TODDLER, hidden = setOf("MEMORY"), forced = setOf("MEMORY")))
        assertTrue(GameVisibility.isVisible(GameId.PEEKABOO, peekaboo, AgeBand.PRESCHOOL, hidden = emptySet(), forced = setOf("PEEKABOO")))
        assertFalse(GameVisibility.isVisible(GameId.PEEKABOO, peekaboo, AgeBand.PRESCHOOL, emptySet(), emptySet()))
    }

    @Test
    fun `switch hides or forces as needed`() {
        val (hidden, forced) = GameVisibility.toggle(GameId.PEEKABOO, on = true, peekaboo, AgeBand.PRESCHOOL, setOf("PEEKABOO"), emptySet())
        assertEquals(emptySet<String>(), hidden)
        assertEquals(setOf("PEEKABOO"), forced)
        val (h2, f2) = GameVisibility.toggle(GameId.PEEKABOO, on = false, peekaboo, AgeBand.PRESCHOOL, hidden, forced)
        assertEquals(setOf("PEEKABOO"), h2)
        assertEquals(emptySet<String>(), f2)
        val (_, f3) = GameVisibility.toggle(GameId.MEMORY, on = true, memory, AgeBand.PRESCHOOL, emptySet(), emptySet())
        assertEquals(emptySet<String>(), f3) // fits the age: no need to force
    }
}

class RoundEngineTest {
    private fun engine() = RoundEngine(listOf(Round(listOf("a", "b"), 1), Round(listOf("c", "d", "e"), 0)))

    @Test
    fun `right answers advance and the last one wins`() {
        val e = engine()
        assertEquals(AnswerOutcome.Correct(lastRound = false), e.answer(1))
        assertEquals(AnswerOutcome.Ignored, e.answer(1)) // locked during feedback
        e.next()
        assertEquals("c", e.current.correct)
        assertEquals(AnswerOutcome.Correct(lastRound = true), e.answer(0))
        assertEquals(RoundPhase.Won, e.phase)
        assertEquals(AnswerOutcome.Ignored, e.answer(0))
    }

    @Test
    fun `mistakes count per game and per round`() {
        val e = engine()
        assertEquals(AnswerOutcome.Wrong(1), e.answer(0))
        assertEquals(AnswerOutcome.Wrong(2), e.answer(0))
        e.answer(1); e.next()
        assertEquals(0, e.mistakesInRound)
        assertEquals(2, e.mistakes)
        assertEquals(AnswerOutcome.Ignored, e.answer(7))
    }

    @Test
    fun `scoring thresholds`() {
        assertEquals(3, RoundScoring.stars(rounds = 6, mistakes = 0))
        assertEquals(2, RoundScoring.stars(rounds = 6, mistakes = 3))
        assertEquals(1, RoundScoring.stars(rounds = 6, mistakes = 4))
    }
}

class VoicePlanTest {
    private val base = PromptSources(null, null, null, "babcia", "Gdzie jest", ttsAvailable = false)

    @Test
    fun `priority of voice sources`() {
        assertEquals(listOf(VoiceStep.Clip("q.m4a")), VoicePlan.build(base.copy(questionClip = "q.m4a", prefixClip = "p", labelClip = "l", ttsAvailable = true)))
        assertEquals(listOf(VoiceStep.Clip("p"), VoiceStep.Clip("l")), VoicePlan.build(base.copy(prefixClip = "p", labelClip = "l", ttsAvailable = true)))
        assertEquals(listOf(VoiceStep.Speech("Gdzie jest babcia?")), VoicePlan.build(base.copy(ttsAvailable = true)))
        assertEquals(emptyList<VoiceStep>(), VoicePlan.build(base))
    }

    @Test
    fun `label after a right answer`() {
        assertEquals(listOf(VoiceStep.Clip("l")), VoicePlan.label("l", "babcia", ttsAvailable = true))
        assertEquals(listOf(VoiceStep.Speech("babcia")), VoicePlan.label(null, "babcia", ttsAvailable = true))
        assertEquals(emptyList<VoiceStep>(), VoicePlan.label(null, "babcia", ttsAvailable = false))
    }
}
