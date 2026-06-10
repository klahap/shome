package de.quati.shome.model

import de.quati.shome.model.WebhookEventType.Companion.QUERY_KEY_EVENT
import de.quati.shome.model.WebhookEventType.Companion.QUERY_KEY_MAC

data class BackendConfig(
    val backendEndpoint: NetworkEndpoint,
    val backendIPv4: Host.IPv4,
) {
    interface Context {
        val backendConfig: BackendConfig
        companion object {
            fun create(ip: Host.IPv4, endpoint: NetworkEndpoint): Context = BackendConfig(
                backendEndpoint = endpoint,
                backendIPv4 = ip,
            ).let(::ContextImpl)
        }
    }

    data class ContextImpl(
        override val backendConfig: BackendConfig,
    ) : Context

    fun webhookUrl(type: WebhookEventType) =
        $$"http://$$backendEndpoint/api/webhook?$$QUERY_KEY_EVENT=$${type.urlName}&$$QUERY_KEY_MAC=${config.sys.device.mac}"
}