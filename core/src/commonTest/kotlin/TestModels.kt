import kotlinx.serialization.json.Json

abstract class TestModels {
    val json = Json { ignoreUnknownKeys = true }
}