package com.familygames.presentation.parent.progress

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.R
import com.familygames.domain.game.puzzle.PuzzleDifficulty
import com.familygames.domain.game.puzzle.PuzzleMode
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.GameResult
import com.familygames.domain.model.MemoryLevel
import com.familygames.domain.repository.GameResultRepository
import com.familygames.presentation.common.StarsRow
import com.familygames.presentation.parent.common.ParentHeader
import com.familygames.presentation.theme.Palette
import com.familygames.presentation.theme.ParentType
import com.familygames.util.Clock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import java.io.File
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import coil3.compose.AsyncImage
import com.familygames.data.local.files.DrawingStore
import com.familygames.domain.model.FREE_PLAY_LEVEL
import com.familygames.domain.model.GameId
import com.familygames.presentation.games.GameRegistry
import kotlinx.coroutines.launch
import java.util.TimeZone

data class LevelRow(@param:StringRes val game: Int, val label: String, val stars: Int)

/** docs/12 games this week: best stars of a task level, or minutes of free play ([stars] null). */
data class WeekRow(@param:StringRes val game: Int, val label: String?, val stars: Int?, val minutes: Long)

data class ProgressUiState(
    val gamesToday: Int = 0,
    val gamesWeek: Int = 0,
    val minutesWeek: Long = 0,
    val levels: List<LevelRow> = emptyList(),
    val week: List<WeekRow> = emptyList(),
    val drawings: List<File> = emptyList(),
)

private val OLD_GAMES = setOf(GameKind.MEMORY, GameKind.PUZZLE_SNAP, GameKind.PUZZLE_SWAP)

/** Rows for the new games played this week (docs/12 §0: free play shows only its time). */
internal fun weekRows(results: List<GameResult>): List<WeekRow> = results
    .filter { it.game !in OLD_GAMES }
    .groupBy { it.game to it.levelKey }
    .map { (key, list) ->
        val (game, level) = key
        val title = GameId.entries.firstOrNull { it.name == game.name }?.let { GameRegistry.entry(it).title } ?: R.string.app_name
        val free = level == FREE_PLAY_LEVEL
        WeekRow(title, if (free) null else level.substringAfter('_'), if (free) null else list.maxOf { it.stars }, list.sumOf { it.durationMs } / 60_000)
    }
    .sortedBy { it.game }

/** Start of today and of the current week (Monday) in the device time zone. */
internal fun periodStarts(now: Long, zone: TimeZone = TimeZone.getDefault()): Pair<Long, Long> {
    val c = Calendar.getInstance(zone).apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val today = c.timeInMillis
    val daysFromMonday = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
    c.add(Calendar.DAY_OF_YEAR, -daysFromMonday)
    return today to c.timeInMillis
}

internal fun summarize(results: List<GameResult>, today: Long, best: Map<GameKind, Map<String, Int>>): ProgressUiState {
    val levels = MemoryLevel.entries.map { LevelRow(R.string.game_memory, "${it.rows}×${it.cols}", best[GameKind.MEMORY]?.get(it.key) ?: 0) } +
        PuzzleDifficulty.entries.map {
            val kind = if (it.mode == PuzzleMode.SNAP) GameKind.PUZZLE_SNAP else GameKind.PUZZLE_SWAP
            LevelRow(if (it.mode == PuzzleMode.SNAP) R.string.progress_puzzle_snap else R.string.progress_puzzle_swap, "${it.rows}×${it.cols}", best[kind]?.get(it.key) ?: 0)
        }
    return ProgressUiState(
        gamesToday = results.count { it.finishedAt >= today },
        gamesWeek = results.size,
        minutesWeek = results.sumOf { it.durationMs } / 60_000,
        levels = levels,
    )
}

/** S14 · simple summary from `game_results`, no charts in v1 (docs/03). */
class ProgressViewModel(results: GameResultRepository, clock: Clock, private val drawings: DrawingStore) : ViewModel() {
    private val starts = periodStarts(clock.now())

    val uiState: StateFlow<ProgressUiState> = combine(
        results.observeSince(starts.second),
        results.observeBestStars(GameKind.MEMORY),
        results.observeBestStars(GameKind.PUZZLE_SNAP),
        results.observeBestStars(GameKind.PUZZLE_SWAP),
        drawings.drawings,
    ) { week, memory, snap, swap, files ->
        summarize(week, starts.first, mapOf(GameKind.MEMORY to memory, GameKind.PUZZLE_SNAP to snap, GameKind.PUZZLE_SWAP to swap))
            .copy(week = weekRows(week), drawings = files)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    init {
        viewModelScope.launch { drawings.refresh() }
    }

    fun deleteDrawing(file: File) {
        viewModelScope.launch { drawings.delete(file) }
    }
}

@Composable
fun ProgressScreen(state: ProgressUiState, onDeleteDrawing: (File) -> Unit = {}) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 40.dp, end = 40.dp, top = 32.dp, bottom = 32.dp),
    ) {
        ParentHeader(stringResource(R.string.nav_progress), stringResource(R.string.progress_subtitle))
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Stat(pluralStringResource(R.plurals.progress_games, state.gamesToday, state.gamesToday), stringResource(R.string.progress_today), Modifier.weight(1f))
            Stat(pluralStringResource(R.plurals.progress_games, state.gamesWeek, state.gamesWeek), stringResource(R.string.progress_week), Modifier.weight(1f))
            Stat(stringResource(R.string.progress_minutes, state.minutesWeek), stringResource(R.string.progress_time_week), Modifier.weight(1f))
        }
        Spacer(Modifier.height(22.dp))
        val shape = RoundedCornerShape(24.dp)
        Column(Modifier.fillMaxWidth().background(Palette.Surface, shape).border(1.dp, Palette.ParentLine, shape).padding(horizontal = 32.dp, vertical = 20.dp)) {
            Text(stringResource(R.string.progress_best), style = ParentType.H2.copy(fontSize = 22.sp))
            Spacer(Modifier.height(8.dp))
            state.levels.forEachIndexed { i, row ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(row.game), style = ParentType.Body.copy(fontSize = 17.sp), modifier = Modifier.weight(1f))
                    Text(row.label, style = ParentType.Body.copy(fontSize = 17.sp, color = Palette.ParentInk2), modifier = Modifier.weight(1f))
                    StarsRow(row.stars, size = 26.dp, gap = 4.dp)
                }
                if (i < state.levels.lastIndex) HorizontalDivider(color = Palette.ParentLine)
            }
            state.week.forEach { row ->
                HorizontalDivider(color = Palette.ParentLine)
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(row.game), style = ParentType.Body.copy(fontSize = 17.sp), modifier = Modifier.weight(1f))
                    Text(
                        row.label ?: stringResource(R.string.progress_free_play),
                        style = ParentType.Body.copy(fontSize = 17.sp, color = Palette.ParentInk2),
                        modifier = Modifier.weight(1f),
                    )
                    if (row.stars != null) {
                        StarsRow(row.stars, size = 26.dp, gap = 4.dp)
                    } else {
                        Text(stringResource(R.string.progress_minutes, row.minutes), style = ParentType.Body.copy(fontSize = 17.sp))
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Column(Modifier.fillMaxWidth().background(Palette.Surface, shape).border(1.dp, Palette.ParentLine, shape).padding(horizontal = 32.dp, vertical = 20.dp)) {
            Text(stringResource(R.string.progress_drawings), style = ParentType.H2.copy(fontSize = 22.sp))
            Spacer(Modifier.height(12.dp))
            if (state.drawings.isEmpty()) {
                Text(stringResource(R.string.progress_drawings_empty), style = ParentType.Body.copy(color = Palette.ParentInk2))
            }
            state.drawings.chunked(4).forEach { row ->
                Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { file ->
                        Box(Modifier.weight(1f).aspectRatio(4f / 3f).clip(RoundedCornerShape(14.dp)).border(1.dp, Palette.ParentLine, RoundedCornerShape(14.dp))) {
                            AsyncImage(model = file, contentDescription = null, modifier = Modifier.fillMaxSize())
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(48.dp)
                                    .background(Palette.Surface, CircleShape)
                                    .clickable(role = Role.Button) { onDeleteDrawing(file) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(painterResource(R.drawable.ic_trash), stringResource(R.string.cd_delete_drawing), tint = Palette.Danger, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Column(modifier.background(Palette.Surface, shape).border(1.dp, Palette.ParentLine, shape).padding(28.dp)) {
        Text(value, style = ParentType.H1.copy(fontSize = 30.sp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = ParentType.Body.copy(color = Palette.ParentInk2))
    }
}
