package de.quati.shome.model

import de.quati.shome.model.WebhookEventType.Companion.QUERY_KEY_EVENT
import de.quati.shome.model.WebhookEventType.Companion.QUERY_KEY_MAC

data class ServerConfig(
    val serverIp: Ip,
    val serverHost: String,
    val serverPort: Int,
) {
    interface Context {
        val serverConfig: ServerConfig
    }

    data class ContextImpl(
        override val serverConfig: ServerConfig,
    ) : Context

    fun webhookUrl(type: WebhookEventType) =
        $$"http://$$serverHost:$$serverPort/api/webhook?$$QUERY_KEY_EVENT=$${type.urlName}&$$QUERY_KEY_MAC=${config.sys.device.mac}"

    companion object {
        fun create(ip: Ip, host: String?, port: Int): ServerConfig = ServerConfig(
            serverIp = ip,
            serverHost = host ?: ip.value,
            serverPort = port,
        )
    }
}