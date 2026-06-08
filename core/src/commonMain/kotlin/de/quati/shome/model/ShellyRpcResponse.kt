package de.quati.shome.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Duration.Companion.milliseconds

@Serializable
data class ShellyRpcResponse(
    val error: Error? = null,
    val params: JsonElement? = null,
) {
    companion object {
        val json = Json { ignoreUnknownKeys = true }
        inline fun <reified T : Params> create(params: T) = ShellyRpcResponse(
            params = json.encodeToJsonElement(params),
        )
    }

    @Serializable
    data class Error(
        val message: String? = null,
    )

    interface Params {
        @Serializable
        data class KvsGetMany(
            val items: List<ShellyRpcRequest.Params.KvsEntry>,
            val offset: Int,
            val total: Int,
        ) : Params {
            fun totalDuration(direction: Direction) = items.firstOrNull { it.isTotalDuration(direction) }
                ?.value?.jsonPrimitive?.longOrNull?.milliseconds
        }

        @Serializable
        data class WebhookList(
            val hooks: List<Webhook>
        ) : Params {
            context(_: ServerConfig.Context)
            fun isValid(): Boolean {
                val validTypes = hooks
                    .filter { it.isQuatiWebhook }
                    .map { it.validQuatiType() }
                if (validTypes.distinct().size != validTypes.size) return false
                return validTypes.filterNotNull().toSet() == WebhookEventType.entries.toSet()
            }

        }

        @Serializable
        data class Webhook(
            val id: Int,
            val cid: Int,
            val enable: Boolean,
            val event: WebhookEventType,
            val name: String? = null,
            val urls: List<String> = emptyList(),
        ) {
            val isQuatiWebhook get() = name?.startsWith(WebhookEventType.NAME_PREFIX) ?: false

            context(c: ServerConfig.Context)
            fun validQuatiType(): WebhookEventType? {
                if (!enable) return null
                if (event.prettyName != name) return null
                val expectedUrls = listOf(
                    c.serverConfig.webhookUrl(type = event)
                )
                if (urls != expectedUrls) return null
                return event
            }
        }
    }
}