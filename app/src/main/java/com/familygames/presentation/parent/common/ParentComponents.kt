package com.familygames.presentation.parent.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familygames.R
import com.familygames.presentation.theme.Palette
import com.familygames.presentation.theme.ParentType

private val ButtonShape = RoundedCornerShape(12.dp)

/** Primary parent action: filled AccentInk, white text (5.9:1, docs/08 §2). */
@Composable
fun ParentPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    busy: Boolean = false,
    color: Color = Palette.AccentInk,
) {
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .background(color, ButtonShape)
            .clickable(enabled = enabled && !busy, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        } else if (icon != null) {
            Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(text, style = ParentType.Body.copy(color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp))
    }
}

@Composable
fun ParentSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    textColor: Color = Palette.ParentInk,
) {
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .background(Palette.Surface, ButtonShape)
            .border(1.dp, Palette.ParentLine, ButtonShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(painterResource(icon), null, tint = textColor, modifier = Modifier.size(22.dp))
        Text(text, style = ParentType.Body.copy(color = textColor, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp))
    }
}

/** Segmented control (S08 tabs, S10 aspect). */
@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, enabled: (Int) -> Boolean = { true }) {
    Row(
        modifier
            .background(Palette.Bg2, RoundedCornerShape(14.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSelected = i == selected
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .background(if (isSelected) Palette.Surface else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable(enabled = enabled(i)) { onSelect(i) }
                    .semantics {
                        role = Role.Tab
                        this.selected = isSelected
                    }
                    .alpha(if (enabled(i)) 1f else 0.4f)
                    .padding(horizontal = 26.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = ParentType.Body.copy(fontWeight = FontWeight.ExtraBold, color = if (isSelected) Palette.ParentInk else Palette.ParentInk2, fontSize = 17.sp))
            }
        }
    }
}

/** Four dots for the PIN being typed. */
@Composable
fun PinDots(filled: Int, modifier: Modifier = Modifier, error: Boolean = false) {
    val description = stringResource(R.string.cd_pin_progress, filled)
    Row(modifier.semantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(4) { i ->
            Box(
                Modifier
                    .size(20.dp)
                    .background(
                        when {
                            error -> Palette.Danger
                            i < filled -> Palette.AccentInk
                            else -> Palette.Bg2
                        },
                        CircleShape,
                    ),
            )
        }
    }
}

/** 3×4 keypad, 72 dp keys (docs/06 §4). */
@Composable
fun PinPad(onDigit: (Char) -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val rows = listOf("123", "456", "789", " 0<")
    val deleteDescription = stringResource(R.string.cd_delete_digit)
    Column(modifier.alpha(if (enabled) 1f else 0.4f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    KeyButton(
                        visible = key != ' ',
                        enabled = enabled,
                        description = if (key == '<') deleteDescription else null,
                        onClick = { if (key == '<') onDelete() else onDigit(key) },
                    ) {
                        if (key == '<') {
                            Icon(painterResource(R.drawable.ic_back), null, tint = Palette.ParentInk, modifier = Modifier.size(26.dp))
                        } else {
                            Text(key.toString(), style = ParentType.H1.copy(fontSize = 28.sp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.KeyButton(visible: Boolean, enabled: Boolean, description: String?, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .size(width = 96.dp, height = 72.dp)
            .then(
                if (visible) {
                    Modifier
                        .background(Palette.Surface, shape)
                        .border(1.dp, Palette.ParentLine, shape)
                        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                        .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) { if (visible) content() }
}
