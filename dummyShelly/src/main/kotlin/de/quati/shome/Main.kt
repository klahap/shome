package de.quati.shome

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import de.quati.shome.model.Mac
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.coroutines.awaitCancellation

/*

#!/bin/bash

IP=$(ifconfig | grep 'inet ' | grep -v '127.0.0.1' | awk '{print $2}' | head -1)
./CMD --host "$IP" --port 8080

*/

class MainCmd : SuspendingCliktCommand() {
    val host by option(help = "Host to listen on")
    val ports by option(help = "Ports to listen on").int().multiple(required = true)

    override suspend fun run() {
        ports.distinct().forEach { port ->
            embeddedServer(
                factory = io.ktor.server.cio.CIO,
                port = port,
                host = host ?: "0.0.0.0",
                module = { rootModule(mac = Mac("mac-$port")) }
            ).startSuspend(wait = false)
        }
        awaitCancellation()
    }
}

suspend fun main(args: Array<String>) = MainCmd().main(args)

fun Application.rootModule(mac: Mac) {
    install(ContentNegotiation) { json() }
    addController(mac = mac)
}