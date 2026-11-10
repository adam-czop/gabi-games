package com.familygames.presentation.games

import com.familygames.domain.model.AgeBand
import com.familygames.domain.model.AppSettings
import com.familygames.domain.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRegistryTest {

    @Test
    fun `every game is registered once`() {
        assertEquals(GameId.entries.toList(), GameRegistry.games.map { it.id })
    }

    @Test
    fun `coming soon tile fills the row only below three games`() {
        assertTrue(GameRegistry.showComingSoonTile(2))
        assertFalse(GameRegistry.showComingSoonTile(3))
    }

    @Test
    fun `preschool band hides peekaboo, toddler band hides the 3-5 games`() {
        val preschool = GameRegistry.visible(AppSettings(childAgeBand = AgeBand.PRESCHOOL)).map { it.id }
        assertFalse(GameId.PEEKABOO in preschool)
        assertTrue(GameId.COUNTING in preschool)

        val toddler = GameRegistry.visible(AppSettings(childAgeBand = AgeBand.TODDLER)).map { it.id }
        assertTrue(GameId.PEEKABOO in toddler)
        assertFalse(GameId.COUNTING in toddler)
        assertFalse(GameId.ODD_ONE_OUT in toddler)
    }

    @Test
    fun `hidden, forced and unavailable games`() {
        val settings = AppSettings(childAgeBand = AgeBand.PRESCHOOL, hiddenGames = setOf("MEMORY"), forcedGames = setOf("PEEKABOO"))
        val ids = GameRegistry.visible(settings, unavailable = setOf(GameId.SEQUENCE)).map { it.id }
        assertFalse(GameId.MEMORY in ids)
        assertTrue(GameId.PEEKABOO in ids)
        assertFalse(GameId.SEQUENCE in ids)
    }
}
