package de.quati.shome.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Serializable
data class KvsEntry(
    val key: String,
    val value: JsonElement,
) {
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
data class KvsGetMany(
    val items: List<KvsEntry>,
    val offset: Int,
    val total: Int,
) {
    fun totalDuration(direction: Direction) = items.firstOrNull { it.isTotalDuration(direction) }
        ?.value?.jsonPrimitive?.longOrNull?.milliseconds
}
