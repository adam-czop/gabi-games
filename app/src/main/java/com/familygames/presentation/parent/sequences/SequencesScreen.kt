package com.familygames.presentation.parent.sequences

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familygames.R
import com.familygames.domain.model.Photo
import com.familygames.domain.model.PhotoSequence
import com.familygames.presentation.common.PhotoImage
import com.familygames.presentation.parent.common.ParentHeader
import com.familygames.presentation.parent.common.ParentPrimaryButton
import com.familygames.presentation.parent.common.ParentSecondaryButton
import com.familygames.presentation.theme.Palette
import com.familygames.presentation.theme.ParentType

private val CardShape = RoundedCornerShape(22.dp)

/** S15 · Sekwencje. */
@Composable
fun SequencesScreen(state: SequencesUiState, onEvent: (SequencesEvent) -> Unit) {
    val editor = state.editor
    if (editor != null) {
        Editor(editor, state.photos, onEvent)
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 36.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ParentHeader(stringResource(R.string.sequences_title), stringResource(R.string.sequences_subtitle)) {
            ParentPrimaryButton("+ " + stringResource(R.string.sequences_new), onClick = { onEvent(SequencesEvent.New) })
        }
        if (!state.loading && state.sequences.isEmpty()) {
            Text(
                stringResource(R.string.sequences_empty),
                style = ParentType.Body.copy(color = Palette.ParentInk2, fontSize = 18.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Palette.Line2, CardShape)
                    .padding(32.dp),
            )
        }
        state.sequences.forEach { s -> SequenceCard(s, onEvent) }
    }
}

@Composable
private fun SequenceCard(sequence: PhotoSequence, onEvent: (SequencesEvent) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Surface, CardShape)
            .border(1.dp, Palette.ParentLine, CardShape)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(sequence.name, style = ParentType.H2.copy(fontSize = 22.sp), modifier = Modifier.weight(1f))
            Text(pluralStringResource(R.plurals.sequences_steps, sequence.steps.size, sequence.steps.size), style = ParentType.Body.copy(color = Palette.ParentInk2))
            ParentSecondaryButton(stringResource(R.string.sequences_edit), onClick = { onEvent(SequencesEvent.Edit(sequence)) })
            ParentSecondaryButton(stringResource(R.string.sequences_delete), onClick = { onEvent(SequencesEvent.Delete(sequence.id)) }, textColor = Palette.Danger)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            sequence.steps.forEachIndexed { i, photo ->
                if (i > 0) Text("›", style = ParentType.H2.copy(color = Palette.ParentInk2))
                StepThumb(photo, Modifier.width(150.dp))
            }
        }
    }
}

@Composable
private fun StepThumb(photo: Photo, modifier: Modifier = Modifier, number: Int? = null) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.border(1.dp, Palette.ParentLine, shape).clip(shape).background(Palette.Surface)) {
        Box {
            PhotoImage(photo.thumb, contentDescription = photo.label, modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f))
            if (number != null) NumberBadge(number, Modifier.padding(6.dp))
        }
        Text(
            photo.label.orEmpty(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = ParentType.Body.copy(fontSize = 15.sp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun NumberBadge(number: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(32.dp).background(Palette.AccentInk, CircleShape), contentAlignment = Alignment.Center) {
        Text(number.toString(), style = ParentType.Body.copy(color = Color.White, fontSize = 16.sp))
    }
}

@Composable
private fun Editor(editor: SequenceEditor, photos: List<Photo>, onEvent: (SequencesEvent) -> Unit) {
    val byId = photos.associateBy { it.id }
    Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = editor.name,
                onValueChange = { onEvent(SequencesEvent.Name(it)) },
                singleLine = true,
                label = { Text(stringResource(R.string.sequences_name)) },
                placeholder = { Text(stringResource(R.string.sequences_name_hint)) },
                textStyle = ParentType.Body.copy(fontSize = 20.sp),
                modifier = Modifier.weight(1f),
            )
            ParentSecondaryButton(stringResource(R.string.sequences_cancel), onClick = { onEvent(SequencesEvent.Cancel) })
            ParentPrimaryButton(stringResource(R.string.sequences_save), onClick = { onEvent(SequencesEvent.Save) }, enabled = editor.canSave, icon = R.drawable.ic_check)
        }
        Text(stringResource(R.string.sequences_pick), style = ParentType.Caption)
        // Fixed height, so the grid below does not jump when the first step is picked.
        Row(Modifier.height(236.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            editor.selected.forEachIndexed { i, id ->
                val photo = byId[id] ?: return@forEachIndexed
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StepThumb(photo, Modifier.width(132.dp), number = i + 1)
                    Row {
                        MoveButton(R.drawable.ic_back, stringResource(R.string.cd_move_earlier), enabled = i > 0) { onEvent(SequencesEvent.Move(i, -1)) }
                        MoveButton(R.drawable.ic_back, stringResource(R.string.cd_move_later), enabled = i < editor.selected.lastIndex, mirrored = true) {
                            onEvent(SequencesEvent.Move(i, 1))
                        }
                    }
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(photos, key = { it.id }) { photo ->
                val index = editor.selected.indexOf(photo.id)
                val shape = RoundedCornerShape(14.dp)
                Box(
                    Modifier
                        .clip(shape)
                        .border(if (index >= 0) 4.dp else 1.dp, if (index >= 0) Palette.AccentInk else Palette.ParentLine, shape)
                        .clickable(role = Role.Checkbox) { onEvent(SequencesEvent.TogglePhoto(photo.id)) },
                ) {
                    PhotoImage(photo.thumb, contentDescription = photo.label, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                    if (index >= 0) NumberBadge(index + 1, Modifier.padding(8.dp))
                }
            }
        }
        Spacer(Modifier.height(0.dp))
    }
}

@Composable
private fun MoveButton(icon: Int, description: String, enabled: Boolean, mirrored: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            description,
            tint = if (enabled) Palette.ParentInk else Palette.Line2,
            modifier = Modifier.size(24.dp).graphicsLayer { if (mirrored) rotationZ = 180f },
        )
    }
}
