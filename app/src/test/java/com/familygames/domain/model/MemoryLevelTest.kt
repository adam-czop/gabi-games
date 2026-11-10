package com.familygames.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryLevelTest {

    @Test
    fun `grids match the spec and always hold whole pairs`() {
        assertEquals(listOf(2, 3, 4, 6), MemoryLevel.entries.map { it.pairs })
        MemoryLevel.entries.forEach { assertEquals(0, it.cards % 2) }
    }

    @Test
    fun `level keys round-trip`() {
        MemoryLevel.entries.forEach { assertEquals(it, MemoryLevel.fromKey(it.key)) }
        assertNull(MemoryLevel.fromKey("memory_9x9"))
    }
}
