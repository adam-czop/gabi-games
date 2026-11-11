package com.familygames.domain.model

/** Game identifier stored in `game_results.gameId` (docs/02 §1). */
enum class GameKind {
    MEMORY, PUZZLE_SNAP, PUZZLE_SWAP,
    WHERE_IS, PEEKABOO, SCRATCH, SORTING, ODD_ONE_OUT, SOUND_MEMO, BUBBLES, XYLOPHONE, SEQUENCE, COUNTING, COLORING, MAZE,
}

data class GameResult(
    val game: GameKind,
    /** e.g. `memory_2x3`, `snap_2x2`, `swap_3x3`. */
    val levelKey: String,
    val categoryId: Long? = null,
    val photoId: Long? = null,
    val stars: Int,
    val moves: Int,
    val mistakes: Int,
    val hintsUsed: Int,
    val durationMs: Long,
    val finishedAt: Long,
) {
    init {
        require(stars in 0..3) { "stars must be 0..3 (0 = free play), was $stars" }
    }
}

/** All preferences from docs/02 §2. */
data class AppSettings(
    val firstRunCompleted: Boolean = false,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val gateLockoutUntil: Long = 0,
    val gateFailures: Int = 0,
    val gateLockoutLevel: Int = 0,
    val recoveryHash: String? = null,
    val recoverySalt: String? = null,
    /** S11 "Zawsze wymagaj PIN": the PIN instead of the multiplication task also for entering the parent zone. */
    val alwaysRequirePin: Boolean = false,
    val soundEnabled: Boolean = true,
    val showCardLabels: Boolean = true,
    val levelLocksEnabled: Boolean = false,
    val lastMemoryLevel: String = MemoryLevel.L1.key,
    val lastMemoryCategory: Long? = null,
    val lastPuzzleDifficulty: String = "snap_2x2",
    val updateCheckOnStart: Boolean = true,
    val updateWifiOnly: Boolean = true,
    val updateLastCheckAt: Long = 0,
    val updateEtag: String? = null,
    val hideSystemBars: Boolean = true,
    val keepScreenOn: Boolean = false,
    /** docs/12 M10.2 — S17 "Gry". */
    val childAgeBand: AgeBand = AgeBand.PRESCHOOL,
    val hiddenGames: Set<String> = emptySet(),
    val forcedGames: Set<String> = emptySet(),
    /** Parent's recorded prompt prefixes (docs/12 M10.3): prefix key → relative audio path. */
    val prefixClips: Map<String, String> = emptyMap(),
)

/** Free play is stored with 0 stars and this level key so S14 can show the play time (docs/12 §0). */
const val FREE_PLAY_LEVEL = "free"
