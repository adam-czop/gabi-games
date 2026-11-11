package com.familygames.domain.game.sorter

import com.familygames.domain.game.board.DropOutcome
import kotlin.random.Random

/** Shapes of the sorter box, from the easiest to tell apart (docs/12 M26). */
enum class BlockShape { CIRCLE, SQUARE, TRIANGLE, STAR, HEART, MOON }

/** Levels of "Klocki do dziurek": holes per round and which shapes may appear. */
enum class SorterLevel(val key: String, val holes: Int, val shapes: List<BlockShape>, val rounds: Int = 3) {
    TWO("sorter_2", 2, listOf(BlockShape.CIRCLE, BlockShape.SQUARE, BlockShape.TRIANGLE)),
    THREE("sorter_3", 3, listOf(BlockShape.CIRCLE, BlockShape.SQUARE, BlockShape.TRIANGLE, BlockShape.STAR)),
    FOUR("sorter_4", 4, BlockShape.entries),
    ;

    companion object {
        fun fromKey(key: String): SorterLevel = entries.firstOrNull { it.key == key } ?: TWO
    }
}

/** A block in the tray: its shape and one of the four block colours. */
data class Block(val shape: BlockShape, val color: Int)

/** One round: the holes in the lid and the blocks in the tray (another order, never right under their hole). */
data class SorterRound(val holes: List<BlockShape>, val tray: List<Block>)

class SorterDealer(private val random: Random) {
    fun deal(level: SorterLevel): List<SorterRound> {
        var previous: Set<BlockShape> = emptySet()
        return List(level.rounds) {
            // A new mix every round when the level has enough shapes.
            val fresh = level.shapes.filter { it !in previous }.shuffled(random)
            val holes = (fresh + level.shapes.filter { it in previous }.shuffled(random)).take(level.holes).shuffled(random)
            previous = holes.toSet()
            val colors = (0 until COLORS).shuffled(random)
            val blocks = holes.mapIndexed { i, s -> Block(s, colors[i % COLORS]) }
            SorterRound(holes, shuffledApart(blocks, holes))
        }
    }

    private fun shuffledApart(blocks: List<Block>, holes: List<BlockShape>): List<Block> {
        repeat(20) {
            val tray = blocks.shuffled(random)
            if (tray.indices.none { tray[it].shape == holes[it] }) return tray
        }
        return blocks.drop(1) + blocks.first()
    }

    companion object {
        const val COLORS = 4
    }
}

/** The lid of one round: each hole takes only its own shape. */
class SorterBoard(val holes: List<BlockShape>) {
    private val filled = BooleanArray(holes.size)
    var mistakes = 0
        private set

    val done: List<Boolean> get() = filled.toList()

    fun drop(shape: BlockShape, hole: Int): DropOutcome {
        if (hole !in holes.indices || filled[hole] || holes.indices.any { holes[it] == shape && filled[it] }) return DropOutcome.Ignored
        if (holes[hole] != shape) {
            mistakes++
            return DropOutcome.Wrong
        }
        filled[hole] = true
        return DropOutcome.Correct(won = filled.all { it })
    }

    fun holeOf(shape: BlockShape): Int = holes.indexOf(shape)
}
