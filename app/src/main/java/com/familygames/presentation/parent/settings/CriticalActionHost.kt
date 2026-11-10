package com.familygames.presentation.parent.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.familygames.R
import com.familygames.kiosk.KioskManager
import com.familygames.presentation.parent.gate.GatePurpose
import com.familygames.presentation.parent.gate.ParentGateHost
import com.familygames.presentation.theme.Palette
import org.koin.compose.koinInject

/** Asks for the PIN (docs/06 §4) and then runs [action]. [onFinished] closes the flow either way. */
@Composable
fun CriticalActionHost(action: CriticalAction, onClearProgress: () -> Unit, onFinished: () -> Unit) {
    val kiosk: KioskManager = koinInject()
    val activity = LocalActivity.current ?: return
    var passed by remember(action) { mutableStateOf(false) }
    val opening = remember(action) { System.nanoTime() } // a fresh gate ViewModel for every request

    if (!passed) {
        ParentGateHost(
            purpose = if (action == CriticalAction.ChangePin) GatePurpose.ChangePin else GatePurpose.Critical,
            key = "critical-$action-$opening",
            onPassed = {
                when (action) {
                    CriticalAction.ExitKiosk -> {
                        kiosk.exitKiosk(activity)
                        onFinished()
                    }
                    CriticalAction.ClearProgress -> {
                        onClearProgress()
                        onFinished()
                    }
                    CriticalAction.ChangePin -> onFinished() // the gate itself set the new PIN and showed the code
                    CriticalAction.Decommission -> passed = true
                }
            },
            onClosed = onFinished,
        )
    } else {
        var text by remember { mutableStateOf("") }
        val word = stringResource(R.string.decommission_word)
        AlertDialog(
            onDismissRequest = onFinished,
            title = { Text(stringResource(R.string.decommission_title)) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text(stringResource(R.string.decommission_text)) }) },
            confirmButton = {
                TextButton(enabled = text.trim().equals(word, ignoreCase = true), onClick = {
                    kiosk.decommission(activity)
                    onFinished()
                }) { Text(stringResource(R.string.settings_decommission), color = Palette.Danger) }
            },
            dismissButton = { TextButton(onClick = onFinished) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
