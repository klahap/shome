package de.quati.shome.model

import kotlinx.serialization.Serializable


@Serializable
sealed interface BackendIntent {
    @Serializable
    object StartSearchShellysInSubnet : BackendIntent

    @Serializable
    data class StartSearchShellys(val endpoints: Set<NetworkEndpoint>) : BackendIntent

    @Serializable
    data class UpsertProfile(
        val name: ProfileName,
        val positions: Map<Mac, Position>
    ) : BackendIntent


    @Serializable
    data class ExecuteProfile(val name: ProfileName) : BackendIntent

    @Serializable
    data class DeleteProfile(val name: ProfileName) : BackendIntent

    @Serializable
    data class Shelly(
        val mac: Mac,
        val intent: ShellyIntent,
    ) : BackendIntent
}
