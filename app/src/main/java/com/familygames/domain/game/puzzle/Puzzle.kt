package com.familygames.domain.game.puzzle

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.random.Random

enum class PuzzleMode { SNAP, SWAP }

/** The four difficulty buttons of S05 (docs/05). [key] is stored as `levelKey` and in settings. */
enum class PuzzleDifficulty(val mode: PuzzleMode, val rows: Int, val cols: Int, val key: String) {
    SNAP_2X2(PuzzleMode.SNAP, 2, 2, "snap_2x2"),
    SNAP_2X3(PuzzleMode.SNAP, 2, 3, "snap_2x3"),
    SWAP_2X2(PuzzleMode.SWAP, 2, 2, "swap_2x2"),
    SWAP_3X3(PuzzleMode.SWAP, 3, 3, "swap_3x3"),
    ;

    val pieces: Int get() = rows * cols

    companion object {
        fun fromKey(key: String): PuzzleDifficulty = entries.firstOrNull { it.key == key } ?: SNAP_2X2

        fun snap(rows: Int, cols: Int): PuzzleDifficulty =
            entries.firstOrNull { it.mode == PuzzleMode.SNAP && it.rows == rows && it.cols == cols } ?: SNAP_2X2

        fun swap(size: Int): PuzzleDifficulty =
            entries.firstOrNull { it.mode == PuzzleMode.SWAP && it.rows == size } ?: SWAP_2X2
    }
}

/** Tuning constants in dp (docs/05 §2). */
object PuzzleTuning {
    const val SNAP_DISTANCE_DP = 40f
    const val SNAP_DISTANCE_SMALL_BOARD_DP = 56f
    const val TRAY_SCALE = 0.7f
    const val HINT_DURATION_MS = 2000L
    const val HINT_COOLDOWN_MS = 3000L
    const val WIN_DELAY_MS = 400L
    const val SWAP_MS = 250

    fun snapDistanceDp(difficulty: PuzzleDifficulty): Float =
        if (difficulty.pieces <= 4) SNAP_DISTANCE_SMALL_BOARD_DP else SNAP_DISTANCE_DP
}

/** Magnet rule: the piece snaps when its centre is closer than [thresholdPx] to its own slot's centre. */
fun isWithinSnap(pieceX: Float, pieceY: Float, slotX: Float, slotY: Float, thresholdPx: Float): Boolean =
    hypot(pieceX - slotX, pieceY - slotY) < thresholdPx

/** docs/05 §2: wrong drops never cost stars — the child learns by trying. */
object SnapScoring {
    fun stars(hintsUsed: Int): Int = when (hintsUsed) {
        0 -> 3
        1 -> 2
        else -> 1
    }
}

/** `tiles[pos]` = the tile lying at position `pos`; solved when `tiles[i] == i` (docs/05 §3). */
data class SwapBoard(val size: Int, val tiles: List<Int>) {
    init {
        require(tiles.size == size * size && tiles.sorted() == List(size * size) { it }) { "Not a permutation: $tiles" }
    }

    fun isSolved(): Boolean = tiles.withIndex().all { (i, t) -> i == t }

    fun areAdjacent(a: Int, b: Int): Boolean {
        val (ra, ca) = a / size to a % size
        val (rb, cb) = b / size to b % size
        return abs(ra - rb) + abs(ca - cb) == 1
    }

    fun swap(a: Int, b: Int): SwapBoard {
        require(areAdjacent(a, b)) { "$a and $b are not neighbours" }
        return copy(tiles = tiles.toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t })
    }

    fun correctCount(): Int = tiles.withIndex().count { (i, t) -> i == t }

    fun isCorrect(pos: Int): Boolean = tiles[pos] == pos

    fun neighbours(pos: Int): List<Int> = tiles.indices.filter { areAdjacent(pos, it) }

    companion object {
        fun solved(size: Int) = SwapBoard(size, List(size * size) { it })
    }
}

/** Fisher–Yates until the board is unsolved with at least 60% of tiles misplaced (docs/05 §3). */
class ShuffleSwapBoardUseCase(private val random: Random) {
    operator fun invoke(size: Int): SwapBoard {
        val n = size * size
        val minMisplaced = ceil(0.6 * n).toInt()
        while (true) {
            val board = SwapBoard(size, List(n) { it }.shuffled(random))
            if (!board.isSolved() && n - board.correctCount() >= minMisplaced) return board
        }
    }
}

object SwapScoring {
    fun stars(moves: Int, misplacedAtStart: Int): Int = when {
        moves <= 2 * misplacedAtStart -> 3
        moves <= 4 * misplacedAtStart -> 2
        else -> 1
    }
}
