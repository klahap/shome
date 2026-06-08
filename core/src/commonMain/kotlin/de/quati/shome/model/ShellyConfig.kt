package de.quati.shome.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds


@Serializable
data class ShellyConfig(
    @SerialName("cover:0") val cover0: Cover0? = null,
    val sys: Sys? = null,
): ShellyRpcResponse.Params {
    @Serializable
    data class Cover0(
        @SerialName("maxtime_open") val maxtimeOpen: Double,
        @SerialName("maxtime_close") val maxtimeClose: Double,
        @SerialName("swap_inputs") val swapInputs: Boolean? = null,
        @SerialName("invert_directions") val invertDirections: Boolean,
    ) {
        val maxtimeOpenDuration get() = maxtimeOpen.seconds
        val maxtimeCloseDuration get() = maxtimeClose.seconds
    }

    @Serializable
    data class Sys(
        val device: Device? = null,
    ) {
        @Serializable
        data class Device(
            @SerialName("name") val name: String? = null,
            @SerialName("mac") val mac: Mac? = null,
            @SerialName("profile") val profile: String? = null,
        )
    }
}
