package de.quati.shome.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class ShellyStatus(
    @SerialName("cover:0") val cover0: Cover0? = null,
    val sys: Sys? = null,
) : ShellyRpcResponse.Params {
    @Serializable
    data class Cover0(
        @SerialName("last_direction") val lastDirection: String? = null
    ) {
        val lastDirectionTyped
            get() = Direction.entries.firstOrNull { it.lastDirectionValue == lastDirection }

    }

    @Serializable
    data class Sys(
        val mac: Mac? = null,
        @SerialName("restart_required") val restartRequired: Boolean? = null
    )
}
