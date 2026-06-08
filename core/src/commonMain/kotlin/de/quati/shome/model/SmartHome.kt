package de.quati.shome.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant


@Serializable
data class SmartHomeState(
    val isSearchingShellys: Boolean = false,
    val shellys: Map<Mac, ShellyState> = emptyMap(),
)


@Serializable
sealed interface SmartHomeIntent {
    @Serializable
    data class FixShellyWebhooks(val mac: Mac) : SmartHomeIntent

    @Serializable
    object StartSearchShellys : SmartHomeIntent

    @Serializable
    data class MoveTo(val mac: Mac, val pos: Position) : SmartHomeIntent

    @Serializable
    data class ShellyWebhookEvent(
        val mac: Mac,
        val event: WebhookEventType,
        val timestamp: Instant,
    ) : SmartHomeIntent {
        val direction: Direction = when (event) {
            WebhookEventType.COVER_CLOSING -> Direction.CLOSE
            WebhookEventType.COVER_OPENING -> Direction.OPEN
        }
    }
}
