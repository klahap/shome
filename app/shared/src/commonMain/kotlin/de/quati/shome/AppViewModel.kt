package de.quati.shome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.quati.shome.model.SmartHomeIntent
import de.quati.shome.model.SmartHomeState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


class AppViewModel(
    private val backendClient: BackendClient = BackendClient(),
) : ViewModel() {
    val state: StateFlow<SmartHomeState> = backendClient.getStateFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SmartHomeState(),
    )

    fun sendIntent(intent: SmartHomeIntent) {
        viewModelScope.launch { backendClient.sendIntent(intent) }
    }
}