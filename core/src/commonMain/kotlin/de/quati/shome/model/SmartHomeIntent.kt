package de.quati.shome.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant


@Serializable
sealed interface SmartHomeIntent {
    @Serializable
    data class FixShellyWebhooks(val mac: Mac) : SmartHomeIntent

    @Serializable
    object StartSearchShellysInSubnet : SmartHomeIntent

    @Serializable
    data class RemoveShelly(val mac: Mac) : SmartHomeIntent

    data class StartSearchShellys(val endpoints: Set<NetworkEndpoint>) : SmartHomeIntent

    @Serializable
    data class MoveTo(val mac: Mac, val pos: Position) : SmartHomeIntent

    @Serializable
    data class ShellyWebhookEvent(
        val mac: Mac,
        val event: WebhookEventType.Quati,
        val timestamp: Instant,
    ) : SmartHomeIntent {
        val direction: Direction = when (event) {
            WebhookEventType.CoverClosing -> Direction.CLOSE
            WebhookEventType.CoverOpening -> Direction.OPEN
        }
    }
}
