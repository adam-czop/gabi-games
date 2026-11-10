package com.familygames.presentation.parent.settings

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familygames.BuildConfig
import com.familygames.R
import com.familygames.data.updater.UpdateError
import com.familygames.data.updater.UpdateState
import com.familygames.domain.update.UpdateInfo
import com.familygames.presentation.parent.common.ParentPrimaryButton
import com.familygames.presentation.parent.common.ParentSecondaryButton
import com.familygames.presentation.theme.Palette
import com.familygames.presentation.theme.ParentType
import java.util.Date

/** S11 · "Aktualizacje" card (docs/07 §6). */
@Composable
fun ColumnScope.UpdatesCard(
    update: UpdateState,
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onCheck: () -> Unit,
    onInstall: () -> Unit,
) {
    val context = LocalContext.current
    KeyValueRow(stringResource(R.string.updates_installed), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    val info: UpdateInfo? = when (update) {
        is UpdateState.Downloading -> update.info
        is UpdateState.ReadyToInstall -> update.info
        is UpdateState.Installing -> update.info
        else -> null
    }
    val status = when (update) {
        UpdateState.Idle -> stringResource(R.string.updates_idle)
        UpdateState.Checking -> stringResource(R.string.updates_checking)
        is UpdateState.UpToDate -> stringResource(R.string.updates_up_to_date, DateFormat.getTimeFormat(context).format(Date(update.checkedAt)))
        is UpdateState.Downloading -> stringResource(R.string.updates_downloading)
        is UpdateState.ReadyToInstall -> stringResource(R.string.updates_ready)
        is UpdateState.Installing -> stringResource(R.string.updates_installing)
        is UpdateState.Error -> stringResource(R.string.updates_error, stringResource(errorText(update.reason)))
    }
    KeyValueRow(
        stringResource(R.string.updates_available),
        info?.let { "${it.versionName} (${it.versionCode})" } ?: status,
        valueColor = if (update is UpdateState.Error) Palette.Danger else if (info != null) Palette.AccentInk else Palette.ParentInk,
    )
    if (update is UpdateState.Downloading) {
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { if (update.total > 0) update.bytes.toFloat() / update.total else 0f },
            modifier = Modifier.fillMaxWidth(),
            color = Palette.AccentInk,
            trackColor = Palette.Bg2,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.updates_progress, Formatter.formatShortFileSize(context, update.bytes), Formatter.formatShortFileSize(context, update.total)),
            style = ParentType.Body.copy(color = Palette.ParentInk2, fontSize = 14.sp),
        )
    }
    if (info != null && info.changelog.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth().background(Palette.Bg, RoundedCornerShape(12.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            info.changelog.forEach { Text("• $it", style = ParentType.Body.copy(color = Palette.ParentInk2, fontSize = 15.sp)) }
        }
    }
    ToggleRow(stringResource(R.string.updates_check_on_start), state.settings.updateCheckOnStart) { v -> onEvent(SettingsEvent.Update { it.copy(updateCheckOnStart = v) }) }
    ToggleRow(stringResource(R.string.updates_wifi_only), state.settings.updateWifiOnly, last = true) { v -> onEvent(SettingsEvent.Update { it.copy(updateWifiOnly = v) }) }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ParentSecondaryButton(
            stringResource(R.string.updates_check_now),
            onClick = onCheck,
            enabled = update !is UpdateState.Checking && update !is UpdateState.Downloading && update !is UpdateState.Installing,
        )
        if (update is UpdateState.ReadyToInstall) {
            ParentPrimaryButton(stringResource(R.string.updates_install), onClick = onInstall, icon = R.drawable.ic_download)
        }
    }
}

private fun errorText(error: UpdateError): Int = when (error) {
    UpdateError.Network -> R.string.update_error_network
    UpdateError.NoUpdateFile -> R.string.update_error_no_file
    UpdateError.BadUpdateFile -> R.string.update_error_bad_file
    UpdateError.Verification -> R.string.update_error_verification
    UpdateError.DeviceTooOld -> R.string.update_error_device
    UpdateError.Install -> R.string.update_error_install
}
