package de.quati.shome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.quati.shome.api.Api
import de.quati.shome.model.BackendIntent
import de.quati.shome.model.BackendMessage
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

expect fun getHost(): String
expect fun getPort(): Int?
expect fun openUrl(url: String)

private val STATE_STREAM_TIMEOUT = 40.seconds

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

    val notifications: SharedFlow<BackendMessage.Notification>
        field = MutableSharedFlow(
            extraBufferCapacity = 16,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val state: StateFlow<BackendMessage.State>
        field = MutableStateFlow(BackendMessage.State())

    fun error(msg: String) = notifications.tryEmit(BackendMessage.Error(msg))

    fun sendIntent(intent: BackendIntent) = viewModelScope.launch {
        val res = try {
            httpClient.post(Api.Intent()) {
                contentType(ContentType.Application.Json)
                setBody(intent)
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            error("Failed to send command: ${t.message ?: t.toString()}")
            return@launch
        }
        if (!res.status.isSuccess())
            error("Command failed with status: ${res.status}")
    }

    init {
        viewModelScope.launch {
            val retryDelay = RetryDelay()
            while (true) {
                try {
                    println("fetching state...")
                    httpClient.prepareGet(Api.State()).execute { response ->
                        if (!response.status.isSuccess()) {
                            error("Connection error: ${response.status}")
                            return@execute
                        }
                        val channel = response.bodyAsChannel()
                        while (!channel.isClosedForRead) {
                            val line = try {
                                withTimeout(STATE_STREAM_TIMEOUT) { channel.readLine() }
                            } catch (_: TimeoutCancellationException) {
                                throw Exception("no data received from /api/state in time")
                            } ?: break
                            retryDelay.reset()
                            when (val msg = json.decodeFromString<BackendMessage>(line)) {
                                is BackendMessage.State -> state.emit(msg)
                                BackendMessage.Heartbeat -> Unit
                                is BackendMessage.Notification -> notifications.emit(msg)
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    println("cancel")
                    throw e
                } catch (t: Throwable) {
                    error("Connection error: ${t.message ?: t.toString()}")
                }
                println("retrying in ${retryDelay.currentDelay}...")
                retryDelay.wait()
            }
        }
    }

    fun downloadLogs() {
        openUrl(href(ResourcesFormat(), Api.Logs()))
    }

    fun showTodaysLogs() {
        openUrl(href(ResourcesFormat(), Api.LogsToday()))
    }
}