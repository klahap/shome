package de.quati.shome

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.system.exitProcess

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
)

class OtfService(
    private val jarPath: Path,
) {
    companion object {
        val log = LoggerFactory.getLogger(OtfService::class.java)!!
        const val OWNER = "klahap"
        const val REPO = "shome"
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun searchLatestVersion(): String? {
        val release = httpClient.get("https://api.github.com/repos/$OWNER/$REPO/releases/latest") {
            header("Accept", "application/vnd.github+json")
            header("User-Agent", "shome-client") // GitHub API requires a User-Agent
        }.body<GithubRelease>()

        val latestVersion = release.tagName.parseVersion()
            ?: throw RuntimeException("Failed to parse latest version from GitHub release tag: ${release.tagName}")
        val currentVersion = BuildInfo.VERSION.parseVersion()
            ?: throw RuntimeException("Failed to parse current version from BuildInfo: ${BuildInfo.VERSION}")
        if (latestVersion < currentVersion)
            return null
        return release.tagName
    }

    suspend fun update(version: String) {
        try {
            updateFileAtomic(jarPath) {
                httpClient.downloadFile(
                    url = "https://github.com/$OWNER/$REPO/releases/download/$version/server-all.jar",
                    output = this
                )
            }
        } catch (e: Exception) {
            log.error("Failed to update server-all.jar: ${e.message}")
        }
        exitProcess(0)
    }
}