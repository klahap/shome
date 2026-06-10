package de.quati.shome

import de.quati.shome.api.Api
import de.quati.shome.model.BackendIntent
import de.quati.shome.model.BackendState
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.resources.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readLine
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json


class BackendClient {
    private val json: Json = Json
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(Resources)
        install(ContentNegotiation) { json(json) }
        defaultRequest {
            host = "localhost"
            port = Const.BACKEND_PORT
        }
    }

    fun getStateFlow() = flow {
        httpClient.prepareGet(Api.State()).execute { response ->
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readLine() ?: break
                val state = json.decodeFromString<BackendState>(line)
                emit(state)
            }
        }
    }

    suspend fun sendIntent(intent: BackendIntent) {
        val res = httpClient.post(Api.Intent()) {
            setBody(intent)
        }
        if (!res.status.isSuccess())
            println("Failed to send intent: ${res.status.description}") // TODO logging
    }
}