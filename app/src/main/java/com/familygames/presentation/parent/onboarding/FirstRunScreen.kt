package com.familygames.presentation.parent.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import com.familygames.presentation.parent.common.copyText
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familygames.R
import com.familygames.presentation.parent.common.ParentPrimaryButton
import com.familygames.presentation.parent.common.ParentSecondaryButton
import com.familygames.presentation.parent.common.PinDots
import com.familygames.presentation.parent.common.PinPad
import com.familygames.presentation.parent.gate.RecoveryCodeCard
import com.familygames.presentation.theme.Palette
import com.familygames.presentation.theme.ParentTheme
import com.familygames.presentation.theme.ParentType

/** S12 · first start (no mockup; docs/03): a calm 4-step wizard in the parent style. */
@Composable
fun FirstRunScreen(state: FirstRunUiState, onEvent: (FirstRunEvent) -> Unit) {
    ParentTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(Palette.Bg)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(28.dp)
            Column(
                Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth(0.9f)
                    .background(Palette.Surface, shape)
                    .border(1.dp, Palette.ParentLine, shape)
                    .verticalScroll(rememberScrollState())
                    .padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                StepIndicator(state.step)
                when (state.step) {
                    FirstRunStep.Welcome -> Welcome(onEvent)
                    FirstRunStep.Pin -> PinStep(state, onEvent)
                    FirstRunStep.RecoveryCode -> CodeStep(state, onEvent)
                    FirstRunStep.Kiosk -> KioskStep(state, onEvent)
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(step: FirstRunStep) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FirstRunStep.entries.forEach {
            Box(Modifier.size(width = 40.dp, height = 6.dp).background(if (it.ordinal <= step.ordinal) Palette.AccentInk else Palette.Bg2, CircleShape))
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, style = ParentType.H1, modifier = Modifier.semantics { heading() })

@Composable
private fun Body(text: String) = Text(text, style = ParentType.Body.copy(color = Palette.ParentInk2, fontSize = 17.sp))

@Composable
private fun Welcome(onEvent: (FirstRunEvent) -> Unit) {
    Title(stringResource(R.string.first_run_welcome_title))
    Body(stringResource(R.string.first_run_welcome_text))
    ParentPrimaryButton(stringResource(R.string.action_next), onClick = { onEvent(FirstRunEvent.Next) })
}

@Composable
private fun PinStep(state: FirstRunUiState, onEvent: (FirstRunEvent) -> Unit) {
    Title(stringResource(if (state.firstPin == null) R.string.first_run_pin_title else R.string.first_run_pin_repeat))
    Body(stringResource(R.string.first_run_pin_text))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PinDots(state.pin.length, error = state.mismatch)
            if (state.mismatch) Text(stringResource(R.string.pin_mismatch), style = ParentType.Body.copy(color = Palette.Danger))
        }
        PinPad(onDigit = { onEvent(FirstRunEvent.Digit(it)) }, onDelete = { onEvent(FirstRunEvent.Delete) }, enabled = !state.busy)
    }
}

@Composable
private fun CodeStep(state: FirstRunUiState, onEvent: (FirstRunEvent) -> Unit) {
    Title(stringResource(R.string.first_run_code_title))
    Body(stringResource(R.string.first_run_code_text))
    RecoveryCodeCard(state.recoveryCode.orEmpty())
    Row(
        Modifier.toggleable(value = state.codeSaved, role = Role.Checkbox, onValueChange = { onEvent(FirstRunEvent.CodeSaved(it)) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = state.codeSaved, onCheckedChange = null)
        Text(stringResource(R.string.first_run_code_saved), style = ParentType.Body, modifier = Modifier.padding(end = 12.dp))
    }
    ParentPrimaryButton(stringResource(R.string.action_next), onClick = { onEvent(FirstRunEvent.Next) }, enabled = state.codeSaved)
}

@Composable
private fun KioskStep(state: FirstRunUiState, onEvent: (FirstRunEvent) -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    if (state.deviceOwner) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_check), null, tint = Palette.AccentInk, modifier = Modifier.size(32.dp))
            Title(stringResource(R.string.first_run_kiosk_ready))
        }
        Body(stringResource(R.string.first_run_kiosk_ready_text))
    } else {
        Title(stringResource(R.string.first_run_kiosk_missing))
        Body(stringResource(R.string.first_run_kiosk_missing_text))
        Box(
            Modifier
                .fillMaxWidth()
                .background(Palette.Bg2, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text(state.provisioningCommand, style = ParentType.Body.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp))
        }
        ParentSecondaryButton(stringResource(R.string.action_copy_command), onClick = { scope.copyText(clipboard, state.provisioningCommand) })
        Text(stringResource(R.string.first_run_kiosk_warning), style = ParentType.Body.copy(color = Palette.Danger, fontSize = 15.sp))
    }
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ParentSecondaryButton(stringResource(R.string.first_run_add_photos), onClick = { onEvent(FirstRunEvent.Finish(addPhotos = true)) }, icon = R.drawable.ic_image)
        ParentPrimaryButton(
            stringResource(if (state.deviceOwner) R.string.first_run_play else R.string.first_run_continue_without_kiosk),
            onClick = { onEvent(FirstRunEvent.Finish(addPhotos = false)) },
        )
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun FirstRunPreview() {
    FirstRunScreen(FirstRunUiState(step = FirstRunStep.RecoveryCode, recoveryCode = "ABCD-EFGH-2345"), {})
}
