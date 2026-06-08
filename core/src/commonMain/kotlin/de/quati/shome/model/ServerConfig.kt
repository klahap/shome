package de.quati.shome.model

import de.quati.shome.model.WebhookEventType.Companion.QUERY_KEY_EVENT
import de.quati.shome.model.WebhookEventType.Companion.QUERY_KEY_MAC

data class ServerConfig(
    val serverEndpoint: NetworkEndpoint,
    val serverIPv4: Host.IPv4,
) {
    interface Context {
        val serverConfig: ServerConfig
        companion object {
            fun create(ip: Host.IPv4, endpoint: NetworkEndpoint): Context = ServerConfig(
                serverEndpoint = endpoint,
                serverIPv4 = ip,
            ).let(::ContextImpl)
        }
    }

    data class ContextImpl(
        override val serverConfig: ServerConfig,
    ) : Context

    fun webhookUrl(type: WebhookEventType) =
        $$"http://$$serverEndpoint/api/webhook?$$QUERY_KEY_EVENT=$${type.urlName}&$$QUERY_KEY_MAC=${config.sys.device.mac}"
}