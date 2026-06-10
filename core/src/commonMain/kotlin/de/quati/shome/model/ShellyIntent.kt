package de.quati.shome.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
sealed interface ShellyIntent {
    @Serializable
    data object FixWebhooks : ShellyIntent

    @Serializable
    data object Delete : ShellyIntent

    @Serializable
    data class MoveTo(val pos: Position) : ShellyIntent

    @Serializable
    data class WebhookEventReceived(
        val event: WebhookEventType.Quati,
        val timestamp: Instant,
    ) : ShellyIntent {
        val direction: Direction = when (event) {
            WebhookEventType.CoverClosing -> Direction.CLOSE
            WebhookEventType.CoverOpening -> Direction.OPEN
        }
    }

}