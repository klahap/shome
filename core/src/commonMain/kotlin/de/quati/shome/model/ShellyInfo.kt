package de.quati.shome.model

import kotlinx.serialization.Serializable
import kotlin.time.Duration


@Serializable
sealed interface ShellyInfo {
    val mac: Mac
    val endpoint: NetworkEndpoint
    val name: String?
    val totalDurationClose: Duration?
    val totalDurationOpen: Duration?

    fun updateInvalid(block: (Invalid) -> ShellyInfo): ShellyInfo = when (this) {
        is Valid -> this
        is Invalid -> block(this).tryToValid()
    }

    fun tryToValid(): ShellyInfo = when (this) {
        is Valid -> this
        is Invalid -> toValidOrNull() ?: this
    }

    @Serializable
    data class Valid(
        override val mac: Mac,
        override val endpoint: NetworkEndpoint,
        override val name: String?,
        override val totalDurationClose: Duration,
        override val totalDurationOpen: Duration,
    ) : ShellyInfo {
        fun totalDuration(direction: Direction) = when (direction) {
            Direction.CLOSE -> totalDurationClose
            Direction.OPEN -> totalDurationOpen
        }

        fun computeDistance(direction: Direction, duration: Duration) = Distance(
            direction.sign * (duration.absoluteValue / totalDuration(direction).absoluteValue)
        )


        fun computeDuration(direction: Direction, distance: Distance): Duration =
            (totalDuration(direction) * distance.value).absoluteValue

    }

    @Serializable
    data class Invalid(
        override val endpoint: NetworkEndpoint,
        override val mac: Mac,
        override val name: String?,
        override val totalDurationClose: Duration?,
        override val totalDurationOpen: Duration?,
        val profile: String,
        val webhooksValid: Boolean,
    ) : ShellyInfo {
        fun toValidOrNull(): Valid? {
            if (profile != "cover") return null
            if (!webhooksValid) return null
            return Valid(
                endpoint = endpoint,
                mac = mac,
                name = name,
                totalDurationClose = totalDurationClose ?: return null,
                totalDurationOpen = totalDurationOpen ?: return null,
            )
        }

    }
}

