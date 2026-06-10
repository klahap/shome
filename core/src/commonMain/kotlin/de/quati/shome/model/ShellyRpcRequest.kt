package de.quati.shome.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.time.Duration

@Serializable
data class ShellyRpcRequest(
    val id: Int,
    val method: ShellyRpcMethod,
    val params: JsonElement? = null,
) {
    companion object {
        val json = Json { ignoreUnknownKeys = true }

        inline fun <reified T : Params> create(
            id: Int,
            method: ShellyRpcMethod,
            params: T?,
        ) = ShellyRpcRequest(
            id = id,
            method = method,
            params = params?.let { json.encodeToJsonElement(it) },
        )

    }

    inline fun <reified T : Params> parse(): T =
        json.decodeFromJsonElement(params ?: error("Params are not of type ${T::class.simpleName}"))

    interface Params {
        @Serializable
        data class CoverDrive(
            val id: Int,
            val tag: String,
        ) : Params

        @Serializable
        data class WebhookDelete(
            val id: Int,
        ) : Params

        @Serializable
        data class Webhook(
            val cid: Int,
            val enable: Boolean,
            val event: WebhookEventType,
            val name: String,
            val urls: List<String>,
        ) : Params

        @Serializable
        data class KvsEntry(
            val key: String,
            val value: JsonElement,
        ) : Params {
            fun isTotalDuration(direction: Direction) = key == when (direction) {
                Direction.OPEN -> KEY_TOTAL_MS_OPEN
                Direction.CLOSE -> KEY_TOTAL_MS_CLOSE
            }

            companion object {
                private const val KEY_TOTAL_MS_OPEN = "total_duration_open_in_ms"
                private const val KEY_TOTAL_MS_CLOSE = "total_duration_close_in_ms"

                fun totalDuration(direction: Direction, duration: Duration) = KvsEntry(
                    key = when (direction) {
                        Direction.OPEN -> KEY_TOTAL_MS_OPEN
                        Direction.CLOSE -> KEY_TOTAL_MS_CLOSE
                    },
                    value = JsonPrimitive(duration.inWholeMilliseconds),
                )
            }
        }

        @Serializable
        data class SetConfig(
            val config: Config
        ) : Params {
            @Serializable
            data class Config(
                val device: Device
            ) {
                @Serializable
                data class Device(
                    val name: String
                )
            }
        }
    }
}