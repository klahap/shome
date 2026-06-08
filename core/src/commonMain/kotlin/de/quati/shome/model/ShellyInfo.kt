package de.quati.shome.model

import kotlinx.serialization.Serializable
import kotlin.time.Duration


@Serializable
sealed interface ShellyInfo {
    companion object {
        private const val VALID_PROFILE = "cover"
    }

    val mac: Mac
    val ip: Ip
    val name: String?
    val profile: String
    val webhooksValid: Boolean
    val totalDurationClose: Duration?
    val totalDurationOpen: Duration?

    fun updateInvalid(block: (Invalid) -> ShellyInfo): ShellyInfo = when (this) {
        is Valid -> this
        is Invalid -> block(this).tryToValid()
    }

    fun tryToValid(): ShellyInfo = when (this) {
        is Valid -> this
        is Invalid -> {
            if (profile != VALID_PROFILE) return this
            if (!webhooksValid) return this
            return Valid(
                ip = ip,
                mac = mac,
                name = name,
                totalDurationClose = totalDurationClose ?: return this,
                totalDurationOpen = totalDurationOpen ?: return this,
            )
        }
    }


    @Serializable
    data class Valid(
        override val mac: Mac,
        override val ip: Ip,
        override val name: String?,
        override val totalDurationClose: Duration,
        override val totalDurationOpen: Duration,
    ) : ShellyInfo {
        override val profile get() = VALID_PROFILE
        override val webhooksValid get() = true
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
        override val ip: Ip,
        override val mac: Mac,
        override val name: String?,
        override val profile: String,
        override val webhooksValid: Boolean,
        override val totalDurationClose: Duration?,
        override val totalDurationOpen: Duration?,
    ) : ShellyInfo
}

