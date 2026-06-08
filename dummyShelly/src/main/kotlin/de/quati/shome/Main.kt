package de.quati.shome

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import de.quati.shome.model.Mac
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
    val host by option(help = "Host to listen on")
    val port by option(help = "Port to listen on").int().default(8085)
    val mac by option(help = "MAC id")
    val macValue get() = Mac(mac ?: "mac-$port")

    override suspend fun run() {
        embeddedServer(
            factory = io.ktor.server.cio.CIO,
            port = port,
            host = host ?: "0.0.0.0",
            module = { rootModule(cmd = this@MainCmd) }
        ).startSuspend(wait = true)
    }
}

suspend fun main(args: Array<String>) = MainCmd().main(args)

fun Application.rootModule(cmd: MainCmd) {
    install(ContentNegotiation) { json() }
    addController(mac = cmd.macValue)
}