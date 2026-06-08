package de.quati.shome

import de.quati.shome.model.Host
import java.net.Inet4Address
import java.net.NetworkInterface

fun Inet4Address.toHost(): Host.IPv4 {
    val b = address
    return Host.IPv4(b[0].toUByte(), b[1].toUByte(), b[2].toUByte(), b[3].toUByte())
}

fun getServerIp() = NetworkInterface.getNetworkInterfaces().asSequence()
    .filter { it.isUp && !it.isLoopback && !it.isVirtual }
    .flatMap { it.interfaceAddresses.asSequence() }
    .map { it.address }
    .filterIsInstance<Inet4Address>()
    .map { it.toHost() }
    .firstOrNull()
    ?: error("No valid IPv4 address found")
