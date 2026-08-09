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
import io.ktor.resources.href
import io.ktor.resources.serialization.ResourcesFormat
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import io.ktor.utils.io.readLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

expect fun getHost(): String
expect fun getPort(): Int?
expect fun openUrl(url: String)

class AppViewModel : ViewModel() {
    private val json: Json = Json
    private val httpClient: HttpClient = HttpClient {
        install(Resources)
        install(ContentNegotiation) { json(json) }
        defaultRequest {
            host = getHost()
            getPort()?.also { port = it }
        }
    }

    val errors: SharedFlow<String>
        field = MutableSharedFlow(extraBufferCapacity = 64)

    val state = MutableStateFlow(
        BackendState(
            otfState = BackendState.OtfState.DISABLED,
        )
    ).also { stateFlow ->
        viewModelScope.launch {
            while (true) {
                try {
                    println("fetching state...")
                    httpClient.prepareGet(Api.State()).execute { response ->
                        if (!response.status.isSuccess()) {
                            errors.tryEmit("Connection error: ${response.status}")
                            return@execute
                        }
                        val channel = response.bodyAsChannel()
                        while (!channel.isClosedForRead) {
                            val line = channel.readLine() ?: break
                            stateFlow.value = json.decodeFromString<BackendState>(line)
                        }
                    }
                } catch (e: CancellationException) {
                    println("cancel")
                    throw e
                } catch (t: Throwable) {
                    errors.tryEmit("Connection error: ${t.message ?: t.toString()}")
                }
                println("retrying in 2s...")
                delay(2000.milliseconds)
            }
        }
    }.asStateFlow()

    fun sendIntent(intent: BackendIntent) = viewModelScope.launch {
        val res = try {
            httpClient.post(Api.Intent()) {
                contentType(ContentType.Application.Json)
                setBody(intent)
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            errors.tryEmit("Failed to send command: ${t.message ?: t.toString()}")
            return@launch
        }
        if (!res.status.isSuccess())
            errors.tryEmit("Command failed with status: ${res.status}")
    }

    init {
        state.map { it.latestBackendError }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach {
                errors.tryEmit(it.second)
                sendIntent(BackendIntent.ClearError(it.first))
            }.launchIn(viewModelScope)
    }

    fun downloadLogs() {
        openUrl(href(ResourcesFormat(), Api.Logs()))
    }
}