package de.quati.shome.hue

import de.quati.shome.hue.model.DeviceResponse
import de.quati.shome.hue.model.DeviceState
import de.quati.shome.hue.model.HueApiResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.uuid.Uuid

private val deviceMap = mapOf(
    Uuid.parse("c3279c15-c7c9-4e17-acad-4c8cce31dc91") to "foo",
    Uuid.parse("29d49980-d7af-422e-918c-fd66491384e9") to "bar",
)

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun Application.hueRoutes() {
    routing {
        route("/upnp") {
            get("/{deviceId}/setup.xml") {
                val deviceId = call.parameters["deviceId"]!!
                val hostName = call.request.local.localHost
                val port = call.request.local.localPort
                log.info("upnp device settings requested: $deviceId from ${call.request.host()}")
                call.respondText(
                    hueTemplate(hostName, port, deviceId),
                    ContentType.Application.Xml,
                )
            }
        }

        route("/api") {
            get("/{userId}/lights") {
                log.info("hue lights list requested")
                val response = deviceMap.entries.associate { it.key.toString() to JsonPrimitive(it.value) }
                    .let(::JsonObject)
                    .let { json.encodeToString(it) }
                call.respondText(
                    text = response,
                    contentType = ContentType.Application.Json,
                )
            }

            post("/{...}") {
                log.info("registered device")
                call.respondText(
                    text = """[{"success":{"username":"lights"}}]""",
                    contentType = ContentType.Application.Json,
                )
            }

            get("/{userId}") {
                log.info("hue api root requested")
                if (deviceMap.isEmpty()) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                val deviceList = deviceMap.entries.associate { (id, name) ->
                    id.toString() to DeviceResponse(name = name, uniqueid = id)
                }
                call.respondText(
                    text = HueApiResponse(lights = deviceList).let { json.encodeToString(it) },
                    contentType = ContentType.Application.Json,
                )
            }

            get("/{userId}/lights/{lightId}") {
                val lightId = call.parameters["lightId"]!!.let(Uuid::parse)
                log.info("hue light requested")
                val name = deviceMap[lightId]
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                log.info("user ${call.parameters["userId"]} found device named: $name")
                call.respondText(
                    text = DeviceResponse(name = name, uniqueid = lightId).let { json.encodeToString(it) },
                    contentType = ContentType.Application.Json,
                )
            }

            put("/{userId}/lights/{lightId}/state") {
                val lightId = call.parameters["lightId"]!!.let(Uuid::parse)
                log.info("hue state change requested")

                val state = try {
                    call.receive<DeviceState>()
                } catch (e: Exception) {
                    log.info("json decoding failed on input", e)
                    return@put call.respond(HttpStatusCode.BadRequest)
                }

                val name = deviceMap[lightId]
                    ?: return@put call.respond(HttpStatusCode.NotFound)
                log.info("hue state change requested, name: $name")

                call.respondText(
                    text = """[{"success":{"/lights/$lightId/state/on":${state.on}}}]""",
                    contentType = ContentType.Application.Json,
                )
            }
        }
    }
}