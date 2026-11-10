package com.familygames.presentation.parent.gate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Shows the gate with a fresh ViewModel per opening ([key]) and reports the outcome once. */
@Composable
fun ParentGateHost(purpose: GatePurpose, key: String, onPassed: () -> Unit, onClosed: () -> Unit) {
    val vm: ParentGateViewModel = koinViewModel(key = key) { parametersOf(purpose) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val passed by rememberUpdatedState(onPassed)
    val closed by rememberUpdatedState(onClosed)
    LaunchedEffect(vm) {
        vm.result.collect {
            when (it) {
                ParentGateResult.Passed -> passed()
                ParentGateResult.Closed -> closed()
            }
        }
    }
    ParentGateOverlay(state, vm::onEvent)
}
