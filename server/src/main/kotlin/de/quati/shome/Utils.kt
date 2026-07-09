package de.quati.shome

import de.quati.shome.model.CronJobTime
import de.quati.shome.model.Host
import de.quati.shome.model.NetworkEndpoint
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.GZIPInputStream
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import kotlin.io.path.name
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

fun Inet4Address.toHost(): Host.IPv4 {
    val b = address
    return Host.IPv4(b[0].toUByte(), b[1].toUByte(), b[2].toUByte(), b[3].toUByte())
}

fun Host.toInetAddress(): InetAddress = when (this) {
    is Host.Hostname -> InetAddress.getByName(value)
    is Host.IPv4 -> InetAddress.getByName(toString())
}

val backendIp by lazy {
    NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .flatMap { it.interfaceAddresses.asSequence() }
        .map { it.address }
        .filterIsInstance<Inet4Address>()
        .map { it.toHost() }
        .firstOrNull()
        ?: error("No valid IPv4 address found")
}

fun Application.disableCors() = install(CORS) {
    anyHost()
    HttpMethod.DefaultMethods.forEach {
        allowMethod(it)
    }
    allowHeader(HttpHeaders.ContentType)
    allowHeader(HttpHeaders.Authorization)
}


fun <T, K> Flow<T>.distinctBy(keyMapper: (T) -> K): Flow<T> {
    val foundEndpoints = mutableSetOf<K>()
    return filter { x ->
        val key = keyMapper(x)
        if (key in foundEndpoints) return@filter false
        foundEndpoints.add(key)
        true
    }
}

@OptIn(FlowPreview::class)
fun searchDevices(timeout: Duration = 5.seconds): Flow<Pair<String?, NetworkEndpoint>> = callbackFlow {
    val jmdns = JmDNS.create(backendIp.toInetAddress())
    jmdns.addServiceListener("_http._tcp.local.", object : ServiceListener {
        override fun serviceAdded(e: ServiceEvent) {}
        override fun serviceRemoved(e: ServiceEvent) {}
        override fun serviceResolved(e: ServiceEvent) {
            e.info?.inet4Addresses?.filterNotNull()?.map {
                NetworkEndpoint(host = it.toHost(), port = e.info.port)
            }?.forEach { trySend(e.name to it) }
        }
    })
    awaitClose {
        jmdns.close()
    }
}.flowOn(Dispatchers.IO)
    .timeout(timeout)
    .catch { if (it !is TimeoutCancellationException) throw it }
    .distinctBy { it.second }

fun cronTimeFlow(zoneId: ZoneId) = flow {
    while (true) {
        val now = System.currentTimeMillis()
        val delayMs = 60_000 - (now % 60_000) + 2_000 // plus some delay to avoid race conditions
        delay(delayMs.milliseconds)
        val dt = LocalDateTime.now(zoneId)
        emit(CronJobTime(hour = dt.hour, minute = dt.minute))
    }
}.distinctUntilChanged()

fun String.parseVersion(): KotlinVersion? {
    val (major, minor, patch) = split(".").map { it.toIntOrNull() ?: return null }
        .takeIf { it.size == 3 } ?: return null
    return KotlinVersion(major = major, minor = minor, patch = patch)
}

suspend fun updateFileAtomic(dst: Path, tmpWriter: suspend BufferedOutputStream.() -> Unit) {
    val tmp = dst.resolveSibling("${dst.name}.tmp")
    withContext(Dispatchers.IO) {
        BufferedOutputStream(
            Files.newOutputStream(tmp)
        ).use { it.tmpWriter() }
        Files.move(
            tmp, dst,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    }
}

suspend fun HttpClient.downloadFile(url: String, output: OutputStream) {
    val channel: ByteReadChannel = get(url).body()
    withContext(Dispatchers.IO) {
        val buffer = ByteArray(8 * 1024)
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
        }
        output.flush()
    }
}

suspend fun logStream(out: OutputStream) = withContext(Dispatchers.IO) {
    File("./logs").takeIf { it.exists() }
        ?.listFiles { it.name.startsWith("shome.") }
        ?.filterNotNull()
        ?.sortedBy { it.name }
        ?.forEach { file ->
            val input = if (file.name.endsWith(".gz"))
                GZIPInputStream(file.inputStream())
            else
                file.inputStream()
            input.use { it.copyTo(out) }
        }
    Unit
}