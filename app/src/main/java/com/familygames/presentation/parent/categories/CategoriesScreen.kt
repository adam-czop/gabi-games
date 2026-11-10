package com.familygames.presentation.parent.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.R
import com.familygames.domain.model.CategorySummary
import com.familygames.domain.repository.CategoryRepository
import com.familygames.presentation.common.PhotoImage
import com.familygames.presentation.parent.common.ParentHeader
import com.familygames.presentation.parent.common.ParentPrimaryButton
import com.familygames.presentation.theme.Palette
import com.familygames.presentation.theme.ParentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoriesUiState(val categories: List<CategorySummary> = emptyList(), val nameError: Boolean = false)

sealed interface CategoriesEvent {
    data class Create(val name: String) : CategoriesEvent
    data class Rename(val id: Long, val name: String) : CategoriesEvent
    data class SetVisible(val id: Long, val visible: Boolean) : CategoriesEvent
    data class Move(val id: Long, val direction: Int) : CategoriesEvent
    data class Delete(val id: Long) : CategoriesEvent
    data object ClearError : CategoriesEvent
}

/** S13 · categories CRUD, visibility for the child, order. */
class CategoriesViewModel(private val categories: CategoryRepository) : ViewModel() {
    private val error = MutableStateFlow(false)

    val uiState: StateFlow<CategoriesUiState> = combine(categories.observeSummaries(), error) { list, e -> CategoriesUiState(list, e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun onEvent(event: CategoriesEvent) {
        viewModelScope.launch {
            val result = runCatching {
                when (event) {
                    is CategoriesEvent.Create -> categories.create(event.name)
                    is CategoriesEvent.Rename -> categories.rename(event.id, event.name)
                    is CategoriesEvent.SetVisible -> categories.setHidden(event.id, !event.visible)
                    is CategoriesEvent.Move -> categories.move(event.id, event.direction)
                    is CategoriesEvent.Delete -> categories.delete(event.id)
                    CategoriesEvent.ClearError -> Unit
                }
            }
            error.value = result.isFailure
        }
    }
}

private sealed interface Dialog {
    data object New : Dialog
    data class Rename(val summary: CategorySummary) : Dialog
    data class Delete(val summary: CategorySummary) : Dialog
}

@Composable
fun CategoriesScreen(state: CategoriesUiState, onEvent: (CategoriesEvent) -> Unit) {
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 32.dp)) {
        ParentHeader(stringResource(R.string.nav_categories), stringResource(R.string.categories_subtitle)) {
            ParentPrimaryButton(stringResource(R.string.category_new), onClick = { dialog = Dialog.New })
        }
        Spacer(Modifier.height(24.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            itemsIndexed(state.categories, key = { _, it -> it.category.id }) { index, summary ->
                CategoryRow(
                    summary = summary,
                    first = index == 0,
                    last = index == state.categories.lastIndex,
                    onEvent = onEvent,
                    onRename = { dialog = Dialog.Rename(summary) },
                    onDelete = { dialog = Dialog.Delete(summary) },
                )
            }
        }
    }
    when (val d = dialog) {
        Dialog.New, is Dialog.Rename -> {
            var name by remember(d) { mutableStateOf((d as? Dialog.Rename)?.summary?.category?.name.orEmpty()) }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(if (d is Dialog.Rename) R.string.category_rename_title else R.string.category_new_title)) },
                text = {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(24) }, singleLine = true, label = { Text(stringResource(R.string.category_name)) })
                },
                confirmButton = {
                    TextButton(enabled = name.isNotBlank(), onClick = {
                        onEvent(if (d is Dialog.Rename) CategoriesEvent.Rename(d.summary.category.id, name) else CategoriesEvent.Create(name))
                        dialog = null
                    }) { Text(stringResource(R.string.action_save)) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
        is Dialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.category_delete_title, d.summary.category.name)) },
            text = { Text(pluralStringResource(R.plurals.category_delete_text, d.summary.photoCount, d.summary.photoCount)) },
            confirmButton = {
                TextButton(onClick = { onEvent(CategoriesEvent.Delete(d.summary.category.id)); dialog = null }) {
                    Text(stringResource(R.string.action_delete), color = Palette.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
        null -> Unit
    }
    if (state.nameError) {
        AlertDialog(
            onDismissRequest = { onEvent(CategoriesEvent.ClearError) },
            text = { Text(stringResource(R.string.category_name_error)) },
            confirmButton = { TextButton(onClick = { onEvent(CategoriesEvent.ClearError) }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun CategoryRow(
    summary: CategorySummary,
    first: Boolean,
    last: Boolean,
    onEvent: (CategoriesEvent) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = summary.category
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Surface, shape)
            .border(1.dp, Palette.ParentLine, shape)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        PhotoImage(summary.cover, null, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
        Column(Modifier.weight(1f)) {
            Text(c.name, style = ParentType.H2.copy(fontSize = 20.sp))
            Text(
                pluralStringResource(R.plurals.photos_count, summary.photoCount, summary.photoCount) +
                    if (c.isBuiltIn) " · " + stringResource(R.string.category_builtin) else "",
                style = ParentType.Body.copy(color = Palette.ParentInk2, fontSize = 15.sp),
            )
        }
        Text(stringResource(R.string.category_visible), style = ParentType.Body.copy(fontSize = 15.sp, color = Palette.ParentInk2))
        Switch(checked = !c.isHidden, onCheckedChange = { onEvent(CategoriesEvent.SetVisible(c.id, it)) })
        IconAction(R.drawable.ic_back, stringResource(R.string.cd_move_up), enabled = !first, rotation = 90f) { onEvent(CategoriesEvent.Move(c.id, -1)) }
        IconAction(R.drawable.ic_back, stringResource(R.string.cd_move_down), enabled = !last, rotation = -90f) { onEvent(CategoriesEvent.Move(c.id, 1)) }
        IconAction(R.drawable.ic_settings, stringResource(R.string.cd_rename), onClick = onRename)
        if (c.isBuiltIn) {
            Spacer(Modifier.size(48.dp)) // built-in categories can only be hidden; keep the columns aligned
        } else {
            IconAction(R.drawable.ic_close, stringResource(R.string.cd_delete_category), tint = Palette.Danger, onClick = onDelete)
        }
    }
}

@Composable
private fun IconAction(icon: Int, description: String, enabled: Boolean = true, rotation: Float = 0f, tint: androidx.compose.ui.graphics.Color = Palette.ParentInk, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, tint = if (enabled) tint else Palette.Line2, modifier = Modifier.size(24.dp).rotate(rotation))
    }
}
