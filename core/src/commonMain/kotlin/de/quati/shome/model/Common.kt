package de.quati.shome.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.math.absoluteValue


@JvmInline
@Serializable
value class Ip(val value: String) {
    override fun toString() = value
}

@JvmInline
@Serializable
value class Mac(val value: String) {
    override fun toString() = value
}

@JvmInline
@Serializable
value class Distance(val value: Double) {
    val isApproxNull get() = value.absoluteValue < 1e-5
}


@JvmInline
@Serializable
value class Position(val value: Double) {
    fun compute(distance: Distance) = (value + distance.value).coerceIn(0.0, 1.0).let(::Position)

    fun distanceLeft(direction: Direction) = when (direction) {
        Direction.CLOSE -> (CLOSED.value - value.coerceIn(0.0, 1.0)).absoluteValue
        Direction.OPEN -> (OPENED.value - value.coerceIn(0.0, 1.0)).absoluteValue
    }.let(::Distance)

    fun distanceAndDirectionTo(other: Position): Pair<Distance, Direction>? {
        val distance = (other.value.coerceIn(0.0, 1.0) - value.coerceIn(0.0, 1.0)).absoluteValue.let(::Distance)
        if (distance.isApproxNull) return null
        val direction = when {
            other.value > value -> Direction.CLOSE
            else -> Direction.OPEN
        }
        return distance to direction
    }

    fun isApprox(other: Position) = (value - other.value).absoluteValue < 0.01
    fun isApproxEnd() = isApprox(Position.CLOSED) || isApprox(Position.OPENED)

    fun computeOutOfBoundsDistance(distance: Distance): Double {
        val x = value + distance.value
        return if (x > 0.5)
            x - 1.0
        else
            -x
    }

    companion object {
        val OPENED = Position(0.0)
        val CLOSED = Position(1.0)
    }
}


enum class Direction {
    CLOSE, OPEN;

    val opposite
        get() = when (this) {
            CLOSE -> OPEN
            OPEN -> CLOSE
        }

    val endPosition
        get() = when (this) {
            CLOSE -> Position.CLOSED
            OPEN -> Position.OPENED
        }

    val sign
        get() = when (this) {
            CLOSE -> 1
            OPEN -> -1
        }
}

enum class WebhookEventType(val value: String) {
    COVER_OPENING("cover.opening"),
    COVER_CLOSING("cover.closing");

    val urlName get() = value.replace('.', '_')
    val prettyName get() = NAME_PREFIX + urlName

    companion object {
        const val NAME_PREFIX = "quati_"
        const val QUERY_KEY_MAC = "mac"
        const val QUERY_KEY_EVENT = "event"

        fun parseUrlName(value: String): WebhookEventType? = entries.find { it.urlName == value }
    }
}
