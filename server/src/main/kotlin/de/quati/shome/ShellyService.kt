package de.quati.shome

import de.quati.shome.model.Direction
import de.quati.shome.model.NetworkEndpoint
import de.quati.shome.model.ShellyState
import de.quati.shome.model.WebhookEventType
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import de.quati.shome.model.BackendConfig
import de.quati.shome.model.ShellyRpcMethod
import de.quati.shome.model.ShellyRpcRequest
import de.quati.shome.model.ShellyRpcResponse
import io.ktor.client.call.body
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement


class ShellyService(
    backendConfigContext: BackendConfig.Context
) : BackendConfig.Context by backendConfigContext {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 5_000
        }
    }

    suspend fun getConfig(endpoint: NetworkEndpoint) =
        execute<ShellyRpcResponse.Params.ShellyConfig>(endpoint = endpoint, method = ShellyRpcMethod.SHELLY_GET_CONFIG)

    suspend fun getStatus(endpoint: NetworkEndpoint) =
        execute<ShellyRpcResponse.Params.ShellyStatus>(endpoint = endpoint, method = ShellyRpcMethod.SHELLY_GET_STATUS)

    suspend fun getManyKvs(endpoint: NetworkEndpoint) =
        execute<ShellyRpcResponse.Params.KvsGetMany>(endpoint = endpoint, method = ShellyRpcMethod.KVS_GET_MANY)

    suspend fun setKvs(ip: NetworkEndpoint, entry: ShellyRpcRequest.Params.KvsEntry) = request(
        ip = ip,
        met = ShellyRpcMethod.KVS_SET,
        params = entry,
    )

    suspend fun deleteWebhook(ip: NetworkEndpoint, webhookId: Int) = request(
        ip = ip,
        met = ShellyRpcMethod.WEBHOOK_DELETE,
        params = ShellyRpcRequest.Params.WebhookDelete(
            id = webhookId
        ),
    )

    suspend fun createWebhook(ip: NetworkEndpoint, eventType: WebhookEventType) = request(
        ip = ip,
        met = ShellyRpcMethod.WEBHOOK_CREATE,
        params = ShellyRpcRequest.Params.Webhook(
            cid = 0,
            enable = true,
            event = eventType,
            name = eventType.prettyName,
            urls = listOf(backendConfig.webhookUrl(eventType)),
        ),
    )

    suspend fun listWebhooks(ip: NetworkEndpoint) =
        execute<ShellyRpcResponse.Params.WebhookList>(endpoint = ip, method = ShellyRpcMethod.WEBHOOK_LIST)

    suspend fun fixWebhooks(ip: NetworkEndpoint) {
        val webhooks = listWebhooks(ip).getOrThrow()
        if (webhooks.isValid()) return
        webhooks.hooks
            .filter { it.isQuatiWebhook }
            .map { it.id }
            .forEach {
                deleteWebhook(ip = ip, webhookId = it).getOrThrow()
            }
        WebhookEventType.entries.forEach {
            createWebhook(ip = ip, eventType = it).getOrThrow()
        }
    }

    suspend fun coverDrive(ip: NetworkEndpoint, direction: Direction) = request(
        ip = ip,
        met = when (direction) {
            Direction.OPEN -> ShellyRpcMethod.COVER_OPEN
            Direction.CLOSE -> ShellyRpcMethod.COVER_CLOSE
        },
        params = ShellyRpcRequest.Params.CoverDrive(
            id = 0,
            tag = "Quati",
        ),
    )

    suspend fun findShelly(ip: NetworkEndpoint): ShellyState? {
        val status = getStatus(ip).getOrNull() ?: return null
        val config = getConfig(ip).getOrNull() ?: return null
        val kvs = getManyKvs(ip).getOrNull() ?: return null
        val webhooks = listWebhooks(ip).getOrNull() ?: return null
        val info = ShellyState.Invalid(
            endpoint = ip,
            mac = status.sys?.mac ?: return null,
            name = config.sys?.device?.name,
            profile = config.sys?.device?.profile ?: return null,
            webhooksValid = webhooks.isValid(),
            totalDurationClose = kvs.totalDuration(Direction.CLOSE),
            totalDurationOpen = kvs.totalDuration(Direction.OPEN),
            latestEvent = null,
            latestDirection = status.cover0?.lastDirectionTyped,
        ).tryToValid()
        return info
    }

    suspend fun findAllShellys(endpoints: Set<NetworkEndpoint>) = coroutineScope {
        val semaphore = Semaphore(32)
        endpoints.map {
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    findShelly(it)
                }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend inline fun <reified T> HttpResponse.parse(): T {
        val response = body<ShellyRpcResponse>()
        response.error?.also {
            throw Exception("Shelly error: ${it.message ?: "Unknown error"}")
        }
        return json.decodeFromJsonElement<T>(response.params ?: throw Exception("No params in response"))
    }

    private suspend inline fun request(
        ip: NetworkEndpoint,
        met: ShellyRpcMethod,
    ) = request(ip, met, null)

    private suspend inline fun <reified P : ShellyRpcRequest.Params> request(
        ip: NetworkEndpoint,
        met: ShellyRpcMethod,
        params: P?,
    ) = httpClient.post("http://$ip/rpc") {
        contentType(ContentType.Application.Json)
        setBody(
            ShellyRpcRequest.create(
                id = 1,
                method = met,
                params = params
            )
        )
    }.let {
        if (it.status.isSuccess())
            Result.success(it)
        else
            Result.failure(Exception(it.bodyAsText().takeIf { it.isNotBlank() } ?: "Unknown error"))
    }

    private suspend inline fun <reified T> execute(
        endpoint: NetworkEndpoint,
        method: ShellyRpcMethod,
    ): Result<T> = execute(
        endpoint = endpoint,
        method = method,
        params = null
    )

    private suspend inline fun <reified P : ShellyRpcRequest.Params, reified T> execute(
        endpoint: NetworkEndpoint,
        method: ShellyRpcMethod,
        params: P?,
    ): Result<T> = request(ip = endpoint, met = method, params = params)
        .mapCatching { it.parse() }
}
