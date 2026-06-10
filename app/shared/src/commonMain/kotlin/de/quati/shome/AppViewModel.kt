package de.quati.shome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.quati.shome.api.Api
import de.quati.shome.model.BackendIntent
import de.quati.shome.model.BackendState
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.resources.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.readLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

expect fun getHost(): String

class AppViewModel : ViewModel() {
    private val json: Json = Json
    private val httpClient: HttpClient = HttpClient {
        install(Resources)
        install(ContentNegotiation) { json(json) }
        defaultRequest {
            host = getHost()
            port = Const.BACKEND_PORT
        }
    }

    val errors: SharedFlow<String>
        field = MutableSharedFlow()

    val state = MutableStateFlow(BackendState()).also { stateFlow ->
        viewModelScope.launch {
            while (true) {
                try {
                    httpClient.prepareGet(Api.State()).execute { response ->
                        val channel = response.bodyAsChannel()
                        while (!channel.isClosedForRead) {
                            val line = channel.readLine() ?: break
                            stateFlow.value = json.decodeFromString<BackendState>(line)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errors.emit("Connection error: ${e.message}")
                    delay(2000.milliseconds)
                }
            }
        }
    }.asStateFlow()

    fun sendIntent(intent: BackendIntent) = viewModelScope.launch {
        val res = try {
            httpClient.post(Api.Intent()) {
                contentType(ContentType.Application.Json)
                setBody(intent)
            }
        } catch (e: Exception) {
            errors.emit("Failed to send command: ${e.message}")
            return@launch
        }
        if (!res.status.isSuccess())
            errors.emit("Command failed with status: ${res.status}")
    }
}