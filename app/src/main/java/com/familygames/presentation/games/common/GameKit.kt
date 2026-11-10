package com.familygames.presentation.games.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.familygames.R
import com.familygames.domain.model.AppSettings
import com.familygames.domain.model.Category
import com.familygames.domain.model.FREE_PLAY_LEVEL
import com.familygames.domain.model.GameKind
import com.familygames.domain.model.GameResult
import com.familygames.domain.model.Photo
import com.familygames.domain.model.PhotoSource
import com.familygames.domain.repository.CategoryRepository
import com.familygames.domain.repository.GameResultRepository
import com.familygames.domain.repository.PhotoRepository
import com.familygames.presentation.common.KidFrame
import com.familygames.presentation.common.LocalReducedMotion
import com.familygames.presentation.common.PhotoImage
import com.familygames.presentation.common.RoundIconButton
import com.familygames.presentation.common.Sfx
import com.familygames.presentation.common.WinOverlay
import com.familygames.presentation.common.softShadow
import com.familygames.presentation.theme.Dimens
import com.familygames.presentation.theme.KidType
import com.familygames.presentation.theme.Palette
import com.familygames.util.Clock
import kotlinx.coroutines.flow.first

/** One-off effects shared by the new games (docs/12): a sound, or back to the menu. */
sealed interface GameEffect {
    data class Sound(val sfx: Sfx, val rate: Float = 1f) : GameEffect
    data object Home : GameEffect
}

/** Photos the games may use: everything in visible categories (a snapshot, docs/04 §7). */
class GamePhotos(private val photos: PhotoRepository, private val categories: CategoryRepository) {
    suspend fun visibleCategories(): List<Category> = categories.observeCategories().first().filter { !it.isHidden }

    /** Photos per visible category, in category order. */
    suspend fun byCategory(): Map<Long, List<Photo>> {
        val visible = visibleCategories()
        val all = photos.observeAll().first().groupBy { it.categoryId }
        return visible.associate { it.id to all[it.id].orEmpty() }
    }

    suspend fun all(): List<Photo> = byCategory().values.flatten()

    /** "Rodzina"-like category first (docs/12 M15b), otherwise everything. */
    suspend fun preferFamily(familyName: String): List<Photo> {
        val groups = byCategory()
        val family = visibleCategories().firstOrNull { it.name.equals(familyName, ignoreCase = true) }
        return family?.let { groups[it.id] }?.takeIf { it.size >= 4 } ?: groups.values.flatten()
    }
}

/** Free play has no stars: only the time spent is stored, as stars 0 and level "free" (docs/12 §0). */
class FreePlayClock(private val results: GameResultRepository, private val clock: Clock) {
    private var startedAt = 0L

    fun start() {
        startedAt = clock.now()
    }

    suspend fun stop(game: GameKind) {
        if (startedAt == 0L) return
        val duration = clock.now() - startedAt
        startedAt = 0L
        if (duration < MIN_SESSION_MS) return
        results.save(
            GameResult(
                game = game, levelKey = FREE_PLAY_LEVEL, stars = 0, moves = 0, mistakes = 0, hintsUsed = 0,
                durationMs = duration, finishedAt = clock.now(),
            ),
        )
    }

    private companion object {
        const val MIN_SESSION_MS = 5_000L
    }
}

/** Task game frame (docs/12 M10.5): Home left, restart right, progress dots in the middle, S04 on a win. */
@Composable
fun TaskGameScaffold(
    onHome: () -> Unit,
    onRestart: () -> Unit,
    progress: Pair<Int, Int>?,
    winStars: Int?,
    onStarShown: (Int) -> Unit = {},
    singlePointer: Boolean = true,
    content: @Composable BoxWithConstraintsScope.(scale: Float) -> Unit,
) {
    BackHandler(onBack = onHome)
    KidFrame(singlePointer = singlePointer) { scale ->
        Box(Modifier.fillMaxSize().then(if (winStars != null) Modifier.blur(6.dp) else Modifier)) {
            this@KidFrame.content(scale)
            RoundIconButton(
                icon = R.drawable.ic_home,
                contentDescription = stringResource(R.string.cd_home),
                onClick = onHome,
                modifier = Modifier.padding(start = Dimens.ScreenPaddingH, top = Dimens.ScreenPaddingTop),
            )
            RoundIconButton(
                icon = R.drawable.ic_restart,
                contentDescription = stringResource(R.string.cd_restart),
                onClick = onRestart,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = Dimens.ScreenPaddingH, top = Dimens.ScreenPaddingTop),
            )
            if (progress != null) {
                ProgressDots(
                    done = progress.first,
                    total = progress.second,
                    scale = scale,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 66.dp * scale),
                )
            }
        }
        if (winStars != null) {
            WinOverlay(stars = winStars, onPlayAgain = onRestart, onMenu = onHome, scale = scale, onStarShown = onStarShown)
        }
    }
}

/** Free play frame: only the Home button (docs/12 M10.5). */
@Composable
fun FreePlayScaffold(
    onHome: () -> Unit,
    singlePointer: Boolean = true,
    content: @Composable BoxWithConstraintsScope.(scale: Float) -> Unit,
) {
    BackHandler(onBack = onHome)
    KidFrame(singlePointer = singlePointer) { scale ->
        content(scale)
        RoundIconButton(
            icon = R.drawable.ic_home,
            contentDescription = stringResource(R.string.cd_home),
            onClick = onHome,
            modifier = Modifier.padding(start = Dimens.ScreenPaddingH, top = Dimens.ScreenPaddingTop),
        )
    }
}

/** Dots like the Memorki pairs counter. */
@Composable
fun ProgressDots(done: Int, total: Int, scale: Float, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.cd_progress, (done + 1).coerceAtMost(total), total)
    Row(modifier.semantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(14.dp * scale)) {
        repeat(total) { i ->
            Box(Modifier.size(20.dp * scale).background(if (i < done) Palette.Accent else Palette.Line, CircleShape))
        }
    }
}

/** Gentle "no" (±4°, 300 ms) – never red, never a buzzer (docs/03 §0). */
@Composable
fun Modifier.wiggle(trigger: Int): Modifier {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            angle.animateTo(0f, keyframes {
                durationMillis = 300
                -4f at 60
                4f at 140
                -2f at 220
            })
        }
    }
    return graphicsLayer { rotationZ = angle.value }
}

/** Slow breathing scale used as a hint (docs/12 M11: after two misses the right card pulses). */
@Composable
fun Modifier.pulse(active: Boolean, amount: Float = 0.06f): Modifier {
    if (!active || LocalReducedMotion.current) return this
    val transition = rememberInfiniteTransition(label = "pulse")
    val s by transition.animateFloat(1f, 1f + amount, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "pulse")
    return graphicsLayer {
        scaleX = s
        scaleY = s
    }
}

/** White photo card from the mockups: soft shadow, rounded photo, optional accent ring. */
@Composable
fun PhotoCard(
    source: PhotoSource?,
    modifier: Modifier = Modifier,
    ring: Color? = null,
    radius: Dp = Dimens.RadiusL,
    padding: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .softShadow(shape)
            .background(Palette.Surface, shape)
            .then(if (ring != null) Modifier.border(Dimens.StrokeSelected, ring, shape) else Modifier.border(Dimens.Stroke, Palette.Line, shape))
            .padding(padding),
    ) {
        PhotoImage(source, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(radius - 8.dp)))
        content()
    }
}

/** Centered note for the child zone when a game has nothing to show (the parent adds photos). */
@Composable
fun BoxScope.EmptyGameNote(text: String = stringResource(R.string.game_no_photos)) {
    Text(text, style = KidType.Label, modifier = Modifier.align(Alignment.Center))
}

/** Settings helper: age band used for easy modes (docs/12 M16, M18). */
val AppSettings.isToddler: Boolean get() = childAgeBand == com.familygames.domain.model.AgeBand.TODDLER
