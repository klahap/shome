package de.quati.shome

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import de.quati.shome.model.Host
import de.quati.shome.model.NetworkEndpoint
import de.quati.shome.model.BackendConfig
import de.quati.shome.model.BackendIntent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

/*

#!/bin/bash

IP=$(ifconfig | grep 'inet ' | grep -v '127.0.0.1' | awk '{print $2}' | head -1)
./CMD --host "$IP" --port 8080

*/

class MainCmd : SuspendingCliktCommand() {
    private val host by option(help = "IP or hostname to listen on").convert { Host.parse(it) }
    private val port by option(help = "Port to listen on").int().default(8080)
    val shellySearchEndpoints by option(help = "Endpoints to search for shellys")
        .convert { NetworkEndpoint.parse(it) }.multiple()

    val backendConfigContext by lazy {
        val ip = (host as? Host.IPv4) ?: getServerIp()
        BackendConfig.Context.create(
            ip = ip,
            endpoint = NetworkEndpoint(
                host = host ?: ip,
                port = port
            ),
        )
    }

    override suspend fun run() {
        embeddedServer(
            factory = io.ktor.server.cio.CIO,
            port = port,
            host = backendConfigContext.backendConfig.backendEndpoint.host.toString(),
            module = { rootModule(cmd = this@MainCmd) }
        ).startSuspend(wait = true)
    }
}

suspend fun main(args: Array<String>) = MainCmd().main(args)

suspend fun Application.rootModule(cmd: MainCmd) {
    val serverConfigContext = cmd.backendConfigContext
    val shellyService = ShellyService(backendConfigContext = serverConfigContext)
    val backendStateService = BackendStateService(
        app = this,
        backendConfigContext = serverConfigContext,
        shellyService = shellyService,
    )

    install(ContentNegotiation) {
        json()
    }
    addController(backendStateService = backendStateService)

    cmd.shellySearchEndpoints.toSet().takeIf { it.isNotEmpty() }?.also {
        backendStateService.onIntent(BackendIntent.StartSearchShellys(it))
    }
}