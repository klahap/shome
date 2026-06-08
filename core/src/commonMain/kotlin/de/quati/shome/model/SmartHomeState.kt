package de.quati.shome.model

import kotlinx.serialization.Serializable


@Serializable
data class SmartHomeState(
    val shellySearchState: ShellySearchState = ShellySearchState.None,
    val shellys: Map<Mac, ShellyState> = emptyMap(),
) {
    @Serializable
    sealed interface ShellySearchState {

        @Serializable
        data object None : ShellySearchState

        @Serializable
        data object Searching : ShellySearchState

        @Serializable
        data class Result(
            val macBefore: Set<Mac>,
            val macFound: Set<Mac>,
        ) : ShellySearchState
    }

}

