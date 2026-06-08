package de.quati.shome

import de.quati.shome.model.Ip
import java.net.Inet4Address
import java.net.NetworkInterface

fun getServerIp() = NetworkInterface.getNetworkInterfaces().asSequence()
    .filter { it.isUp && !it.isLoopback && !it.isVirtual }
    .flatMap { it.interfaceAddresses.asSequence() }
    .map { it.address }
    .filterIsInstance<Inet4Address>()
    .firstOrNull()?.hostAddress?.let(::Ip)
    ?: error("No valid IPv4 address found")
