package de.quati.shome.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface BackendMessage {
    @Serializable
    @SerialName("state")
    data class State(
        val otfState: OtfState = OtfState.DISABLED,
        val currentVersion: String? = null,
        val latestVersion: String? = null,
        val isSearchingShellys: Boolean = false,
        val shellys: Map<Mac, ShellyState> = emptyMap(),
        val profiles: Map<ProfileId, Profile> = emptyMap(),
    ) : BackendMessage

    @Serializable
    @SerialName("notification")
    sealed interface Notification : BackendMessage

    @Serializable
    @SerialName("error")
    data class Error(val msg: String) : Notification

    @Serializable
    @SerialName("shellySearching")
    data class ShellySearching(val msg: String) : Notification

    @Serializable
    @SerialName("heartbeat")
    data object Heartbeat : Notification
}