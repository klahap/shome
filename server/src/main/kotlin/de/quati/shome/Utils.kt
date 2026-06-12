package de.quati.shome

import de.quati.shome.model.Host
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import java.net.Inet4Address
import java.net.NetworkInterface

private fun Inet4Address.toHost(): Host.IPv4 {
    val b = address
    return Host.IPv4(b[0].toUByte(), b[1].toUByte(), b[2].toUByte(), b[3].toUByte())
}

val backendIp by lazy {
    NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .flatMap { it.interfaceAddresses.asSequence() }
        .map { it.address }
        .filterIsInstance<Inet4Address>()
        .map { it.toHost() }
        .firstOrNull()
        ?: error("No valid IPv4 address found")
}

fun Application.disableCors() = install(CORS) {
    anyHost()
    HttpMethod.DefaultMethods.forEach {
        allowMethod(it)
    }
    allowHeader(HttpHeaders.ContentType)
    allowHeader(HttpHeaders.Authorization)
}
