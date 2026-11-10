package com.familygames.presentation.menu

import android.os.SystemClock
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familygames.R
import com.familygames.data.updater.UpdateManager
import com.familygames.data.updater.UpdateState
import com.familygames.presentation.theme.KidType
import com.familygames.presentation.theme.Palette
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import java.util.Calendar

/** Idle time on S01 before a ready update is installed (docs/07 §5). */
private const val MENU_IDLE_MS = 10_000L
private const val RESTART_SCREEN_MS = 2_500L

/**
 * Installs a downloaded and verified update while S01 is idle (never during a game). Returns true while the
 * "Za chwilę wracamy!" screen should be shown.
 */
@Composable
fun rememberUpdateOnMenu(lastTouch: () -> Long, gateOpen: () -> Boolean): Boolean {
    val updates: UpdateManager = koinInject()
    val state by updates.state.collectAsStateWithLifecycle()
    val touch by rememberUpdatedState(lastTouch)
    val gate by rememberUpdatedState(gateOpen)
    var restarting by remember { mutableStateOf(false) }
    val ready = state is UpdateState.ReadyToInstall
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        while (true) {
            delay(1_000)
            val idle = SystemClock.elapsedRealtime() - touch()
            if (idle >= MENU_IDLE_MS && !gate()) {
                restarting = true
                delay(RESTART_SCREEN_MS)
                val started = updates.installIfReady(
                    onMenu = true,
                    idleMs = idle + RESTART_SCREEN_MS,
                    parentRequested = false,
                    hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                    appInBackground = false,
                )
                if (!started) restarting = false
                break
            }
        }
    }
    return restarting && state !is UpdateState.Error
}

/** Short full-screen notice before the app replaces itself. */
@Composable
fun RestartingScreen() {
    val pulse = rememberInfiniteTransition(label = "restart")
    val scale by pulse.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale")
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Box(
                Modifier
                    .size(140.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(Palette.HoneySoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_star_filled), null, tint = Palette.Honey, modifier = Modifier.size(72.dp))
            }
            Text(stringResource(R.string.updates_restarting), style = KidType.Title)
        }
    }
}
