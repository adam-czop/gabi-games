package com.familygames.presentation.parent.gate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familygames.domain.parent.AuthResult
import com.familygames.domain.parent.Challenge
import com.familygames.domain.parent.GateLockoutPolicy
import com.familygames.domain.parent.ParentAuthenticator
import com.familygames.domain.parent.ParentChallengeGenerator
import com.familygames.domain.repository.SettingsRepository
import com.familygames.util.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Why the gate is shown (docs/06 §4): entering the zone allows the task, critical actions need the PIN. */
enum class GatePurpose { EnterParentZone, Critical, ChangePin }

enum class GateTab { Task, Pin, Recovery, NewPin, NewCode }

data class ParentGateUiState(
    val loading: Boolean = true,
    val purpose: GatePurpose = GatePurpose.EnterParentZone,
    val tab: GateTab = GateTab.Task,
    val taskAllowed: Boolean = true,
    val hasPin: Boolean = false,
    val challenge: Challenge? = null,
    val pin: String = "",
    val recoveryInput: String = "",
    val newPin: String = "",
    val newPinFirst: String? = null,
    val newRecoveryCode: String? = null,
    val attemptsLeft: Int = GateLockoutPolicy.ATTEMPTS,
    val lockedSeconds: Int = 0,
    val wrong: Boolean = false,
    val mismatch: Boolean = false,
    val secondsToClose: Int = AUTO_CLOSE_SECONDS,
    val busy: Boolean = false,
)

sealed interface ParentGateEvent {
    data class SelectTab(val tab: GateTab) : ParentGateEvent
    data class Answer(val value: Int) : ParentGateEvent
    data class Digit(val digit: Char) : ParentGateEvent
    data object Delete : ParentGateEvent
    data object ForgotPin : ParentGateEvent
    data class RecoveryChanged(val text: String) : ParentGateEvent
    data object SubmitRecovery : ParentGateEvent
    data object FinishNewCode : ParentGateEvent
    data object Interaction : ParentGateEvent
    data object Close : ParentGateEvent
}

sealed interface ParentGateResult {
    data object Passed : ParentGateResult
    data object Closed : ParentGateResult
}

/** Inactivity after which the gate closes itself (docs/06 §4). */
const val AUTO_CLOSE_SECONDS = 30

class ParentGateViewModel(
    private val purpose: GatePurpose,
    private val auth: ParentAuthenticator,
    private val settings: SettingsRepository,
    private val challenges: ParentChallengeGenerator,
    private val clock: Clock,
) : ViewModel() {

    private val state = MutableStateFlow(ParentGateUiState(purpose = purpose))
    val uiState: StateFlow<ParentGateUiState> = state.asStateFlow()

    private val results = Channel<ParentGateResult>(Channel.BUFFERED)
    val result: Flow<ParentGateResult> = results.receiveAsFlow()

    private var lockedUntil = 0L
    private var ticker: Job? = null

    init {
        viewModelScope.launch {
            val prefs = settings.settings.first()
            val hasPin = prefs.pinHash != null
            val taskAllowed = purpose == GatePurpose.EnterParentZone && !(prefs.alwaysRequirePin && hasPin)
            lockedUntil = auth.gateState().lockedUntil
            state.update {
                it.copy(
                    loading = false, hasPin = hasPin, taskAllowed = taskAllowed,
                    tab = if (taskAllowed || !hasPin) GateTab.Task else GateTab.Pin,
                    challenge = challenges.next(),
                    attemptsLeft = GateLockoutPolicy.attemptsLeft(auth.gateState()),
                )
            }
            startTicker()
        }
    }

    fun onEvent(event: ParentGateEvent) {
        if (event != ParentGateEvent.Close) state.update { it.copy(secondsToClose = AUTO_CLOSE_SECONDS) }
        when (event) {
            is ParentGateEvent.SelectTab -> selectTab(event.tab)
            is ParentGateEvent.Answer -> answer(event.value)
            is ParentGateEvent.Digit -> digit(event.digit)
            ParentGateEvent.Delete -> delete()
            ParentGateEvent.ForgotPin -> state.update { it.copy(tab = GateTab.Recovery, recoveryInput = "", wrong = false) }
            is ParentGateEvent.RecoveryChanged -> state.update { it.copy(recoveryInput = event.text.take(16), wrong = false) }
            ParentGateEvent.SubmitRecovery -> submitRecovery()
            ParentGateEvent.FinishNewCode -> results.trySend(ParentGateResult.Passed)
            ParentGateEvent.Interaction -> Unit
            ParentGateEvent.Close -> results.trySend(ParentGateResult.Closed)
        }
    }

    private fun selectTab(tab: GateTab) {
        val s = state.value
        val allowed = when (tab) {
            GateTab.Task -> s.taskAllowed
            GateTab.Pin -> s.hasPin
            else -> false
        }
        if (allowed) state.update { it.copy(tab = tab, pin = "", wrong = false) }
    }

    private fun answer(value: Int) {
        val s = state.value
        val challenge = s.challenge ?: return
        if (s.busy || isLocked()) return
        submit { auth.checkChallenge(value == challenge.correct) }
    }

    private fun digit(d: Char) {
        val s = state.value
        if (s.busy || !d.isDigit()) return
        when (s.tab) {
            GateTab.Pin -> {
                if (isLocked()) return
                val pin = (s.pin + d).take(4)
                state.update { it.copy(pin = pin, wrong = false) }
                if (pin.length == 4) {
                    // Changing the PIN: the old one first, then a new one twice (S11).
                    if (purpose == GatePurpose.ChangePin) {
                        submit(onSuccess = { state.update { it.copy(tab = GateTab.NewPin, newPin = "", newPinFirst = null) } }) { auth.checkPin(pin) }
                    } else {
                        submit { auth.checkPin(pin) }
                    }
                }
            }
            GateTab.NewPin -> {
                val pin = (s.newPin + d).take(4)
                state.update { it.copy(newPin = pin, mismatch = false) }
                if (pin.length == 4) confirmNewPin(pin)
            }
            else -> Unit
        }
    }

    private fun delete() = state.update {
        when (it.tab) {
            GateTab.Pin -> it.copy(pin = it.pin.dropLast(1))
            GateTab.NewPin -> it.copy(newPin = it.newPin.dropLast(1))
            else -> it
        }
    }

    private fun submitRecovery() {
        if (state.value.busy || isLocked()) return
        submit(onSuccess = { state.update { it.copy(tab = GateTab.NewPin, newPin = "", newPinFirst = null) } }) {
            auth.checkRecoveryCode(state.value.recoveryInput)
        }
    }

    private fun confirmNewPin(pin: String) {
        val first = state.value.newPinFirst
        when {
            first == null -> state.update { it.copy(newPinFirst = pin, newPin = "") }
            first != pin -> state.update { it.copy(newPinFirst = null, newPin = "", mismatch = true) }
            else -> viewModelScope.launch {
                state.update { it.copy(busy = true) }
                val code = auth.setPin(pin)
                state.update { it.copy(busy = false, tab = GateTab.NewCode, newRecoveryCode = code, hasPin = true) }
            }
        }
    }

    private fun submit(onSuccess: () -> Unit = { results.trySend(ParentGateResult.Passed) }, check: suspend () -> AuthResult) {
        viewModelScope.launch {
            state.update { it.copy(busy = true) }
            when (val r = check()) {
                AuthResult.Success -> {
                    state.update { it.copy(busy = false) }
                    onSuccess()
                }
                is AuthResult.Wrong -> state.update {
                    it.copy(busy = false, wrong = true, pin = "", attemptsLeft = r.attemptsLeft, challenge = challenges.next())
                }
                is AuthResult.Locked -> {
                    lockedUntil = r.until
                    state.update {
                        it.copy(busy = false, wrong = true, pin = "", attemptsLeft = GateLockoutPolicy.ATTEMPTS, challenge = challenges.next())
                    }
                    tick()
                }
            }
        }
    }

    private fun isLocked() = clock.now() < lockedUntil

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                tick()
                delay(1_000)
                val left = state.value.secondsToClose - 1
                state.update { it.copy(secondsToClose = left.coerceAtLeast(0)) }
                // New recovery code on screen: never close it automatically before the parent wrote it down.
                if (left <= 0 && state.value.tab != GateTab.NewCode) results.trySend(ParentGateResult.Closed)
            }
        }
    }

    private fun tick() {
        val seconds = ((lockedUntil - clock.now()).coerceAtLeast(0) + 999) / 1000
        state.update { it.copy(lockedSeconds = seconds.toInt()) }
    }
}
