package de.quati.shome.model

import kotlinx.serialization.Serializable


@Serializable
sealed interface BackendIntent {

    @Serializable
    data object OTFRun : BackendIntent

    @Serializable
    data object OTFSearchLatestVersion : BackendIntent

    @Serializable
    data object StartSearchShellysInSubnet : BackendIntent

    @Serializable
    data class StartSearchShellys(val endpoints: Set<NetworkEndpoint>) : BackendIntent

    @Serializable
    data class UpsertProfile(
        val data: Profile,
    ) : BackendIntent

    @Serializable
    data class ExecuteProfile(val id: ProfileId) : BackendIntent

    @Serializable
    data class DeleteProfile(val id: ProfileId) : BackendIntent

    @Serializable
    data class Shelly(
        val mac: Mac,
        val intent: ShellyIntent,
    ) : BackendIntent
}
