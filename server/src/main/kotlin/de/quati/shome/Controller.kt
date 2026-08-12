package de.quati.shome

import de.quati.shome.model.Mac
import de.quati.shome.model.BackendIntent
import de.quati.shome.model.ShellyIntent
import de.quati.shome.model.WebhookEventType
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.http.content.staticResources
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeStringUtf8
import kotlinx.serialization.json.Json
import kotlin.time.Clock


fun Application.addController(backendStateService: BackendStateService) {
    val ndJsonContentType = ContentType.parse("application/x-ndjson")
    routing {
        staticResources(
            remotePath = "/",
            basePackage = "/static",
        )

        get("/api/logs") {
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    "shome.log",
                ).toString()
            )
            call.respondOutputStream(ContentType.Text.Plain) {
                logStream(this, withZip = true)
            }
        }

        get("/api/logs-today") {
            call.respondOutputStream(ContentType.Text.Plain) {
                logStream(this, withZip = false)
            }
        }

        get("/api/state") {
            call.response.header(HttpHeaders.CacheControl, "no-cache, no-transform")
            call.response.header("X-Accel-Buffering", "no")
            call.respondBytesWriter(contentType = ndJsonContentType) {
                kotlinx.coroutines.flow.merge(
                    backendStateService.state,
                    backendStateService.backendMessageFlow,
                ).collect { state ->
                    val json = Json.encodeToString(state)
                    writeStringUtf8(json + "\n")
                    flush()
                }
            }
        }

        post("/api/intent") {
            val intent = try {
                call.receive<BackendIntent>()
            } catch (e: Exception) {
                log.warn("Failed to receive intent: ${e.message}")
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            try {
                backendStateService.onIntent(intent)
            } catch (e: Exception) {
                log.error("Failed to process intent: $intent, error: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError)
                return@post
            }
            call.respond(HttpStatusCode.Accepted)
        }

        get("/api/webhook") {
            call.respond(HttpStatusCode.Accepted)
            val mac = call.queryParameters[WebhookEventType.QUERY_KEY_MAC]
                ?.let(::Mac)
                ?: return@get log.warn("Missing MAC parameter in webhook request")
            val event = call.queryParameters[WebhookEventType.QUERY_KEY_EVENT]
                ?.let(WebhookEventType::parseUrlName)
                ?: return@get log.warn("Missing event parameter in webhook request form MAC: $mac")
            log.debug("Webhook received form MAC: $mac, Event: $event")
            val intent = BackendIntent.Shelly(
                mac = mac,
                intent = ShellyIntent.WebhookEventReceived(
                    event = event,
                    timestamp = Clock.System.now(),
                ),
            )
            try {
                backendStateService.onIntent(intent)
            } catch (e: Exception) {
                log.error("Failed to process webhook for MAC: $mac, Event: $event", e)
            }
        }
    }
}
