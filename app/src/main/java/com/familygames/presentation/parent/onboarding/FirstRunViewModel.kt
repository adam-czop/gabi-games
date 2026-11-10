package com.familygames.presentation.parent.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.domain.parent.ParentAuthenticator
import com.familygames.domain.repository.SettingsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FirstRunStep { Welcome, Pin, RecoveryCode, Kiosk }

data class FirstRunUiState(
    val step: FirstRunStep = FirstRunStep.Welcome,
    val pin: String = "",
    val firstPin: String? = null,
    val mismatch: Boolean = false,
    val busy: Boolean = false,
    val recoveryCode: String? = null,
    val codeSaved: Boolean = false,
    val deviceOwner: Boolean = false,
    val provisioningCommand: String = "",
)

sealed interface FirstRunEvent {
    data object Next : FirstRunEvent
    data class Digit(val digit: Char) : FirstRunEvent
    data object Delete : FirstRunEvent
    data class CodeSaved(val saved: Boolean) : FirstRunEvent
    data class Finish(val addPhotos: Boolean) : FirstRunEvent
}

enum class FirstRunExit { Play, AddPhotos }

/** S12 · first start wizard (docs/03): welcome, PIN ×2, one-time recovery code, kiosk status. */
class FirstRunViewModel(
    private val auth: ParentAuthenticator,
    private val settings: SettingsRepository,
    deviceOwner: Boolean,
    provisioningCommand: String,
) : ViewModel() {

    private val state = MutableStateFlow(FirstRunUiState(deviceOwner = deviceOwner, provisioningCommand = provisioningCommand))
    val uiState: StateFlow<FirstRunUiState> = state.asStateFlow()

    private val exits = Channel<FirstRunExit>(Channel.BUFFERED)
    val exit: Flow<FirstRunExit> = exits.receiveAsFlow()

    fun onEvent(event: FirstRunEvent) {
        val s = state.value
        when (event) {
            FirstRunEvent.Next -> when (s.step) {
                FirstRunStep.Welcome -> state.update { it.copy(step = FirstRunStep.Pin) }
                FirstRunStep.RecoveryCode -> if (s.codeSaved) state.update { it.copy(step = FirstRunStep.Kiosk) }
                else -> Unit
            }
            is FirstRunEvent.Digit -> if (s.step == FirstRunStep.Pin && !s.busy) {
                val pin = (s.pin + event.digit).take(4)
                state.update { it.copy(pin = pin, mismatch = false) }
                if (pin.length == 4) onPinComplete(pin)
            }
            FirstRunEvent.Delete -> state.update { it.copy(pin = it.pin.dropLast(1)) }
            is FirstRunEvent.CodeSaved -> state.update { it.copy(codeSaved = event.saved) }
            is FirstRunEvent.Finish -> viewModelScope.launch {
                settings.update { it.copy(firstRunCompleted = true) }
                exits.send(if (event.addPhotos) FirstRunExit.AddPhotos else FirstRunExit.Play)
            }
        }
    }

    private fun onPinComplete(pin: String) {
        val first = state.value.firstPin
        when {
            first == null -> state.update { it.copy(firstPin = pin, pin = "") }
            first != pin -> state.update { it.copy(firstPin = null, pin = "", mismatch = true) }
            else -> viewModelScope.launch {
                state.update { it.copy(busy = true) }
                val code = auth.setPin(pin)
                state.update { it.copy(busy = false, recoveryCode = code, step = FirstRunStep.RecoveryCode, pin = "") }
            }
        }
    }
}
