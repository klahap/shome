package de.quati.shome

import de.quati.shome.model.Direction
import de.quati.shome.model.Ip
import de.quati.shome.model.KvsEntry
import de.quati.shome.model.KvsGetMany
import de.quati.shome.model.ShellyConfig
import de.quati.shome.model.ShellyEvent
import de.quati.shome.model.ShellyInfo
import de.quati.shome.model.ShellyState
import de.quati.shome.model.ShellyStatus
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
import de.quati.shome.model.ServerConfig
import de.quati.shome.model.ShellyWebhooks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put


class ShellyService(
    serverConfigContext: ServerConfig.Context
) : ServerConfig.Context by serverConfigContext {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 5_000
        }
    }

    suspend fun getConfig(ip: Ip) = execute<JsonObject>(ip = ip, method = "Shelly.GetConfig").map(::ShellyConfig)
    suspend fun getStatus(ip: Ip) = execute<JsonObject>(ip = ip, method = "Shelly.GetStatus").map(::ShellyStatus)
    suspend fun getManyKvs(ip: Ip) = execute<KvsGetMany>(ip = ip, method = "KVS.GetMany")
    suspend fun setKvs(ip: Ip, entry: KvsEntry) = request(
        ip = ip,
        met = "KVS.Set",
        params = json.encodeToJsonElement(entry).jsonObject,
    )

    suspend fun deleteWebhook(ip: Ip, webhookId: Int) = request(
        ip = ip,
        met = "Webhook.Delete",
        params = buildJsonObject {
            put("id", webhookId)
        },
    )

    suspend fun createWebhook(ip: Ip, eventType: WebhookEventType) = request(
        ip = ip,
        met = "Webhook.Create",
        params = buildJsonObject {
            put("cid", 0)
            put("enable", true)
            put("event", eventType.value)
            put("name", eventType.prettyName)
            put("urls", buildJsonArray {
                add(serverConfig.webhookUrl(eventType))
            })
        },
    )

    suspend fun listWebhooks(ip: Ip) = execute<JsonObject>(ip = ip, method = "Webhook.List")
        .map(::ShellyWebhooks)

    suspend fun fixWebhooks(ip: Ip) {
        val webhooks = listWebhooks(ip).getOrThrow()
        if (webhooks.isValid()) return
        webhooks.quatiHooks.mapNotNull { it.id }.forEach {
            deleteWebhook(ip = ip, webhookId = it).getOrThrow()
        }
        WebhookEventType.entries.forEach {
            createWebhook(ip = ip, eventType = it).getOrThrow()
        }
    }

    suspend fun coverDrive(ip: Ip, direction: Direction) = request(
        ip = ip,
        met = when (direction) {
            Direction.OPEN -> "Cover.Open"
            Direction.CLOSE -> "Cover.Close"
        },
        params = buildJsonObject {
            put("id", 0)
            put("tag", "Quati")
        },
    )

    suspend fun findShelly(ip: Ip): ShellyState? {
        val status = getStatus(ip).getOrNull() ?: return null
        val config = getConfig(ip).getOrNull() ?: return null
        val kvs = getManyKvs(ip).getOrNull() ?: return null
        val webhooks = listWebhooks(ip).getOrNull() ?: return null
        val info = ShellyInfo.Invalid(
            ip = ip,
            mac = status.mac ?: return null,
            name = config.name,
            profile = config.profile ?: return null,
            webhooksValid = webhooks.isValid(),
            totalDurationClose = kvs.totalDuration(Direction.CLOSE),
            totalDurationOpen = kvs.totalDuration(Direction.OPEN),
        ).tryToValid()
        val event = ShellyEvent(latestDirection = status.lastDirection ?: Direction.OPEN)
        return ShellyState(
            info = info,
            latestEvent = event,
        )
    }

    suspend fun findAllShellys() = coroutineScope {
        val subnet = serverConfig.serverIp.value.substringBeforeLast('.')
        val semaphore = Semaphore(32)
        (1..254).mapNotNull {
            val ip = Ip("$subnet.$it")
            if (ip == serverConfig.serverIp) return@mapNotNull null
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    findShelly(ip)
                }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend inline fun <reified T> HttpResponse.parse(): T {
        val rootText = bodyAsText()
        val root = json.parseToJsonElement(rootText).jsonObject
        root["error"]?.jsonObject?.also {
            throw Exception("Shelly error: ${it["message"]?.jsonPrimitive?.content ?: "Unknown error"}")
        }
        val params = root.getValue("params")
        return json.decodeFromJsonElement<T>(params)
    }

    private suspend fun request(
        ip: Ip,
        met: String,
        params: JsonObject? = null,
    ) = httpClient.post("http://$ip/rpc") {
        setBody(buildJsonObject {
            put("id", 1)
            put("method", met)
            if (params != null)
                put("params", params)
        })
    }.let {
        if (it.status.isSuccess())
            Result.success(it)
        else
            Result.failure(Exception(it.bodyAsText().takeIf { it.isNotBlank() } ?: "Unknown error"))
    }

    private suspend inline fun <reified T> execute(
        ip: Ip,
        method: String,
        params: JsonObject? = null,
    ): Result<T> = request(ip = ip, met = method, params = params)
        .mapCatching { it.parse() }
}