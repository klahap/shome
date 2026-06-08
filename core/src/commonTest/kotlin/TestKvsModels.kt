import de.quati.shome.model.ShellyRpcResponse
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test

class TestKvsModels : TestModels() {
    @Test
    fun testKvsModels() {
        val result = json.decodeFromString<ShellyRpcResponse.Params.KvsGetMany>(params0)
        result.offset shouldBe 0
        result.total shouldBe 2
        result.items.size shouldBe 2
        result.items[0].key shouldBe "item1"
        result.items[0].value shouldBe JsonPrimitive("value item1")
        result.items[1].key shouldBe "item2"
        result.items[1].value shouldBe JsonPrimitive("value item2")
    }
}


private const val params0 = """{
  "items": [
    {
      "key": "item1",
      "etag": "0DhkTpVgJk9zc2soEXlpoLrw==",
      "value": "value item1"
    },
    {
      "key": "item2",
      "etag": "0DXyU0CpLjyvZAV8GjRb2VzA==",
      "value": "value item2"
    }
  ],
  "offset": 0,
  "total": 2
}"""