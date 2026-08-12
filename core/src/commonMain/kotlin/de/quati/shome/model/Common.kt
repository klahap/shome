package de.quati.shome.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

@Serializable
enum class OtfState {
    DISABLED,
    ENABLED,
    UPDATING,
    SEARCHING,
}

@Serializable
enum class ShellyRpcMethod {
    @SerialName("Cover.Open")
    COVER_OPEN,

    @SerialName("Cover.Close")
    COVER_CLOSE,

    @SerialName("Webhook.List")
    WEBHOOK_LIST,

    @SerialName("Webhook.Delete")
    WEBHOOK_DELETE,

    @SerialName("Webhook.Create")
    WEBHOOK_CREATE,

    @SerialName("KVS.Set")
    KVS_SET,

    @SerialName("KVS.Delete")
    KVS_DELETE,

    @SerialName("KVS.GetMany")
    KVS_GET_MANY,

    @SerialName("Shelly.GetConfig")
    SHELLY_GET_CONFIG,

    @SerialName("Shelly.GetStatus")
    SHELLY_GET_STATUS,

    @SerialName("Sys.SetConfig")
    SYS_SET_CONFIG,

    @SerialName("Cover.SetConfig")
    COVER_SET_CONFIG,
}

@Serializable(with = Host.Serializer::class)
sealed interface Host {
    @Serializable
    data class IPv4(val a: UByte, val b: UByte, val c: UByte, val d: UByte) : Host {
        override fun toString() = "$a.$b.$c.$d"
    }

    @JvmInline
    value class Hostname(val value: String) : Host {
        override fun toString() = value
    }

    object Serializer : KSerializer<Host> {
        override val descriptor = PrimitiveSerialDescriptor("Host", PrimitiveKind.STRING)
        override fun deserialize(decoder: Decoder): Host = parse(decoder.decodeString())
        override fun serialize(encoder: Encoder, value: Host) = encoder.encodeString(value.toString())
    }

    companion object {
        fun parse(raw: String): Host = raw.split(".")
            .map { it.toUByteOrNull() }
            .takeIf { parts -> parts.size == 4 && parts.all { it != null } }
            ?.filterNotNull()
            ?.let { (a, b, c, d) -> IPv4(a, b, c, d) }
            ?: Hostname(raw)
    }
}

@Serializable
data class NetworkEndpoint(
    val host: Host,
    val port: Int?,
) {
    override fun toString() = host.toString() + port?.let { ":$it" }.orEmpty()

    companion object {
        fun parse(raw: String): NetworkEndpoint = if (':' in raw)
            NetworkEndpoint(
                host = Host.parse(raw.substringBeforeLast(":")),
                port = raw.substringAfterLast(':').toIntOrNull()
                    ?: error("invalid port: ${raw.substringAfterLast(':')}")
            )
        else NetworkEndpoint(
            host = Host.parse(raw),
            port = null,
        )
    }
}

@JvmInline
@Serializable
value class ProfileId(val value: String) {
    constructor() : this(Uuid.random().toString())
}

@JvmInline
@Serializable
value class Mac(val value: String) {
    override fun toString() = value
}

@JvmInline
@Serializable
value class Distance(val value: Double) {
    val isApproxNull get() = value.absoluteValue < 1e-5
}


@Serializable(with = KvsKey.Serializer::class)
sealed interface KvsKey {
    val keyValue: String
    val isValid get() = keyValue.length in 1..42

    @JvmInline
    value class Other(val value: String) : KvsKey {
        override val keyValue get() = value
        override fun toString() = keyValue
    }

    data object TotalMsOpen : KvsKey {
        override val keyValue get() = "total_duration_open_in_ms"
        override fun toString() = keyValue
    }

    data object TotalMsClose : KvsKey {
        override val keyValue get() = "total_duration_close_in_ms"
        override fun toString() = keyValue
    }


    object Serializer : KSerializer<KvsKey> {
        override val descriptor = PrimitiveSerialDescriptor("KvsKey", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: KvsKey) {
            if (!value.isValid)
                throw SerializationException("KvsKey ${value.keyValue} is too short or long, min 1 and max 42 chars allowed")
            encoder.encodeString(value.keyValue)
        }

        override fun deserialize(decoder: Decoder): KvsKey = when (val v = decoder.decodeString()) {
            TotalMsOpen.keyValue -> TotalMsOpen
            TotalMsClose.keyValue -> TotalMsClose
            else -> Other(v)
        }
    }

}

@JvmInline
value class PositionPercent(val value: Int) {
    val position get() = Position(value.coerceIn(0, 100) / 100.0)
    override fun toString() = "$value"
}

@JvmInline
@Serializable
value class Position(val value: Double) {
    fun compute(distance: Distance) = (value + distance.value).coerceIn(0.0, 1.0).let(::Position)
    val percent get() = PositionPercent((value * 100).roundToInt().coerceIn(0, 100))
    override fun toString() = "$value"

    fun distanceLeft(direction: Direction) = when (direction) {
        Direction.CLOSE -> (CLOSED.value - value.coerceIn(0.0, 1.0)).absoluteValue
        Direction.OPEN -> (OPENED.value - value.coerceIn(0.0, 1.0)).absoluteValue
    }.let(::Distance)

    fun distanceAndDirectionTo(other: Position): Pair<Distance, Direction>? {
        val distance = (other.value.coerceIn(0.0, 1.0) - value.coerceIn(0.0, 1.0)).absoluteValue.let(::Distance)
        if (distance.isApproxNull) return null
        val direction = when {
            other.value > value -> Direction.CLOSE
            else -> Direction.OPEN
        }
        return distance to direction
    }

    fun isApprox(other: Position) = (value - other.value).absoluteValue < 0.01
    fun isApproxEnd() = isApprox(CLOSED) || isApprox(OPENED)

    fun computeOutOfBoundsDistance(distance: Distance): Double {
        val x = value + distance.value
        return if (x > 0.5)
            x - 1.0
        else
            -x
    }

    companion object {
        val OPENED = Position(0.0)
        val CLOSED = Position(1.0)
    }
}


enum class Direction {
    CLOSE, OPEN;

    val opposite
        get() = when (this) {
            CLOSE -> OPEN
            OPEN -> CLOSE
        }
    val totalDurationKvsKey
        get() = when (this) {
            CLOSE -> KvsKey.TotalMsClose
            OPEN -> KvsKey.TotalMsOpen
        }

    val lastDirectionValue
        get() = when (this) {
            CLOSE -> "close"
            OPEN -> "open"
        }

    val endPosition
        get() = when (this) {
            CLOSE -> Position.CLOSED
            OPEN -> Position.OPENED
        }

    val sign
        get() = when (this) {
            CLOSE -> 1
            OPEN -> -1
        }
}


@Serializable(with = WebhookEventType.Serializer::class)
sealed interface WebhookEventType {

    @Serializable(with = SerializerQuati::class)
    sealed interface Quati : WebhookEventType

    val value: String

    data object CoverOpening : Quati {
        override val value = "cover.opening"
        val direction = Direction.OPEN
    }

    data object CoverClosing : Quati {
        override val value = "cover.closing"
        val direction = Direction.CLOSE
    }

    data class Unknown(override val value: String) : WebhookEventType

    val urlName get() = value.replace('.', '_')
    val prettyName get() = NAME_PREFIX + urlName

    companion object {
        val entries = listOf(CoverOpening, CoverClosing)
        const val NAME_PREFIX = "quati_"
        const val QUERY_KEY_MAC = "mac"
        const val QUERY_KEY_EVENT = "event"

        fun parseUrlName(value: String): Quati? = entries.find { it.urlName == value }
    }

    object Serializer : KSerializer<WebhookEventType> {
        override val descriptor = PrimitiveSerialDescriptor("WebhookEventType", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: WebhookEventType) {
            encoder.encodeString(value.value)
        }

        override fun deserialize(decoder: Decoder): WebhookEventType {
            val v = decoder.decodeString()
            return entries.find { it.value == v } ?: Unknown(v)
        }
    }

    object SerializerQuati : KSerializer<Quati> {
        override val descriptor = PrimitiveSerialDescriptor("WebhookEventType", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: Quati) {
            encoder.encodeString(value.value)
        }

        override fun deserialize(decoder: Decoder): Quati {
            val v = decoder.decodeString()
            return entries.find { it.value == v } ?: throw SerializationException("Unknown Quati event type: $v")
        }
    }
}

@Serializable
data class CronJobTime(
    val hour: Int,
    val minute: Int,
) {
    fun plusMinute(): CronJobTime {
        val newMinute = (minute + 1) % 60
        val newHour = hour + (minute + 1) / 60
        return CronJobTime(newHour, newMinute)
    }

    override fun toString(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    companion object {
        fun parse(s: String): CronJobTime? {
            val parts = s.split(":")
            if (parts.size != 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            return CronJobTime(h, m)
        }
    }
}
