package com.familygames.presentation.parent.settings

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import com.familygames.presentation.parent.common.copyText
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familygames.BuildConfig
import com.familygames.R
import com.familygames.presentation.parent.common.ParentHeader
import com.familygames.presentation.parent.common.ParentSecondaryButton
import com.familygames.presentation.theme.Palette
import com.familygames.presentation.theme.ParentType

/** S11 · settings in a two-column card layout (mockup). [onCritical] asks for the PIN first. */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onCritical: (CriticalAction) -> Unit,
    updatesCard: @Composable ColumnScope.() -> Unit,
) {
    val s = state.settings
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 40.dp, end = 40.dp, top = 32.dp, bottom = 32.dp),
    ) {
        ParentHeader(
            stringResource(R.string.nav_settings),
            stringResource(R.string.settings_device, Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL, Build.VERSION.RELEASE),
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Card(R.drawable.ic_download, stringResource(R.string.settings_updates), Modifier.weight(1f), content = updatesCard)
            Card(R.drawable.ic_shield, stringResource(R.string.settings_kiosk), Modifier.weight(1f)) {
                KioskCard(state, onEvent, onCritical)
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Card(R.drawable.ic_key, stringResource(R.string.settings_security), Modifier.weight(1f)) {
                KeyValueRow(
                    stringResource(R.string.settings_gate),
                    stringResource(if (s.alwaysRequirePin) R.string.settings_gate_always_pin else R.string.settings_gate_task_and_pin),
                )
                ToggleRow(stringResource(R.string.settings_always_pin), s.alwaysRequirePin) { v -> onEvent(SettingsEvent.Update { it.copy(alwaysRequirePin = v) }) }
                KeyValueRow(stringResource(R.string.settings_pin), "••••")
                Spacer(Modifier.height(16.dp))
                ParentSecondaryButton(stringResource(R.string.settings_change_pin), onClick = { onCritical(CriticalAction.ChangePin) })
            }
            Card(R.drawable.ic_volume_on, stringResource(R.string.settings_game_sound), Modifier.weight(1f)) {
                ToggleRow(stringResource(R.string.settings_sound), s.soundEnabled) { v -> onEvent(SettingsEvent.Update { it.copy(soundEnabled = v) }) }
                ToggleRow(stringResource(R.string.settings_labels), s.showCardLabels) { v -> onEvent(SettingsEvent.Update { it.copy(showCardLabels = v) }) }
                ToggleRow(stringResource(R.string.settings_level_locks), s.levelLocksEnabled, last = true) { v -> onEvent(SettingsEvent.Update { it.copy(levelLocksEnabled = v) }) }
            }
        }
        Spacer(Modifier.height(22.dp))
        Advanced(state, onCritical)
    }
}

@Composable
private fun KioskCard(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit, onCritical: (CriticalAction) -> Unit) {
    val k = state.kiosk
    val s = state.settings
    StatusRow(stringResource(R.string.settings_device_owner), k.deviceOwner, R.string.status_active, R.string.status_missing)
    StatusRow(stringResource(R.string.settings_lock_task), k.lockTaskActive, R.string.status_on, R.string.status_off)
    StatusRow(stringResource(R.string.settings_home_app), k.isHomeApp, R.string.status_set, R.string.status_not_set)
    ToggleRow(stringResource(R.string.settings_hide_bars), s.hideSystemBars) { v -> onEvent(SettingsEvent.Update { it.copy(hideSystemBars = v) }) }
    ToggleRow(stringResource(R.string.settings_keep_screen_on), s.keepScreenOn, last = true) { v -> onEvent(SettingsEvent.Update { it.copy(keepScreenOn = v) }) }
    Spacer(Modifier.height(16.dp))
    if (k.deviceOwner) {
        ParentSecondaryButton(stringResource(R.string.settings_exit_kiosk), onClick = { onCritical(CriticalAction.ExitKiosk) }, icon = R.drawable.ic_exit, textColor = Palette.Danger)
    } else {
        val clipboard = LocalClipboard.current
        val scope = rememberCoroutineScope()
        Text(stringResource(R.string.settings_kiosk_missing), style = ParentType.Body.copy(color = Palette.ParentInk2, fontSize = 15.sp))
        Spacer(Modifier.height(8.dp))
        Text(
            state.provisioningCommand,
            style = ParentType.Body.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            modifier = Modifier.fillMaxWidth().background(Palette.Bg2, RoundedCornerShape(10.dp)).padding(12.dp),
        )
        Spacer(Modifier.height(8.dp))
        ParentSecondaryButton(stringResource(R.string.action_copy_command), onClick = { scope.copyText(clipboard, state.provisioningCommand) })
    }
}

@Composable
private fun Advanced(state: SettingsUiState, onCritical: (CriticalAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().background(Palette.Surface, shape).border(1.dp, Palette.ParentLine, shape).padding(horizontal = 32.dp, vertical = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_advanced), style = ParentType.H2, modifier = Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_back), null, tint = Palette.ParentInk2, modifier = Modifier.size(22.dp).then(Modifier))
        }
        if (open) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ParentSecondaryButton(stringResource(R.string.settings_clear_progress), onClick = { onCritical(CriticalAction.ClearProgress) })
                if (state.kiosk.deviceOwner) {
                    ParentSecondaryButton(stringResource(R.string.settings_decommission), onClick = { onCritical(CriticalAction.Decommission) }, textColor = Palette.Danger)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.settings_build_info, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.KIOSK_ENABLED.toString()), style = ParentType.Body.copy(color = Palette.ParentInk2, fontSize = 13.sp))
        }
    }
}

@Composable
private fun Card(@DrawableRes icon: Int, title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(modifier.background(Palette.Surface, shape).border(1.dp, Palette.ParentLine, shape).padding(32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(painterResource(icon), null, tint = Palette.AccentInk, modifier = Modifier.size(28.dp))
            Text(title, style = ParentType.H2.copy(fontSize = 22.sp))
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
fun KeyValueRow(key: String, value: String, valueColor: Color = Palette.ParentInk, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = ParentType.Body.copy(fontSize = 17.sp, color = Palette.ParentInk2), modifier = Modifier.weight(1f))
        Text(value, style = ParentType.Body.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = valueColor))
    }
    if (!last) HorizontalDivider(color = Palette.ParentLine)
}

@Composable
private fun StatusRow(key: String, ok: Boolean, okText: Int, notText: Int) =
    KeyValueRow(key, stringResource(if (ok) okText else notText) + if (ok) " ✓" else "", if (ok) Palette.AccentInk else Palette.Danger)

@Composable
fun ToggleRow(label: String, checked: Boolean, last: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = ParentType.Body.copy(fontSize = 17.sp, color = Palette.ParentInk2), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
    if (!last) HorizontalDivider(color = Palette.ParentLine)
}
