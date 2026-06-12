package de.quati.shome.hue.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class DeviceResponse(
    val state: DeviceState = DeviceState(),
    val type: String = "Extended color light",
    val name: String,
    val modelid: String = "LCT001",
    val manufacturername: String = "Philips",
    val uniqueid: Uuid,
    val swversion: String = "65003148",
)