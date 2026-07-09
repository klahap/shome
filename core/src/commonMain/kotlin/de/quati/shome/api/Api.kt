package de.quati.shome.api

import de.quati.shome.model.WebhookEventType
import io.ktor.resources.*
import kotlinx.serialization.SerialName

@Resource("/api")
class Api {

    @Resource("state")
    class State(val parent: Api = Api())

    @Resource("logs")
    class Logs(val parent: Api = Api())

    @Resource("intent")
    class Intent(val parent: Api = Api())

    @Resource("webhook")
    class Webhook(
        val parent: Api = Api(),
        @SerialName(WebhookEventType.QUERY_KEY_MAC) val mac: String,
        @SerialName(WebhookEventType.QUERY_KEY_EVENT) val event: String,
    )
}