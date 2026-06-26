package de.quati.shome.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.time.Duration.Companion.seconds

@Serializable
data class ShellyRpcResponse(
    val error: Error? = null,
    private val result: JsonElement? = null,
    private val params: JsonElement? = null,
) {
    val resultOrNull: JsonElement? get() = result ?: params

    inline fun<reified T: Params> parse(): T? = resultOrNull?.let {
        json.decodeFromJsonElement(it)
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true }
        inline fun <reified T : Params> create(params: T) = ShellyRpcResponse(
            result = json.encodeToJsonElement(params),
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
            fun totalDuration(direction: Direction) =
                items.firstNotNullOfOrNull { it.totalDurationValueOrNull(direction) }


        }

        @Serializable
        data class WebhookList(
            val hooks: List<Webhook>
        ) : Params {
            context(_: BackendConfig.Context)
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

            context(c: BackendConfig.Context)
            fun validQuatiType(): WebhookEventType? {
                if (!enable) return null
                if (event.prettyName != name) return null
                val expectedUrls = listOf(
                    c.backendConfig.webhookUrl(type = event)
                )
                if (urls != expectedUrls) return null
                return event
            }
        }

        @Serializable
        data class ShellyStatus(
            @SerialName("cover:0") val cover0: Cover0? = null,
            val sys: Sys? = null,
        ) : Params {
            @Serializable
            data class Cover0(
                @SerialName("last_direction") val lastDirection: String? = null
            ) {
                val lastDirectionTyped
                    get() = Direction.entries.firstOrNull { it.lastDirectionValue == lastDirection }

            }

            @Serializable
            data class Sys(
                val mac: Mac? = null,
                @SerialName("restart_required") val restartRequired: Boolean? = null
            )
        }

        @Serializable
        data class ShellyConfig(
            @SerialName("cover:0") val cover0: Cover0? = null,
            val sys: Sys? = null,
        ) : Params {
            @Serializable
            data class Cover0(
                @SerialName("maxtime_open") val maxtimeOpen: Double,
                @SerialName("maxtime_close") val maxtimeClose: Double,
                @SerialName("swap_inputs") val swapInputs: Boolean = false,
                @SerialName("invert_directions") val invertDirections: Boolean = false,
            ) {
                val maxtimeOpenDuration get() = maxtimeOpen.seconds
                val maxtimeCloseDuration get() = maxtimeClose.seconds
            }

            @Serializable
            data class Sys(
                val device: Device? = null,
            ) {
                @Serializable
                data class Device(
                    @SerialName("name") val name: String? = null,
                    @SerialName("mac") val mac: Mac? = null,
                    @SerialName("profile") val profile: String? = null,
                )
            }
        }
    }
}