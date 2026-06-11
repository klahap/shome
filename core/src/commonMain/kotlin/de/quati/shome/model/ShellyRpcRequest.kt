package de.quati.shome.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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
        data class DeleteKvsEntry(
            val key: KvsKey,
        ) : Params

        @Serializable
        data class KvsEntry(
            val key: KvsKey,
            val value: JsonElement,
        ) : Params {
            fun totalDurationValueOrNull(direction: Direction): Duration? {
                if (direction.totalDurationKvsKey != key) return null
                return (value as? JsonPrimitive)?.longOrNull?.milliseconds
            }

            val profileOrNull: Pair<ProfileName, Position>?
                get() {
                    if (key !is KvsKey.Profile) return null
                    val position = (value as? JsonPrimitive)?.doubleOrNull?.let(::Position) ?: return null
                    return key.name to position
                }

            companion object {
                fun totalDuration(direction: Direction, duration: Duration) = KvsEntry(
                    key = when (direction) {
                        Direction.OPEN -> KvsKey.TotalMsOpen
                        Direction.CLOSE -> KvsKey.TotalMsClose
                    },
                    value = JsonPrimitive(duration.inWholeMilliseconds),
                )

                fun profile(name: ProfileName, position: Position) = KvsEntry(
                    key = KvsKey.Profile(name),
                    value = JsonPrimitive(position.value),
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