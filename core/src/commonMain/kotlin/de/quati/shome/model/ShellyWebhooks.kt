package de.quati.shome.model

import de.quati.shome.util.getArray
import de.quati.shome.util.getPrimitive
import de.quati.shome.util.stringOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull


data class ShellyWebhooks(
    private val raw: JsonObject,
) {
    val quatiHooks = raw.getArray("cover:0")
        ?.filterIsInstance<JsonObject>()
        ?.map(::Entry)
        ?.filter { it.isQuatiWebhook } ?: emptyList()

    context(_: ServerConfig.Context)
    fun isValid(): Boolean {
        val validTypes = quatiHooks.map { it.validQuatiType() }
        if (validTypes.distinct().size != validTypes.size) return false
        return validTypes.filterNotNull().toSet() == WebhookEventType.entries.toSet()
    }

    data class Entry(
        private val raw: JsonObject,
    ) {
        val id = raw.getPrimitive("id")?.intOrNull
        val enabled = raw.getPrimitive("enable")?.booleanOrNull
        val event = raw.getPrimitive("event")?.stringOrNull
        val name = raw.getPrimitive("name")?.stringOrNull
        val urls = raw.getArray("urls")?.filterIsInstance<JsonPrimitive>()
            ?.map { it.stringOrNull } ?: emptyList()

        val isQuatiWebhook get() = name?.startsWith(WebhookEventType.NAME_PREFIX) ?: false

        private val quatiType = WebhookEventType.entries
            .firstOrNull { it.prettyName == name && it.value == event }

        context(c: ServerConfig.Context)
        fun validQuatiType(): WebhookEventType? {
            if (enabled != true) return null
            val type = quatiType ?: return null
            val expectedUrls = listOf(
                c.serverConfig.webhookUrl(type = type)
            )
            if (urls != expectedUrls) return null
            return type
        }
    }
}

/*
http://192.168.178.69/rpc/Webhook.List

{
  "hooks": [
    {
      "id": 1,
      "cid": 0,
      "enable": false,
      "event": "input.toggle_off",
      "name": "When input is OFF",
      "urls": [
        "http://10.33.55.167/rpc/Switch.Set?id=2&on=false"
      ],
      "condition": null,
      "repeat_period": 0
    },
    {
      "id": 2,
      "cid": 0,
      "enable": true,
      "event": "input.toggle_on",
      "name": "null",
      "urls": [
        "http://10.33.55.131/rpc/Switch.Toggle?id=0"
      ],
      "condition": null,
      "repeat_period": 0
    }
  ],
  "rev": 1
}
 */