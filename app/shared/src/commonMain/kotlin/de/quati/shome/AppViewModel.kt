package de.quati.shome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.quati.shome.model.BackendIntent
import de.quati.shome.model.BackendState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


class AppViewModel(
    private val backendClient: BackendClient = BackendClient(),
) : ViewModel() {
    val state: StateFlow<BackendState> = backendClient.getStateFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = BackendState(),
    )

    fun sendIntent(intent: BackendIntent) {
        viewModelScope.launch { backendClient.sendIntent(intent) }
    }
}