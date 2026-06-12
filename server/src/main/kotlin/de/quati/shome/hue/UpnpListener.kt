package de.quati.shome.hue

import de.quati.shome.Const
import de.quati.shome.model.Host
import io.ktor.server.application.*
import kotlinx.coroutines.*
import java.net.*

private const val UPNP_DISCOVERY_PORT = 1900
private const val UPNP_RESPONSE_PORT = 50012
private const val UPNP_MULTICAST_ADDRESS = "239.255.255.250"

private fun discoveryResponse(host: Host) =
    "HTTP/1.1 200 OK\r\n" +
            "CACHE-CONTROL: max-age=86400\r\n" +
            "EXT:\r\n" +
            "LOCATION: http://$host:${Const.HUE_BRIDGE_PORT}/upnp/amazon-ha-bridge/setup.xml\r\n" +
            "OPT: \"http://schemas.upnp.org/upnp/1/0/\"; ns=01\r\n" +
            "01-NLS: D1710C33-328D-4152-A5FA-5382541A92FF\r\n" +
            "ST: urn:schemas-upnp-org:device:basic:1\r\n" +
            "USN: uuid:Socket-1_0-221438K0100073::urn:Belkin:device:**\r\n\r\n"

private fun isSSDPDiscovery(body: String): Boolean =
    body.startsWith("M-SEARCH * HTTP/1.1") && body.contains("MAN: \"ssdp:discover\"")


fun Application.startUpnpListener(host: Host) {
    val socketAddress = InetSocketAddress(UPNP_MULTICAST_ADDRESS, UPNP_DISCOVERY_PORT)

    launch(Dispatchers.IO) {
        log.info("Starting UPNP Discovery Listener")
        try {
            DatagramSocket(UPNP_RESPONSE_PORT).use { responseSocket ->
                MulticastSocket(UPNP_DISCOVERY_PORT).use { multicastSocket ->

                    NetworkInterface.getNetworkInterfaces().asSequence().forEach { iface ->
                        val ipv4Count = iface.inetAddresses.asSequence()
                            .count { it is Inet4Address }
                        log.debug("Checking ${iface.name} to our interface set")
                        if (ipv4Count > 0) {
                            multicastSocket.joinGroup(socketAddress, iface)
                            log.debug("Adding ${iface.name} to our interface set")
                        }
                    }

                    val buf = ByteArray(1024)
                    while (isActive) {
                        val packet = DatagramPacket(buf, buf.size)
                        multicastSocket.receive(packet)
                        val packetString = String(packet.data)
                        if (isSSDPDiscovery(packetString)) {
                            log.debug("Got SSDP Discovery packet from ${packet.address.hostAddress}:${packet.port}")
                            val response = discoveryResponse(host)
                            val bytes = response.toByteArray()
                            responseSocket.send(DatagramPacket(bytes, bytes.size, packet.address, packet.port))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("UpnpListener encountered an error. Shutting down", e)
            this@startUpnpListener.dispose()
        }
        log.info("UPNP Discovery Listener Stopped")
    }
}