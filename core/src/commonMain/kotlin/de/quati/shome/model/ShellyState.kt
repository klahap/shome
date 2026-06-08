package de.quati.shome.model

import kotlinx.serialization.Serializable


@Serializable
data class ShellyState(
    val info: ShellyInfo,
    val latestEvent: ShellyEvent,
) {
    fun update(intent: SmartHomeIntent.ShellyWebhookEvent): ShellyState {
        if (info !is ShellyInfo.Valid) return this
        val newEvent = latestEvent.update(
            shellyInfo = info,
            newDirection = intent.direction,
            newTimeStamp = intent.timestamp,
        )
        return copy(latestEvent = newEvent)
    }
}
