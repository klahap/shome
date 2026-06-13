package de.quati.shome

import de.quati.shome.model.BackendConfig
import de.quati.shome.model.Direction
import de.quati.shome.model.Host
import de.quati.shome.model.NetworkEndpoint
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ApplicationTest {

    @Test
    fun testRoot(): Unit = runBlocking {
        val endpoint = NetworkEndpoint.parse("192.168.178.122:8080")
        val service = ShellyService(
            BackendConfig.ContextImpl(
                BackendConfig(
                    backendEndpoint = endpoint,
                    backendIPv4 = endpoint.host as Host.IPv4
                )
            )
        )

        val shellyIp = NetworkEndpoint.parse("192.168.178.69")
        //service.findShelly(shellyIp)

        service.coverDrive(shellyIp, direction = Direction.CLOSE)
    }

    @Test
    fun testSearchDevices(): Unit = runBlocking {
        searchDevices().collect(::println)
    }
}
