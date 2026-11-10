package com.familygames.presentation.parent.gate

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familygames.R
import com.familygames.presentation.common.raisedShadow
import com.familygames.presentation.parent.common.ParentPrimaryButton
import com.familygames.presentation.parent.common.PinDots
import com.familygames.presentation.parent.common.PinPad
import com.familygames.presentation.parent.common.SegmentedTabs
import com.familygames.presentation.theme.Palette
import com.familygames.presentation.theme.ParentTheme
import com.familygames.presentation.theme.ParentType

/** S08 · parent gate shown over the current screen (same window: immersive mode is not disturbed). */
@Composable
fun ParentGateOverlay(state: ParentGateUiState, onEvent: (ParentGateEvent) -> Unit) {
    BackHandler { onEvent(ParentGateEvent.Close) }
    ParentTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(Palette.Ink.copy(alpha = 0.55f))
                .pointerInput(Unit) {
                    // Any touch counts as activity for the 30 s auto-close.
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            onEvent(ParentGateEvent.Interaction)
                        }
                    }
                }
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(28.dp)
            Column(
                Modifier
                    .width(660.dp)
                    .raisedShadow(shape)
                    .background(Palette.Surface, shape)
                    .padding(start = 48.dp, end = 48.dp, top = 40.dp, bottom = 36.dp),
            ) {
                Header(onClose = { onEvent(ParentGateEvent.Close) }, subtitle = subtitle(state))
                if (!state.loading) {
                    if (state.tab == GateTab.Task || state.tab == GateTab.Pin) {
                        Spacer(Modifier.height(24.dp))
                        SegmentedTabs(
                            options = listOf(stringResource(R.string.gate_tab_task), stringResource(R.string.gate_tab_pin)),
                            selected = if (state.tab == GateTab.Task) 0 else 1,
                            onSelect = { onEvent(ParentGateEvent.SelectTab(if (it == 0) GateTab.Task else GateTab.Pin)) },
                            enabled = { if (it == 0) state.taskAllowed else state.hasPin },
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    when (state.tab) {
                        GateTab.Task -> TaskContent(state, onEvent)
                        GateTab.Pin -> PinContent(state, onEvent)
                        GateTab.Recovery -> RecoveryContent(state, onEvent)
                        GateTab.NewPin -> NewPinContent(state, onEvent)
                        GateTab.NewCode -> NewCodeContent(state, onEvent)
                    }
                    Spacer(Modifier.height(24.dp))
                    Footer(state)
                }
            }
        }
    }
}

@Composable
private fun subtitle(state: ParentGateUiState): String = stringResource(
    when (state.tab) {
        GateTab.Task -> R.string.gate_subtitle_task
        GateTab.Pin -> R.string.gate_subtitle_pin
        GateTab.Recovery -> R.string.gate_subtitle_recovery
        GateTab.NewPin -> if (state.newPinFirst == null) R.string.gate_subtitle_new_pin else R.string.gate_subtitle_repeat_pin
        GateTab.NewCode -> R.string.gate_subtitle_new_code
    },
)

@Composable
private fun Header(onClose: () -> Unit, subtitle: String) {
    Box(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(painterResource(R.drawable.ic_shield), null, tint = Palette.AccentInk, modifier = Modifier.size(34.dp))
                Text(stringResource(R.string.gate_title), style = ParentType.H1.copy(fontSize = 32.sp), modifier = Modifier.semantics { heading() })
            }
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = ParentType.Body.copy(color = Palette.ParentInk2, fontSize = 18.sp))
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(56.dp)
                .background(Palette.Bg, CircleShape)
                .clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.cd_close), tint = Palette.ParentInk2, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun TaskContent(state: ParentGateUiState, onEvent: (ParentGateEvent) -> Unit) {
    val challenge = state.challenge ?: return
    val locked = state.lockedSeconds > 0
    Text(
        text = if (locked) stringResource(R.string.gate_locked, state.lockedSeconds / 60, state.lockedSeconds % 60) else stringResource(R.string.gate_equation, challenge.a, challenge.b),
        style = ParentType.H1.copy(fontSize = if (locked) 26.sp else 64.sp, color = if (locked) Palette.Danger else Palette.ParentInk),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(28.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        challenge.options.forEach { option ->
            val shape = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(82.dp)
                    .background(Palette.Surface, shape)
                    .border(1.dp, Palette.ParentLine, shape)
                    .clickable(enabled = !locked && !state.busy, role = Role.Button) { onEvent(ParentGateEvent.Answer(option)) },
                contentAlignment = Alignment.Center,
            ) {
                Text(option.toString(), style = ParentType.H1.copy(fontSize = 34.sp))
            }
        }
    }
}

@Composable
private fun PinContent(state: ParentGateUiState, onEvent: (ParentGateEvent) -> Unit) {
    val locked = state.lockedSeconds > 0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            PinDots(state.pin.length, error = state.wrong)
            if (locked) {
                Text(stringResource(R.string.gate_locked, state.lockedSeconds / 60, state.lockedSeconds % 60), style = ParentType.Body.copy(color = Palette.Danger))
            }
            Text(
                stringResource(R.string.gate_forgot_pin),
                style = ParentType.Body.copy(color = Palette.AccentInk, fontWeight = FontWeight.ExtraBold),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { onEvent(ParentGateEvent.ForgotPin) }
                    .padding(vertical = 12.dp),
            )
        }
        PinPad(onDigit = { onEvent(ParentGateEvent.Digit(it)) }, onDelete = { onEvent(ParentGateEvent.Delete) }, enabled = !locked && !state.busy)
    }
}

@Composable
private fun RecoveryContent(state: ParentGateUiState, onEvent: (ParentGateEvent) -> Unit) {
    val locked = state.lockedSeconds > 0
    OutlinedTextField(
        value = state.recoveryInput,
        onValueChange = { onEvent(ParentGateEvent.RecoveryChanged(it)) },
        singleLine = true,
        isError = state.wrong,
        label = { Text(stringResource(R.string.gate_recovery_label)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        textStyle = ParentType.H2.copy(fontFamily = FontFamily.Monospace, fontSize = 24.sp),
        modifier = Modifier.fillMaxWidth(),
    )
    if (locked) {
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.gate_locked, state.lockedSeconds / 60, state.lockedSeconds % 60), style = ParentType.Body.copy(color = Palette.Danger))
    }
    Spacer(Modifier.height(20.dp))
    ParentPrimaryButton(stringResource(R.string.gate_recovery_submit), onClick = { onEvent(ParentGateEvent.SubmitRecovery) }, enabled = !locked && state.recoveryInput.isNotBlank(), busy = state.busy)
}

@Composable
private fun NewPinContent(state: ParentGateUiState, onEvent: (ParentGateEvent) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PinDots(state.newPin.length, error = state.mismatch)
            if (state.mismatch) Text(stringResource(R.string.pin_mismatch), style = ParentType.Body.copy(color = Palette.Danger))
        }
        PinPad(onDigit = { onEvent(ParentGateEvent.Digit(it)) }, onDelete = { onEvent(ParentGateEvent.Delete) }, enabled = !state.busy)
    }
}

@Composable
private fun NewCodeContent(state: ParentGateUiState, onEvent: (ParentGateEvent) -> Unit) {
    RecoveryCodeCard(state.newRecoveryCode.orEmpty())
    Spacer(Modifier.height(20.dp))
    ParentPrimaryButton(stringResource(R.string.gate_code_saved), onClick = { onEvent(ParentGateEvent.FinishNewCode) })
}

/** The recovery code shown once, large and in a monospace font so it can be copied by hand. */
@Composable
fun RecoveryCodeCard(code: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Palette.HoneySoft, RoundedCornerShape(16.dp))
            .padding(vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(code, style = ParentType.H1.copy(fontFamily = FontFamily.Monospace, fontSize = 34.sp, letterSpacing = 2.sp))
    }
}

@Composable
private fun Footer(state: ParentGateUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val showAttempts = state.tab == GateTab.Task || state.tab == GateTab.Pin || state.tab == GateTab.Recovery
        Text(
            if (showAttempts) stringResource(R.string.gate_attempts_left, state.attemptsLeft) else "",
            style = ParentType.Body.copy(color = Palette.ParentInk2, fontSize = 15.sp),
        )
        if (state.tab != GateTab.NewCode) {
            Text(
                stringResource(R.string.gate_closes_in, state.secondsToClose / 60, state.secondsToClose % 60),
                style = ParentType.Body.copy(color = Palette.ParentInk2, fontSize = 15.sp),
            )
        }
    }
}
