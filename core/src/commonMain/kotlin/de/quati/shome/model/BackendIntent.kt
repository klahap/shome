package de.quati.shome.model

import kotlinx.serialization.Serializable


@Serializable
sealed interface BackendIntent {
    @Serializable
    object StartSearchShellysInSubnet : BackendIntent

    @Serializable
    data class StartSearchShellys(val endpoints: Set<NetworkEndpoint>) : BackendIntent

    @Serializable
    data class Shelly(
        val mac: Mac,
        val intent: ShellyIntent,
    ) : BackendIntent
}
