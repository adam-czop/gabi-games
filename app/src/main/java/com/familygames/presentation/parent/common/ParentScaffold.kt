package com.familygames.presentation.parent.common

import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familygames.BuildConfig
import com.familygames.R
import com.familygames.kiosk.KioskManager
import com.familygames.presentation.theme.Palette
import com.familygames.presentation.theme.ParentTheme
import com.familygames.presentation.theme.ParentType

enum class ParentSection(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    Photos(R.string.nav_photos, R.drawable.ic_image),
    Categories(R.string.nav_categories, R.drawable.ic_folder),
    Sequences(R.string.nav_sequences, R.drawable.ic_sequence),
    Progress(R.string.nav_progress, R.drawable.ic_chart),
    Games(R.string.nav_games, R.drawable.ic_games),
    Settings(R.string.nav_settings, R.drawable.ic_settings),
}

/**
 * The open parent zone: time of the last touch (the zone closes after 5 min without one, docs/03 S09)
 * and the lock task allow-list for the photo picker and camera (docs/06 §5).
 */
class ParentSession(private val kiosk: KioskManager) {
    fun begin() {
        touch()
        kiosk.beginParentSession()
    }

    fun end() = kiosk.endParentSession()

    @Volatile var lastInteraction: Long = SystemClock.elapsedRealtime()
        private set

    fun touch() {
        lastInteraction = SystemClock.elapsedRealtime()
    }

    fun idleMs(): Long = SystemClock.elapsedRealtime() - lastInteraction

    companion object {
        const val TIMEOUT_MS = 5 * 60_000L
    }
}

/** Parent zone frame (S09/S11/S13/S14): brand, side navigation, back to games, exit kiosk. */
@Composable
fun ParentScaffold(
    selected: ParentSection,
    onSection: (ParentSection) -> Unit,
    onBackToGames: () -> Unit,
    onInteraction: () -> Unit,
    onExitKiosk: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    ParentTheme {
        Row(
            Modifier
                .fillMaxSize()
                .background(Palette.Bg)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            onInteraction()
                        }
                    }
                }
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Column(
                Modifier
                    .width(262.dp)
                    .fillMaxHeight()
                    .background(Palette.Surface)
                    .padding(horizontal = 18.dp, vertical = 28.dp),
            ) {
                Row(Modifier.padding(start = 14.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.parent_brand), style = ParentType.H1.copy(fontSize = 22.sp), maxLines = 1, softWrap = false)
                    Text(
                        stringResource(R.string.parent_badge),
                        maxLines = 1,
                        softWrap = false,
                        style = ParentType.Caption.copy(color = Palette.AccentInk, fontSize = 11.sp),
                        modifier = Modifier
                            .background(Palette.AccentSoft, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
                ParentSection.entries.forEach { section ->
                    NavItem(stringResource(section.label), section.icon, selected = section == selected) { onSection(section) }
                }
                HorizontalDivider(Modifier.padding(vertical = 14.dp, horizontal = 8.dp), color = Palette.ParentLine)
                NavItem(stringResource(R.string.nav_back_to_games), R.drawable.ic_back, selected = false, onClick = onBackToGames)
                if (onExitKiosk != null) {
                    NavItem(stringResource(R.string.nav_exit_kiosk), R.drawable.ic_exit, selected = false, color = Palette.Danger, onClick = onExitKiosk)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.parent_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    style = ParentType.Body.copy(color = Palette.ParentInk2, fontSize = 13.sp),
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

@Composable
private fun NavItem(text: String, @DrawableRes icon: Int, selected: Boolean, color: Color = Palette.ParentInk, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .background(if (selected) Palette.AccentSoft else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val tint = if (selected) Palette.AccentInk else color
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(24.dp))
        Text(text, style = ParentType.Body.copy(color = tint, fontSize = 18.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold))
    }
}

/** Screen header used by all parent sections. */
@Composable
fun ParentHeader(title: String, subtitle: String?, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = ParentType.H1.copy(fontSize = 34.sp))
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = ParentType.Body.copy(color = Palette.ParentInk2, fontSize = 17.sp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { actions() }
    }
}
