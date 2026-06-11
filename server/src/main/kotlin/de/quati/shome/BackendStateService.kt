package de.quati.shome

import de.quati.shome.model.Mac
import de.quati.shome.model.NetworkEndpoint
import de.quati.shome.model.Position
import de.quati.shome.model.BackendConfig
import de.quati.shome.model.ShellyState
import de.quati.shome.model.BackendIntent
import de.quati.shome.model.BackendState
import de.quati.shome.model.KvsKey
import de.quati.shome.model.ProfileName
import de.quati.shome.model.ShellyIntent
import de.quati.shome.model.ShellyRpcRequest
import io.ktor.server.application.Application
import io.ktor.server.application.log
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.collections.plus
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

class BackendStateService(
    backendConfigContext: BackendConfig.Context,
    val app: Application,
    val shellyService: ShellyService,
) : BackendConfig.Context by backendConfigContext {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineName("BackendStateService"))
    val state: StateFlow<BackendState>
        field = MutableStateFlow(BackendState())

    init {
        // position update job
        scope.launch {
            while (true) {
                delay(1000.milliseconds)
                updatePositions()
            }
        }
    }

    suspend fun onIntent(intent: BackendIntent): Any = when (intent) {
        BackendIntent.StartSearchShellysInSubnet -> reloadShellys()
        is BackendIntent.StartSearchShellys -> reloadShellys(intent.endpoints)
        is BackendIntent.UpsertProfile -> setProfile(intent.name, intent.positions)
        is BackendIntent.ExecuteProfile -> moveTo(intent.name)
        is BackendIntent.DeleteProfile -> setProfile(intent.name, emptyMap())
        is BackendIntent.Shelly -> when (val sIntent = intent.intent) {
            ShellyIntent.Delete -> state.update { s -> s.copy(shellys = s.shellys - intent.mac) }
            ShellyIntent.Reload -> reloadShelly(intent.mac)
            is ShellyIntent.MoveTo -> moveTo(mac = intent.mac, pos = sIntent.pos)
            is ShellyIntent.WebhookEventReceived -> updateShellyState(intent.mac) { it.update(sIntent) }
            is ShellyIntent.Update -> updateShelly(intent.mac, sIntent)
        }
    }.also {
        app.log.info("finished onIntent: $intent")
    }

    suspend fun setProfile(name: ProfileName, positions: Map<Mac, Position>) {
        val before = state.value.shellys.mapNotNull { (k, v) ->
            val pos = v.profiles[name] ?: return@mapNotNull null
            k to pos
        }.toMap()

        val toDelete = before.keys - positions.keys
        val toUpsert = positions.filter { (k, v) -> before[k] != v }

        toDelete.forEach { mac -> // TODO parallel?
            val ip = mac.ipOrNull ?: return@forEach
            shellyService.deleteKvs(ip, KvsKey.Profile(name))
        }
        toUpsert.forEach { (mac, position) -> // TODO parallel?
            val ip = mac.ipOrNull ?: return@forEach
            shellyService.setKvs(ip, ShellyRpcRequest.Params.KvsEntry.profile(name, position))
        }
        state.update { s ->
            val newShellys = s.shellys.mapValues { (_, state) ->
                if (state.mac in toDelete)
                    return@mapValues state.update(profiles = state.profiles - name)
                val newPos = toUpsert[state.mac] ?: return@mapValues state
                state.update(profiles = state.profiles + (name to newPos))
            }
            s.copy(shellys = newShellys)
        }
    }

    fun updatePositions(): Boolean {
        val anyMovement = state.value.shellys.values
            .filterIsInstance<ShellyState.Valid>()
            .any { it.latestEvent.direction != null }
        if (!anyMovement) return false
        val now = Clock.System.now()
        state.update { s ->
            val newShellys = s.shellys.mapValues { (_, shelly) ->
                when (shelly) {
                    is ShellyState.Invalid -> shelly
                    is ShellyState.Valid -> shelly.update(now)
                }
            }
            s.copy(shellys = newShellys)
        }
        return true
    }

    private suspend fun moveTo(name: ProfileName) {
        val positions = state.value.shellys.mapNotNull { (k, v) ->
            val pos = v.profiles[name] ?: return@mapNotNull null
            k to pos
        }.toMap()
        positions.map { (mac, pos) ->
            scope.async { moveTo(mac, pos) }
        }.awaitAll()
    }

    private suspend fun moveTo(mac: Mac, pos: Position) {
        val shelly = state.value.shellys[mac]
            ?.let { it as? ShellyState.Valid }
            ?.update(newTimeStamp = Clock.System.now())
            ?: return
        val currentDirection = shelly.latestEvent.direction

        val distanceAndDirection = shelly.latestEvent.position.distanceAndDirectionTo(pos)
        if (distanceAndDirection == null) {
            if (currentDirection != null) // current position is correct, but we are moving -> so stop
                shellyService.coverDrive(ip = shelly.endpoint, direction = currentDirection.opposite)
            return
        }

        val (distance, direction) = distanceAndDirection
        val delayStop = if (pos.isApproxEnd())
            null
        else
            shelly.computeDuration(direction = direction, distance = distance)

        val firstMoveJob = scope.launch {
            shellyService.coverDrive(ip = shelly.endpoint, direction = direction)
        }
        if (delayStop != null) {
            delay(delayStop)
            shellyService.coverDrive(ip = shelly.endpoint, direction = direction.opposite)
        }
        firstMoveJob.join()
    }

    private suspend fun updateShelly(mac: Mac, intent: ShellyIntent.Update) {
        val ip = mac.ipOrNull ?: return
        intent.setConfig?.also {
            shellyService.setConfig(ip = ip, entry = it)
        }
        intent.kvsEntries.forEach { entry ->
            shellyService.setKvs(ip, entry)
        }
        if (intent.fixWebhooks)
            shellyService.fixWebhooks(ip)
        reloadShellys(endpoints = setOf(ip))
    }

    private suspend fun reloadShelly(mac: Mac) {
        val ip = mac.ipOrNull ?: return
        reloadShellys(endpoints = setOf(ip))
    }

    private suspend fun reloadShellys(
        endpoints: Set<NetworkEndpoint> = getSubnetEndpoints(),
    ) {
        state.update { it.copy(shellySearchState = BackendState.ShellySearchState.Searching) }
        app.log.info("searching for shellys (${endpoints.size} endpoints)")
        val newShellyStates = shellyService.findAllShellys(endpoints = endpoints)
            .associateBy { it.mac }
        state.update { s ->
            val newShellys = s.shellys.mapValues {
                it.value.update(newShellyStates[it.key])
            } + newShellyStates.filterKeys { it !in s.shellys.keys }
            s.copy(
                shellys = newShellys,
                shellySearchState = BackendState.ShellySearchState.Result(
                    macBefore = s.shellys.keys,
                    macFound = newShellyStates.keys,
                ),
            )
        }
        app.log.info("${newShellyStates.size} shellys updated")
    }

    private fun updateShellyState(mac: Mac, block: (ShellyState) -> ShellyState) = state.update { backendState ->
        val state = backendState.shellys[mac] ?: return@update backendState
        val newState = block(state)
        backendState.copy(shellys = backendState.shellys + (mac to newState))
    }

    private fun getSubnetEndpoints(): Set<NetworkEndpoint> {
        val serverIp = backendConfig.backendIPv4
        return (1..254).map { it.toUByte() }.mapNotNull {
            val ip = serverIp.copy(d = it)
            if (ip == serverIp) return@mapNotNull null
            NetworkEndpoint(host = ip, port = null)
        }.toSet()
    }

    private val Mac.ipOrNull get() = state.value.shellys[this]?.endpoint
}
