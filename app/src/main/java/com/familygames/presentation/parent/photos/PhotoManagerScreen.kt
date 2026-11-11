package com.familygames.presentation.parent.photos

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familygames.R
import com.familygames.domain.model.Photo
import com.familygames.presentation.common.PhotoImage
import com.familygames.presentation.parent.common.ParentHeader
import com.familygames.presentation.parent.common.ParentPrimaryButton
import com.familygames.presentation.parent.common.ParentSecondaryButton
import com.familygames.presentation.theme.Palette
import com.familygames.presentation.theme.ParentType

private const val MAX_PICK = 20

/** S09 · photos of the chosen category. */
@Composable
fun PhotoManagerScreen(
    state: PhotoManagerUiState,
    onEvent: (PhotoManagerEvent) -> Unit,
    onOpenPhoto: (Long) -> Unit,
    onAddPhotos: (List<String>, Long?) -> Unit,
    newCameraUri: () -> Uri,
    onClearNewCategoryError: () -> Unit,
) {
    val categoryId = state.selectedCategoryId
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { uris ->
        if (uris.isNotEmpty()) onAddPhotos(uris.map(Uri::toString), categoryId)
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) onAddPhotos(listOf(uri.toString()), categoryId)
    }
    val pickPhotos = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    var newCategoryDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 32.dp)) {
        val selected = state.selected
        if (state.selectionMode) {
            SelectionBar(state, onEvent, onDelete = { confirmDelete = true })
        } else {
            ParentHeader(
                title = stringResource(R.string.nav_photos),
                subtitle = selected?.let { stringResource(R.string.photos_subtitle, it.category.name, pluralStringResource(R.plurals.photos_count, it.photoCount, it.photoCount)) },
            ) {
                ParentSecondaryButton(stringResource(R.string.photos_from_gallery), onClick = pickPhotos, icon = R.drawable.ic_image)
                ParentPrimaryButton(stringResource(R.string.photos_take_photo), onClick = {
                    val uri = newCameraUri()
                    cameraUri = uri
                    camera.launch(uri)
                }, icon = R.drawable.ic_camera)
            }
        }
        Spacer(Modifier.height(20.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.categories, key = { it.category.id }) { summary ->
                Chip(
                    text = stringResource(R.string.category_chip, summary.category.name, summary.photoCount),
                    selected = summary.category.id == state.selectedCategoryId,
                ) { onEvent(PhotoManagerEvent.SelectCategory(summary.category.id)) }
            }
            item { Chip(stringResource(R.string.category_new), selected = false, dashed = true) { newCategoryDialog = true } }
        }
        Spacer(Modifier.height(18.dp))
        if (selected != null) StatusBar(state)
        Spacer(Modifier.height(18.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.photos, key = { it.id }) { photo ->
                PhotoCard(
                    photo = photo,
                    isCover = photo.id == selected?.category?.coverPhotoId,
                    selected = photo.id in state.selection,
                    onClick = { if (state.selectionMode) onEvent(PhotoManagerEvent.ToggleSelection(photo.id)) else onOpenPhoto(photo.id) },
                    onLongClick = { onEvent(PhotoManagerEvent.ToggleSelection(photo.id)) },
                )
            }
            item {
                Box(
                    Modifier
                        .aspectRatio(0.98f)
                        .dashed(Palette.Line2, 16.dp)
                        .clickable(role = Role.Button, onClick = pickPhotos),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.photos_add), style = ParentType.H2.copy(color = Palette.ParentInk2))
                }
            }
        }
    }

    if (newCategoryDialog || state.newCategoryError) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newCategoryDialog = false; onClearNewCategoryError() },
            title = { Text(stringResource(R.string.category_new_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    singleLine = true,
                    isError = state.newCategoryError,
                    supportingText = { if (state.newCategoryError) Text(stringResource(R.string.category_name_error)) },
                    label = { Text(stringResource(R.string.category_name)) },
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    onEvent(PhotoManagerEvent.CreateCategory(name))
                    newCategoryDialog = false
                }) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = { TextButton(onClick = { newCategoryDialog = false; onClearNewCategoryError() }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmDelete) {
        val deletable = state.photos.count { it.id in state.selection && !it.isBuiltIn }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(pluralStringResource(R.plurals.photos_delete_title, deletable, deletable)) },
            text = { Text(stringResource(R.string.photos_delete_text)) },
            confirmButton = {
                TextButton(onClick = { onEvent(PhotoManagerEvent.DeleteSelected); confirmDelete = false }) {
                    Text(stringResource(R.string.action_delete), color = Palette.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SelectionBar(state: PhotoManagerUiState, onEvent: (PhotoManagerEvent) -> Unit, onDelete: () -> Unit) {
    var moveMenu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.photos_selected, state.selection.size), style = ParentType.H1.copy(fontSize = 28.sp), modifier = Modifier.weight(1f))
        Box {
            ParentSecondaryButton(stringResource(R.string.photos_move), onClick = { moveMenu = true }, icon = R.drawable.ic_folder)
            DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
                state.categories.filter { it.category.id != state.selectedCategoryId }.forEach { summary ->
                    DropdownMenuItem(text = { Text(summary.category.name) }, onClick = {
                        onEvent(PhotoManagerEvent.MoveSelected(summary.category.id))
                        moveMenu = false
                    })
                }
            }
        }
        if (state.selection.size == 1) {
            ParentSecondaryButton(stringResource(R.string.photos_set_cover), onClick = { onEvent(PhotoManagerEvent.SetSelectedAsCover) })
        }
        val anyDeletable = state.photos.any { it.id in state.selection && !it.isBuiltIn }
        ParentSecondaryButton(stringResource(R.string.action_delete), onClick = onDelete, enabled = anyDeletable, textColor = Palette.Danger)
        ParentSecondaryButton(stringResource(R.string.action_cancel), onClick = { onEvent(PhotoManagerEvent.ClearSelection) })
    }
}

@Composable
private fun StatusBar(state: PhotoManagerUiState) {
    val missing = state.missingForNextLevel
    val ok = missing == null
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (ok) Palette.AccentSoft else Palette.HoneySoft, RoundedCornerShape(14.dp))
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(painterResource(if (ok) R.drawable.ic_check else R.drawable.ic_camera), null, tint = if (ok) Palette.AccentInk else Palette.ParentInk, modifier = Modifier.size(24.dp))
        Text(
            if (missing == null) {
                stringResource(R.string.photos_status_ok)
            } else {
                pluralStringResource(R.plurals.photos_status_missing, missing.first, missing.first, missing.second.rows, missing.second.cols)
            },
            style = ParentType.Body.copy(fontSize = 17.sp, color = if (ok) Palette.AccentInk else Palette.ParentInk),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoCard(photo: Photo, isCover: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .background(Palette.Surface, shape)
            .border(if (selected) 3.dp else 1.dp, if (selected) Palette.Accent else Palette.ParentLine, shape)
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            PhotoImage(photo.thumb, photo.label, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .background(Palette.Accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_check), null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                photo.label ?: stringResource(R.string.photo_no_label),
                style = ParentType.Body.copy(fontSize = 16.sp, color = if (photo.label == null) Palette.ParentInk2 else Palette.ParentInk),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isCover) {
                Text(
                    stringResource(R.string.photos_cover_badge),
                    style = ParentType.Body.copy(fontSize = 13.sp, color = Palette.ParentInk2),
                    modifier = Modifier.background(Palette.Bg2, CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
                )
            } else if (!photo.promptText.isNullOrBlank()) {
                Icon(painterResource(R.drawable.ic_volume_on), stringResource(R.string.cd_has_question), tint = Palette.ParentInk2, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, dashed: Boolean = false, onClick: () -> Unit) {
    val shape = CircleShape
    Box(
        Modifier
            .heightIn(min = 52.dp)
            .then(
                when {
                    dashed -> Modifier.dashed(Palette.Line2, 26.dp)
                    selected -> Modifier.background(Palette.AccentSoft, shape).border(1.dp, Palette.Accent, shape)
                    else -> Modifier.background(Palette.Surface, shape).border(1.dp, Palette.ParentLine, shape)
                },
            )
            .clip(shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = ParentType.Body.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (selected) Palette.AccentInk else Palette.ParentInk))
    }
}

private fun Modifier.dashed(color: Color, radius: Dp) = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
    )
}
