package com.familygames.presentation.parent.common

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Copies plain text (e.g. the provisioning command) with the suspend clipboard API. */
fun CoroutineScope.copyText(clipboard: Clipboard, text: String) {
    launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("FamilyGames", text))) }
}
