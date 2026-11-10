package com.familygames.presentation.parent.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.domain.model.AppSettings
import com.familygames.domain.repository.GameResultRepository
import com.familygames.domain.repository.SettingsRepository
import com.familygames.kiosk.KioskManager
import com.familygames.kiosk.KioskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val kiosk: KioskStatus = KioskStatus(deviceOwner = false, lockTaskActive = false, isHomeApp = false),
    val provisioningCommand: String = "",
)

/** Actions that need the PIN again (docs/06 §4). */
enum class CriticalAction { ExitKiosk, Decommission, ClearProgress, ChangePin }

sealed interface SettingsEvent {
    data class Update(val transform: (AppSettings) -> AppSettings) : SettingsEvent
    data object RefreshKiosk : SettingsEvent
    data object ClearProgress : SettingsEvent
}

/** S11 · settings: kiosk status, security, game and sound (updates card: M8). */
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val results: GameResultRepository,
    private val kiosk: KioskManager,
) : ViewModel() {

    private val kioskStatus = MutableStateFlow(kiosk.status())

    val uiState: StateFlow<SettingsUiState> = combine(settings.settings, kioskStatus) { s, k ->
        SettingsUiState(s, k, kiosk.provisioningCommand)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(kiosk = kiosk.status(), provisioningCommand = kiosk.provisioningCommand))

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.Update -> viewModelScope.launch { settings.update(event.transform) }
            SettingsEvent.RefreshKiosk -> kioskStatus.value = kiosk.status()
            SettingsEvent.ClearProgress -> viewModelScope.launch { results.clear() }
        }
    }
}
