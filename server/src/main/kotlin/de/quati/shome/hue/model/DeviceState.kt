package de.quati.shome.hue.model

import kotlinx.serialization.Serializable

@Serializable
class DeviceState(
    val on: Boolean = false,
    val bri: Int = 254,
    val hue: Int = 15823,
    val sat: Int = 88,
    val effect: String = "none",
    val ct: Int = 313,
    val alert: String = "none",
    val colormode: String = "ct",
    val reachable: Boolean = true,
    val xy: List<Double> = listOf(0.4255, 0.3998),
)