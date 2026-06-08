package de.quati.shome

import de.quati.shome.model.Mac
import de.quati.shome.model.ShellyConfig
import de.quati.shome.model.ShellyRpcMethod
import de.quati.shome.model.ShellyRpcRequest
import de.quati.shome.model.ShellyRpcResponse
import de.quati.shome.model.ShellyStatus
import de.quati.shome.model.WebhookEventType
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement


data class State(
    val mac: Mac,
    val deviceName: String? = null,
    val webhooks: List<ShellyRpcResponse.Params.Webhook> = emptyList(),
    val kvs: Map<String, JsonElement> = emptyMap(),
    val config: ShellyConfig = ShellyConfig(
        cover0 = ShellyConfig.Cover0(
            maxtimeOpen = 0.5,
            maxtimeClose = 0.5,
            swapInputs = false,
            invertDirections = false,
        ),
        sys = ShellyConfig.Sys(
            device = ShellyConfig.Sys.Device(
                name = null,
                mac = mac,
                profile = "cover",
            ),
        )
    ),
    val status: ShellyStatus = ShellyStatus(
        cover0 = ShellyStatus.Cover0(
            lastDirection = null
        ),
        sys = ShellyStatus.Sys(
            mac = mac,
            restartRequired = false,
        )
    )
)

class Helper(
    val mac: Mac,
    val app: Application,
) {
    val state = MutableStateFlow(State(mac = mac))
    val httpClient = HttpClient(CIO) {}

    fun getConfig() = state.value.config.let { ShellyRpcResponse.create(params = it) }
    fun getStatus() = state.value.status.let { ShellyRpcResponse.create(params = it) }

    fun deleteWebhook(id: Int): ShellyRpcResponse {
        state.update { s -> s.copy(webhooks = s.webhooks.filter { it.id != id }) }
        app.log.info("Deleted webhook with id: $id")
        return ShellyRpcResponse()
    }

    fun createWebhook(w: ShellyRpcRequest.Params.Webhook): ShellyRpcResponse {
        state.update { s ->
            s.copy(
                webhooks = s.webhooks + ShellyRpcResponse.Params.Webhook(
                    id = s.webhooks.maxOfOrNull { it.id }?.inc() ?: 1,
                    cid = w.cid,
                    enable = w.enable,
                    event = w.event,
                    name = w.name,
                    urls = w.urls,
                )
            )
        }
        app.log.info("Created webhook: ${w.event}; url: ${w.urls}")
        return ShellyRpcResponse()
    }

    fun listWebhooks() = state.value.webhooks
        .let { ShellyRpcResponse.Params.WebhookList(it) }
        .let { ShellyRpcResponse.create(params = it) }


    fun setKvs(entry: ShellyRpcRequest.Params.KvsEntry): ShellyRpcResponse {
        state.update { s -> s.copy(kvs = s.kvs + (entry.key to entry.value)) }
        app.log.info("Set KVS entry: ${entry.key}=${entry.value}")
        return ShellyRpcResponse()
    }

    fun getManyKvs(): ShellyRpcResponse =
        state.value.kvs.map { (key, value) -> ShellyRpcRequest.Params.KvsEntry(key, value) }
            .let {
                ShellyRpcResponse.Params.KvsGetMany(
                    items = it,
                    offset = 0,
                    total = it.size,
                )
            }.let { ShellyRpcResponse.create(params = it) }

    suspend fun coverMoving(eventType: WebhookEventType.Quati): ShellyRpcResponse {
        state.value.webhooks
            .filter { it.enable && it.event == eventType }
            .flatMap { it.urls }
            .map { it.replace($$"${config.sys.device.mac}", mac.value) }
            .forEach { url ->
                httpClient.get(url).also {
                    if (it.status.isSuccess())
                        app.log.info("Sending webhook request to $url")
                    else
                        app.log.warn("Failed to send webhook request to $url, http code: ${it.status.value}")
                }
            }
        state.update { s ->
            val lastDirection = when (eventType) {
                is WebhookEventType.CoverOpening -> eventType.direction.lastDirectionValue
                is WebhookEventType.CoverClosing -> eventType.direction.lastDirectionValue
            }
            s.copy(status = s.status.copy(cover0 = s.status.cover0?.copy(lastDirection = lastDirection)))
        }
        app.log.info("Cover moving: $eventType")
        return ShellyRpcResponse()
    }

}

fun Application.addController(mac: Mac) {
    val helper = Helper(app = this, mac = mac)
    routing {
        post("/rpc") {
            val body = call.receive<ShellyRpcRequest>()
            val response = when (body.method) {
                ShellyRpcMethod.COVER_OPEN -> helper.coverMoving(WebhookEventType.CoverOpening)
                ShellyRpcMethod.COVER_CLOSE -> helper.coverMoving(WebhookEventType.CoverClosing)
                ShellyRpcMethod.WEBHOOK_LIST -> helper.listWebhooks()
                ShellyRpcMethod.WEBHOOK_DELETE -> helper.deleteWebhook(body.parse<ShellyRpcRequest.Params.WebhookDelete>().id)
                ShellyRpcMethod.WEBHOOK_CREATE -> helper.createWebhook(body.parse<ShellyRpcRequest.Params.Webhook>())
                ShellyRpcMethod.KVS_SET -> helper.setKvs(body.parse<ShellyRpcRequest.Params.KvsEntry>())
                ShellyRpcMethod.KVS_GET_MANY -> helper.getManyKvs()
                ShellyRpcMethod.SHELLY_GET_CONFIG -> helper.getConfig()
                ShellyRpcMethod.SHELLY_GET_STATUS -> helper.getStatus()
            }
            call.respond(status = HttpStatusCode.OK, message = response)
            log.info("Request handled: $body")
        }
    }
}
