package com.familygames.presentation.parent.photos

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.familygames.R
import com.familygames.data.local.files.CropFraction
import com.familygames.domain.model.Aspect
import com.familygames.presentation.common.PhotoImage
import com.familygames.presentation.parent.common.ParentPrimaryButton
import com.familygames.presentation.parent.common.ParentSecondaryButton
import com.familygames.presentation.parent.common.SegmentedTabs
import com.familygames.presentation.theme.Palette
import com.familygames.presentation.theme.ParentTheme
import com.familygames.presentation.theme.ParentType

/** S10 · crop + label + category + voice + games. */
@Composable
fun PhotoEditScreen(state: PhotoEditUiState, onEvent: (PhotoEditEvent) -> Unit, onBack: () -> Unit, onInteraction: () -> Unit) {
    var crop by remember { mutableStateOf<CropFraction?>(null) }
    var askMic by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var askTarget by remember { mutableStateOf(RecordTarget.Label) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onEvent(PhotoEditEvent.RecordToggle(askTarget))
    }
    val onRecord: (RecordTarget) -> Unit = { target ->
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted || state.recordingOf(target) is RecordingState.Recording) {
            onEvent(PhotoEditEvent.RecordToggle(target))
        } else {
            askTarget = target
            askMic = true
        }
    }

    ParentTheme {
        Column(
            Modifier
                .fillMaxSize()
                .background(Palette.Bg)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 36.dp, end = 36.dp, top = 28.dp, bottom = 24.dp),
        ) {
            TopBar(state, onEvent, onBack)
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                Column(Modifier.weight(0.47f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Palette.Ink),
                        contentAlignment = Alignment.Center,
                    ) {
                        val image = state.image
                        when {
                            state.loading -> CircularProgressIndicator(color = Palette.Surface)
                            state.existing != null -> PhotoImage(
                                state.existing, null,
                                Modifier
                                    .fillMaxWidth(0.86f)
                                    .aspectRatio(if (state.aspect == Aspect.SQUARE) 1f else 4f / 3f)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                            image != null -> CropView(image, state.aspect, resetKey = state.imageVersion, onCrop = { crop = it }, modifier = Modifier.fillMaxSize())
                            state.decodeFailed -> Text(stringResource(R.string.photo_decode_failed), style = ParentType.Body.copy(color = Palette.Surface))
                        }
                    }
                    if (!state.editing) {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.photo_crop_hint), style = ParentType.Body.copy(color = Palette.ParentInk2, fontSize = 15.sp))
                    }
                }
                DetailsCard(
                    state = state,
                    onEvent = onEvent,
                    onRecord = onRecord,
                    onSave = { onInteraction(); onEvent(PhotoEditEvent.Save(crop)) },
                    onCancel = onBack,
                    modifier = Modifier.weight(0.53f).fillMaxHeight(),
                )
            }
        }
        if (askMic) {
            AlertDialog(
                onDismissRequest = { askMic = false },
                title = { Text(stringResource(R.string.mic_rationale_title)) },
                text = { Text(stringResource(R.string.mic_rationale_text)) },
                confirmButton = {
                    TextButton(onClick = { askMic = false; micPermission.launch(Manifest.permission.RECORD_AUDIO) }) { Text(stringResource(R.string.action_continue)) }
                },
                dismissButton = { TextButton(onClick = { askMic = false }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

@Composable
private fun TopBar(state: PhotoEditUiState, onEvent: (PhotoEditEvent) -> Unit, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        val shape = RoundedCornerShape(16.dp)
        Box(
            Modifier
                .size(64.dp)
                .background(Palette.Surface, shape)
                .border(1.dp, Palette.ParentLine, shape)
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_back), stringResource(R.string.cd_back), tint = Palette.ParentInk, modifier = Modifier.size(26.dp))
        }
        Text(stringResource(if (state.editing) R.string.photo_edit_title else R.string.photo_new_title), style = ParentType.H1.copy(fontSize = 34.sp))
        if (state.total > 1) {
            Text(stringResource(R.string.photo_progress, state.index + 1, state.total), style = ParentType.H2.copy(color = Palette.ParentInk2))
            ParentSecondaryButton(stringResource(R.string.action_skip), onClick = { onEvent(PhotoEditEvent.Skip) })
        }
        Spacer(Modifier.weight(1f))
        if (!state.editing) {
            SegmentedTabs(
                options = listOf(stringResource(R.string.aspect_square), stringResource(R.string.aspect_four_three)),
                selected = if (state.aspect == Aspect.SQUARE) 0 else 1,
                onSelect = { onEvent(PhotoEditEvent.AspectChanged(if (it == 0) Aspect.SQUARE else Aspect.FOUR_THREE)) },
            )
            ParentSecondaryButton(stringResource(R.string.action_rotate), onClick = { onEvent(PhotoEditEvent.Rotate) }, icon = R.drawable.ic_rotate)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsCard(
    state: PhotoEditUiState,
    onEvent: (PhotoEditEvent) -> Unit,
    onRecord: (RecordTarget) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .background(Palette.Surface, shape)
            .border(1.dp, Palette.ParentLine, shape)
            .padding(32.dp),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldLabel(stringResource(R.string.photo_label_title))
            OutlinedTextField(
                value = state.label,
                onValueChange = { onEvent(PhotoEditEvent.LabelChanged(it)) },
                singleLine = true,
                textStyle = ParentType.Body.copy(fontSize = 20.sp),
                suffix = { Text("${state.label.length}/$LABEL_MAX", style = ParentType.Body.copy(color = Palette.ParentInk2)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            FieldLabel(stringResource(R.string.photo_category_title))
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = state.categories.firstOrNull { it.id == state.categoryId }?.name.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    textStyle = ParentType.Body.copy(fontSize = 20.sp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    state.categories.forEach { category ->
                        DropdownMenuItem(text = { Text(category.name, style = ParentType.Body) }, onClick = {
                            onEvent(PhotoEditEvent.CategoryChanged(category.id))
                            expanded = false
                        })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            FieldLabel(stringResource(R.string.photo_voice_title))
            VoiceSection(state.recording, { onRecord(RecordTarget.Label) }, RecordTarget.Label, onEvent)
            Spacer(Modifier.height(10.dp))
            FieldLabel(stringResource(R.string.photo_prompt_title))
            Text(stringResource(R.string.photo_prompt_hint), style = ParentType.Body.copy(color = Palette.ParentInk2, fontSize = 15.sp))
            VoiceSection(state.promptRecording, { onRecord(RecordTarget.Prompt) }, RecordTarget.Prompt, onEvent)
            Spacer(Modifier.height(10.dp))
            FieldLabel(stringResource(R.string.photo_games_title))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                ToggleWithLabel(stringResource(R.string.game_memory), state.useInMemory) { onEvent(PhotoEditEvent.UseInMemory(it)) }
                ToggleWithLabel(stringResource(R.string.game_puzzle), state.useInPuzzle) { onEvent(PhotoEditEvent.UseInPuzzle(it)) }
            }
            ToggleWithLabel(stringResource(R.string.photo_countable), state.countable) { onEvent(PhotoEditEvent.Countable(it)) }
            Text(stringResource(R.string.photo_countable_hint), style = ParentType.Body.copy(color = Palette.ParentInk2, fontSize = 15.sp))
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End)) {
            ParentSecondaryButton(stringResource(R.string.action_cancel), onClick = onCancel)
            ParentPrimaryButton(
                stringResource(R.string.photo_save),
                onClick = onSave,
                icon = R.drawable.ic_check,
                busy = state.saving,
                enabled = state.categoryId != null && (state.image != null || state.editing) && !state.anyRecording,
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) = Text(text.uppercase(), style = ParentType.Caption)

@Composable
private fun ToggleWithLabel(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Switch(checked = checked, onCheckedChange = onChange)
        Text(label, style = ParentType.Body.copy(fontSize = 18.sp))
    }
}

@Composable
private fun VoiceSection(recording: RecordingState, onRecord: () -> Unit, target: RecordTarget, onEvent: (PhotoEditEvent) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Palette.ParentLine, shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val isRecording = recording is RecordingState.Recording
        Box(
            Modifier
                .size(80.dp)
                .background(if (isRecording) Palette.Danger.copy(alpha = 0.15f) else Palette.Bg, CircleShape)
                .padding(8.dp)
                .background(Palette.Danger, CircleShape)
                .clickable(role = Role.Button, onClick = onRecord),
            contentAlignment = Alignment.Center,
        ) {
            if (isRecording) {
                Box(Modifier.size(22.dp).background(Palette.Surface, RoundedCornerShape(4.dp)))
            } else {
                Icon(painterResource(R.drawable.ic_mic), stringResource(R.string.cd_record), tint = Palette.Surface, modifier = Modifier.size(30.dp))
            }
        }
        val levels = (recording as? RecordingState.Recording)?.levels.orEmpty()
        Row(Modifier.weight(1f).height(56.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(40) { i ->
                val level = levels.getOrNull(i)
                Box(
                    Modifier
                        .width(5.dp)
                        .fillMaxHeight(level ?: if (recording is RecordingState.Ready) 0.4f else 0.15f)
                        .background(if (level != null || recording is RecordingState.Ready) Palette.AccentInk else Palette.Line, RoundedCornerShape(3.dp)),
                )
            }
        }
        val seconds = when (recording) {
            is RecordingState.Recording -> recording.elapsedMs / 1000
            is RecordingState.Ready -> recording.durationMs / 1000
            RecordingState.None -> 0
        }
        Text(stringResource(R.string.voice_timer, seconds), style = ParentType.H2.copy(fontSize = 22.sp))
    }
    if (recording is RecordingState.Ready) {
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ParentSecondaryButton(stringResource(R.string.voice_play), onClick = { onEvent(PhotoEditEvent.Play(target)) }, icon = R.drawable.ic_play)
            ParentSecondaryButton(stringResource(R.string.voice_rerecord), onClick = onRecord)
            ParentSecondaryButton(stringResource(R.string.voice_delete), onClick = { onEvent(PhotoEditEvent.DeleteRecording(target)) }, textColor = Palette.Danger)
        }
    }
}
