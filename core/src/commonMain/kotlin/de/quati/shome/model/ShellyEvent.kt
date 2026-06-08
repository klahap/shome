package de.quati.shome.model

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant


@Serializable
data class ShellyEvent(
    val startTimeStamp: Instant,
    val startPos: Position,
    val direction: Direction?,
) {
    constructor(latestDirection: Direction) : this(
        startTimeStamp = Clock.System.now(),
        startPos = latestDirection.endPosition,
        direction = null,
    )

    fun at(
        shellyInfo: ShellyInfo.Valid,
        newTimeStamp: Instant,
    ): ShellyEvent {
        if (direction == null) // stopped -> start driving
            return copy(startTimeStamp = newTimeStamp)

        val distance = shellyInfo.computeDistance(
            direction = direction,
            duration = (newTimeStamp - startTimeStamp).absoluteValue,
        )
        val outOfBoundsDistance = startPos.computeOutOfBoundsDistance(distance)
        val newStartPos = startPos.compute(distance)
        return if (outOfBoundsDistance >= 0.0) // rollo should be stopped in endposition
            copy(
                startTimeStamp = newTimeStamp,
                startPos = newStartPos,
                direction = null,
            )
        else  // rollo should NOT be stopped in endposition
            copy(
                startTimeStamp = newTimeStamp,
                startPos = newStartPos,
            )
    }

    fun update(
        shellyInfo: ShellyInfo.Valid,
        newDirection: Direction,
        newTimeStamp: Instant
    ): ShellyEvent {
        if (direction == newDirection) return this // same direction, nothing changes
        val currentEvent = at(shellyInfo = shellyInfo, newTimeStamp = newTimeStamp)
        return when (currentEvent.direction) {
            newDirection -> currentEvent // same direction, nothing changes
            null -> {
                val isNothingToDrive = currentEvent.startPos.distanceLeft(newDirection).isApproxNull
                if (isNothingToDrive)
                    currentEvent // already in endposition of newDirection
                else
                    currentEvent.copy(direction = newDirection) // stopped -> start driving
            }

            else -> currentEvent.copy(direction = null)   // rollo should NOT be stopped in endposition, so stop
        }
    }
}
