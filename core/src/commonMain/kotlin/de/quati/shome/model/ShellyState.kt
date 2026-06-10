package de.quati.shome.model

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant


@Serializable
sealed interface ShellyState {
    val mac: Mac
    val endpoint: NetworkEndpoint
    val name: String?
    val totalDurationClose: Duration?
    val totalDurationOpen: Duration?
    val latestEvent: Event?

    fun update(newState: ShellyState?): ShellyState {
        if (newState == null) return this
        val oldEvent = latestEvent // try to keep the old event, it is more likely to be correct
        return when (newState) {
            is Invalid -> newState.copy(latestEvent = oldEvent ?: newState.latestEvent)
            is Valid -> newState.copy(latestEvent = oldEvent ?: newState.latestEvent)
        }
    }

    fun update(intent: ShellyIntent.WebhookEventReceived): ShellyState = when (this) {
        is Invalid -> this
        is Valid -> update(
            newTimeStamp = intent.timestamp,
            newDirection = intent.direction,
        )
    }

    fun updateInvalid(block: (Invalid) -> ShellyState): ShellyState = when (this) {
        is Valid -> this
        is Invalid -> block(this).tryToValid()
    }

    fun tryToValid(): ShellyState = when (this) {
        is Valid -> this
        is Invalid -> toValidOrNull() ?: this
    }

    @Serializable
    data class Event(
        val timeStamp: Instant,
        val position: Position,
        val direction: Direction?,
    )

    @Serializable
    data class Valid(
        override val mac: Mac,
        override val endpoint: NetworkEndpoint,
        override val name: String?,
        override val totalDurationClose: Duration,
        override val totalDurationOpen: Duration,
        override val latestEvent: Event,
    ) : ShellyState {
        fun totalDuration(direction: Direction) = when (direction) {
            Direction.CLOSE -> totalDurationClose
            Direction.OPEN -> totalDurationOpen
        }

        fun computeDistance(direction: Direction, duration: Duration) = Distance(
            direction.sign * (duration.absoluteValue / totalDuration(direction).absoluteValue)
        )

        fun computeDuration(direction: Direction, distance: Distance): Duration =
            (totalDuration(direction) * distance.value).absoluteValue

        fun update(
            newTimeStamp: Instant,
        ): Valid {
            if (latestEvent.direction == null) // stopped -> start driving
                return copy(latestEvent = latestEvent.copy(timeStamp = newTimeStamp))

            val distance = computeDistance(
                direction = latestEvent.direction,
                duration = (newTimeStamp - latestEvent.timeStamp).absoluteValue,
            )
            val outOfBoundsDistance = latestEvent.position.computeOutOfBoundsDistance(distance)
            val newStartPos = latestEvent.position.compute(distance)
            return if (outOfBoundsDistance >= 0.0) // rollo should be stopped in endposition
                copy(
                    latestEvent = latestEvent.copy(
                        timeStamp = newTimeStamp,
                        position = newStartPos,
                        direction = null,
                    )
                )
            else  // rollo should NOT be stopped in endposition
                copy(
                    latestEvent = latestEvent.copy(
                        timeStamp = newTimeStamp,
                        position = newStartPos,
                    )
                )
        }

        fun update(newDirection: Direction): Valid = when (latestEvent.direction) {
            newDirection -> this // same direction, nothing changes
            null -> {
                val isNothingToDrive = latestEvent.position.distanceLeft(newDirection).isApproxNull
                if (isNothingToDrive)
                    this // already in endposition of newDirection
                else
                    copy(
                        latestEvent = latestEvent.copy(direction = newDirection),
                    ) // stopped -> start driving
            }

            else -> copy(
                latestEvent = latestEvent.copy(direction = null),
            )   // rollo should NOT be stopped in endposition, so stop
        }

        fun update(
            newDirection: Direction,
            newTimeStamp: Instant
        ): Valid = update(newTimeStamp = newTimeStamp)
            .update(newDirection = newDirection)

    }

    @Serializable
    data class Invalid(
        override val endpoint: NetworkEndpoint,
        override val mac: Mac,
        override val name: String?,
        override val totalDurationClose: Duration?,
        override val totalDurationOpen: Duration?,
        val latestDirection: Direction?,
        override val latestEvent: Event?,
        val profile: String,
        val webhooksValid: Boolean,
    ) : ShellyState {
        fun toValidOrNull(): Valid? {
            if (profile != "cover") return null
            if (!webhooksValid) return null
            return Valid(
                endpoint = endpoint,
                mac = mac,
                name = name,
                totalDurationClose = totalDurationClose ?: return null,
                totalDurationOpen = totalDurationOpen ?: return null,
                latestEvent = latestEvent ?: Event(
                    timeStamp = Clock.System.now(),
                    position = latestDirection?.endPosition ?: Position.CLOSED,
                    direction = null,
                ),
            )
        }

    }
}
