import de.quati.shome.model.NetworkEndpoint
import de.quati.shome.model.BackendConfig
import de.quati.shome.model.ShellyRpcResponse
import de.quati.shome.model.WebhookEventType.Companion.QUERY_KEY_EVENT
import de.quati.shome.model.WebhookEventType.Companion.QUERY_KEY_MAC
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class TestWebhookModels : TestModels() {
    val backendConfig = BackendConfig.create(ip = NetworkEndpoint(HOST), host = null, port = PORT)
        .let(BackendConfig::ContextImpl)

    @Test
    fun testWebhookModels() {
        val result = json.decodeFromString<ShellyRpcResponse.Params.WebhookList>(params0)
        with(backendConfig) {
            result.isValid() shouldBe true
        }
    }
}


// http://192.168.178.69/rpc/Webhook.List
private const val HOST = "192.168.178.69"
private const val PORT = 8090
private const val params0 = $$"""{
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
      "id": 3,
      "cid": 0,
      "enable": true,
      "event": "cover.opening",
      "name": "quati_cover_opening",
      "urls": [
        "http://$$HOST:$$PORT/api/webhook?$$QUERY_KEY_EVENT=cover_opening&$$QUERY_KEY_MAC=${config.sys.device.mac}"
      ],
      "condition": null,
      "repeat_period": 0
    },
    {
      "id": 4,
      "cid": 0,
      "enable": true,
      "event": "cover.closing",
      "name": "quati_cover_closing",
      "urls": [
        "http://$$HOST:$$PORT/api/webhook?$$QUERY_KEY_EVENT=cover_closing&$$QUERY_KEY_MAC=${config.sys.device.mac}"
      ],
      "condition": null,
      "repeat_period": 0
    }
  ],
  "rev": 1
}"""