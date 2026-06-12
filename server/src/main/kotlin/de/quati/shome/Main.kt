package de.quati.shome

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import de.quati.shome.model.Host
import de.quati.shome.model.NetworkEndpoint
import de.quati.shome.model.BackendConfig
import de.quati.shome.model.BackendIntent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.application.log

/*

#!/bin/bash

IP=$(ifconfig | grep 'inet ' | grep -v '127.0.0.1' | awk '{print $2}' | head -1)
./CMD --host "$IP" --port 8080

*/

class MainCmd : SuspendingCliktCommand() {
    private val host by option(help = "IP or hostname to listen on").convert { Host.parse(it) }
    val shellySearchEndpoints by option(help = "Endpoints to search for shellys")
        .convert { NetworkEndpoint.parse(it) }.multiple()

    val backendConfigContext by lazy {
        val ip = (host as? Host.IPv4) ?: backendIp
        BackendConfig.Context.create(
            ip = ip,
            endpoint = NetworkEndpoint(
                host = host ?: ip,
                port = Const.BACKEND_PORT
            ),
        )
    }

    override suspend fun run() {
        embeddedServer(
            factory = io.ktor.server.cio.CIO,
            port = Const.BACKEND_PORT,
            host = "0.0.0.0",
            module = {
                rootModule(cmd = this@MainCmd)
                log.info("Responding at http://${backendConfigContext.backendConfig.backendEndpoint}")
            }
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
    disableCors()
    addController(backendStateService = backendStateService)

    cmd.shellySearchEndpoints.toSet().takeIf { it.isNotEmpty() }?.also {
        backendStateService.onIntent(BackendIntent.StartSearchShellys(it))
    }
}