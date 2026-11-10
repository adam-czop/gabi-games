package com.familygames.presentation.parent.progress

import com.familygames.domain.model.GameKind
import com.familygames.domain.model.GameResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ProgressTest {
    private val zone = TimeZone.getTimeZone("Europe/Warsaw")

    private fun at(y: Int, m: Int, d: Int, h: Int): Long =
        Calendar.getInstance(zone).apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis

    @Test
    fun `week starts on monday, today at midnight`() {
        val wednesday = at(2026, 11, 11, 15)
        val (today, week) = periodStarts(wednesday, zone)
        assertEquals(at(2026, 11, 11, 0), today)
        assertEquals(at(2026, 11, 9, 0), week)
        val (_, sundayWeek) = periodStarts(at(2026, 11, 15, 10), zone)
        assertEquals(at(2026, 11, 9, 0), sundayWeek)
    }

    @Test
    fun `summary counts games, minutes and best stars`() {
        fun r(at: Long, ms: Long) = GameResult(GameKind.MEMORY, "memory_2x2", stars = 3, moves = 2, mistakes = 0, hintsUsed = 0, durationMs = ms, finishedAt = at)
        val today = at(2026, 11, 11, 0)
        val s = summarize(
            listOf(r(today + 1_000, 90_000), r(today - 1_000, 150_000)),
            today,
            mapOf(GameKind.MEMORY to mapOf("memory_2x2" to 3), GameKind.PUZZLE_SWAP to mapOf("swap_3x3" to 1)),
        )
        assertEquals(1, s.gamesToday)
        assertEquals(2, s.gamesWeek)
        assertEquals(4L, s.minutesWeek)
        assertEquals(3, s.levels.first().stars)
        assertEquals(1, s.levels.last().stars)
        assertEquals(8, s.levels.size)
    }
}
