package com.familygames.presentation.games

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.familygames.R
import com.familygames.domain.model.AgeRange
import com.familygames.domain.model.AppSettings
import com.familygames.domain.model.GameId
import com.familygames.domain.model.GameVisibility
import com.familygames.domain.model.PlayStyle
import com.familygames.presentation.navigation.BubblesRoute
import com.familygames.presentation.navigation.ColoringRoute
import com.familygames.presentation.navigation.GameLevelsRoute
import com.familygames.presentation.navigation.MemoryLevelsRoute
import com.familygames.presentation.navigation.PeekabooRoute
import com.familygames.presentation.navigation.PuzzleSetupRoute
import com.familygames.presentation.navigation.ScratchRoute
import com.familygames.presentation.navigation.SequencePickRoute
import com.familygames.presentation.navigation.XylophoneRoute
import com.familygames.presentation.theme.Palette

/** A game shown in S01/S16 and listed in S17. A new game = new package + one entry here (docs/01 §5, docs/12). */
data class GameEntry(
    val id: GameId,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    @param:DrawableRes val icon: Int,
    val age: AgeRange,
    val style: PlayStyle,
    val entryRoute: Any,
)

object GameRegistry {
    val games: List<GameEntry> = listOf(
        GameEntry(GameId.MEMORY, R.string.game_memory, R.string.game_desc_memory, R.drawable.ic_game_memory, AgeRange(2f, 5f), PlayStyle.TASK, MemoryLevelsRoute),
        GameEntry(GameId.PUZZLE, R.string.game_puzzle, R.string.game_desc_puzzle, R.drawable.ic_game_puzzle, AgeRange(2f, 5f), PlayStyle.TASK, PuzzleSetupRoute),
        GameEntry(GameId.WHERE_IS, R.string.game_where_is, R.string.game_desc_where_is, R.drawable.ic_game_where, AgeRange(1.5f, 3f), PlayStyle.TASK, GameLevelsRoute(GameId.WHERE_IS)),
        GameEntry(GameId.PEEKABOO, R.string.game_peekaboo, R.string.game_desc_peekaboo, R.drawable.ic_game_peek, AgeRange(1f, 2f), PlayStyle.FREE_PLAY, PeekabooRoute),
        GameEntry(GameId.SCRATCH, R.string.game_scratch, R.string.game_desc_scratch, R.drawable.ic_game_scratch, AgeRange(1.5f, 3f), PlayStyle.FREE_PLAY, ScratchRoute),
        GameEntry(GameId.SORTING, R.string.game_sorting, R.string.game_desc_sorting, R.drawable.ic_game_sort, AgeRange(2.5f, 4f), PlayStyle.TASK, GameLevelsRoute(GameId.SORTING)),
        GameEntry(GameId.ODD_ONE_OUT, R.string.game_odd_one_out, R.string.game_desc_odd_one_out, R.drawable.ic_game_odd, AgeRange(3f, 5f), PlayStyle.TASK, GameLevelsRoute(GameId.ODD_ONE_OUT)),
        GameEntry(GameId.SOUND_MEMO, R.string.game_sound_memo, R.string.game_desc_sound_memo, R.drawable.ic_game_sound_memo, AgeRange(2f, 4f), PlayStyle.TASK, GameLevelsRoute(GameId.SOUND_MEMO)),
        GameEntry(GameId.BUBBLES, R.string.game_bubbles, R.string.game_desc_bubbles, R.drawable.ic_game_bubbles, AgeRange(1f, 3f), PlayStyle.FREE_PLAY, BubblesRoute),
        GameEntry(GameId.XYLOPHONE, R.string.game_xylophone, R.string.game_desc_xylophone, R.drawable.ic_game_xylophone, AgeRange(1.5f, 4f), PlayStyle.FREE_PLAY, XylophoneRoute),
        GameEntry(GameId.SEQUENCE, R.string.game_sequence, R.string.game_desc_sequence, R.drawable.ic_game_sequence, AgeRange(3f, 5f), PlayStyle.TASK, SequencePickRoute),
        GameEntry(GameId.COUNTING, R.string.game_counting, R.string.game_desc_counting, R.drawable.ic_game_counting, AgeRange(3f, 5f), PlayStyle.TASK, GameLevelsRoute(GameId.COUNTING)),
        GameEntry(GameId.COLORING, R.string.game_coloring, R.string.game_desc_coloring, R.drawable.ic_game_coloring, AgeRange(2.5f, 5f), PlayStyle.FREE_PLAY, ColoringRoute),
        GameEntry(GameId.MAZE, R.string.game_maze, R.string.game_desc_maze, R.drawable.ic_game_maze, AgeRange(2.5f, 5f), PlayStyle.TASK, GameLevelsRoute(GameId.MAZE)),
        GameEntry(GameId.SHADOWS, R.string.game_shadows, R.string.game_desc_shadows, R.drawable.ic_game_shadows, AgeRange(2f, 4.5f), PlayStyle.TASK, GameLevelsRoute(GameId.SHADOWS)),
        GameEntry(GameId.MARBLES, R.string.game_marbles, R.string.game_desc_marbles, R.drawable.ic_game_marbles, AgeRange(2.5f, 5f), PlayStyle.TASK, GameLevelsRoute(GameId.MARBLES)),
        GameEntry(GameId.FIND, R.string.game_find, R.string.game_desc_find, R.drawable.ic_game_find, AgeRange(2f, 5f), PlayStyle.TASK, GameLevelsRoute(GameId.FIND)),
        GameEntry(GameId.MISSING, R.string.game_missing, R.string.game_desc_missing, R.drawable.ic_game_missing, AgeRange(3f, 5f), PlayStyle.TASK, GameLevelsRoute(GameId.MISSING)),
        GameEntry(GameId.HALVES, R.string.game_halves, R.string.game_desc_halves, R.drawable.ic_game_halves, AgeRange(2.5f, 4.5f), PlayStyle.TASK, GameLevelsRoute(GameId.HALVES)),
    )

    fun entry(id: GameId): GameEntry = games.first { it.id == id }

    /**
     * Games shown in the menu (docs/12 M10.2): age band, hidden and forced games from S17. [unavailable] are games
     * without the content they need (e.g. "Po kolei" without any sequence), which the child never sees.
     */
    fun visible(settings: AppSettings, unavailable: Set<GameId> = emptySet()): List<GameEntry> = games.filter {
        it.id !in unavailable && GameVisibility.isVisible(it.id, it.age, settings.childAgeBand, settings.hiddenGames, settings.forcedGames)
    }

    /** The "Wkrótce" tile fills the row while fewer than 3 games are shown (docs/12 M10.1). */
    fun showComingSoonTile(visibleGames: Int): Boolean = visibleGames < 3

    /** Icon circles alternate AccentSoft / MistSoft (docs/08 §7) – no new colours. */
    fun circleColors(index: Int): Pair<Color, Color> =
        if (index % 2 == 0) Palette.AccentSoft to Palette.AccentInk else Palette.MistSoft to Palette.Mist
}
