package de.quati.shome.hue.model

import kotlinx.serialization.Serializable

@Serializable
data class HueApiResponse(
    val lights: Map<String, DeviceResponse> = emptyMap(),
)