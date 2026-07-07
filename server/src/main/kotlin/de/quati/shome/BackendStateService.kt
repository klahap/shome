package de.quati.shome

import de.quati.shome.model.Mac
import de.quati.shome.model.NetworkEndpoint
import de.quati.shome.model.Position
import de.quati.shome.model.BackendConfig
import de.quati.shome.model.ShellyState
import de.quati.shome.model.BackendIntent
import de.quati.shome.model.BackendState
import de.quati.shome.model.ProfileId
import de.quati.shome.model.ShellyIntent
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
    val profileDbService: ProfileDbService,
    val shellyService: ShellyService,
    val otfService: OtfService?,
) : BackendConfig.Context by backendConfigContext {
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineName("BackendStateService"))
    private val defaultOtfState = if (otfService == null) BackendState.OtfState.DISABLED
    else BackendState.OtfState.ENABLED
    val state: StateFlow<BackendState>
        field = MutableStateFlow(
            BackendState(
                currentVersion = BuildInfo.VERSION,
                otfState = defaultOtfState,
            )
        )

    init {
        // update profiles
        scope.launch {
            profileDbService.profileState.collect { profiles ->
                state.update {
                    it.copy(profiles = profiles)
                }
            }
        }

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
        BackendIntent.OTFSearchLatestVersion -> {
            state.update { it.copy(otfState = BackendState.OtfState.SEARCHING) }
            val version = otfService?.searchLatestVersion()
            state.update {
                it.copy(
                    latestVersion = version,
                    otfState = defaultOtfState,
                )
            }
        }

        BackendIntent.OTFRun -> {
            val version = state.value.latestVersion ?: return Unit
            state.update { it.copy(otfState = BackendState.OtfState.UPDATING) }
            otfService?.update(version)
            state.update { it.copy(otfState = defaultOtfState) }
        }

        is BackendIntent.StartSearchShellys -> reloadShellys(intent.endpoints)
        is BackendIntent.UpsertProfile -> profileDbService.upsertProfile(intent.data)
        is BackendIntent.ExecuteProfile -> moveTo(intent.id)
        is BackendIntent.DeleteProfile -> profileDbService.deleteProfile(intent.id)
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

    private suspend fun moveTo(id: ProfileId) {
        val positions = profileDbService.profileState.value[id]?.positions ?: return
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
        intent.sysSetConfig?.also {
            shellyService.setSysConfig(ip = ip, entry = it)
        }
        intent.coverSetConfig?.also {
            shellyService.setSysConfig(ip = ip, entry = it)
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
