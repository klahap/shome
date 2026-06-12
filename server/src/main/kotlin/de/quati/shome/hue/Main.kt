package de.quati.shome.hue

import de.quati.shome.Const
import de.quati.shome.backendIp
import de.quati.shome.disableCors
import de.quati.shome.model.Host
import io.ktor.server.engine.embeddedServer


fun createHueBridgeEmbeddedServer(host: Host = backendIp) = embeddedServer(
    factory = io.ktor.server.cio.CIO,
    port = Const.HUE_BRIDGE_PORT,
    host = "0.0.0.0",
    module = {
        disableCors()
        hueRoutes()
        startUpnpListener(host)
    }
)

fun main() {
    createHueBridgeEmbeddedServer().start(wait = true)
}
