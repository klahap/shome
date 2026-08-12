package de.quati.shome

import de.quati.shome.model.Mac
import de.quati.shome.model.NetworkEndpoint
import de.quati.shome.model.Position
import de.quati.shome.model.BackendConfig
import de.quati.shome.model.ShellyState
import de.quati.shome.model.BackendIntent
import de.quati.shome.model.BackendMessage
import de.quati.shome.model.OtfState
import de.quati.shome.model.ProfileId
import de.quati.shome.model.ShellyIntent
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.collections.plus
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BackendStateService(
    backendConfigContext: BackendConfig.Context,
    val profileDbService: ProfileDbService,
    val shellyService: ShellyService,
    val otfService: OtfService?,
) : BackendConfig.Context by backendConfigContext {
    companion object {
        private val log = LoggerFactory.getLogger(BackendStateService::class.java)!!
    }

    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineName("BackendStateService"))
    private val defaultOtfState = if (otfService == null) OtfState.DISABLED
    else OtfState.ENABLED

    val backendMessageFlow: SharedFlow<BackendMessage>
        field = MutableSharedFlow(
            extraBufferCapacity = 16,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val state: StateFlow<BackendMessage.State>
        field = MutableStateFlow(
            BackendMessage.State(
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

        scope.launch {
            reloadShellys()
        }

        // heartbeat, keeps /api/state connections alive so stale ones are noticed and reconnected
        scope.launch {
            while (true) {
                delay(15.seconds)
                backendMessageFlow.emit(BackendMessage.Heartbeat)
            }
        }
    }

    suspend fun onIntent(intent: BackendIntent): Any = when (intent) {
        BackendIntent.StartSearchShellysInSubnet -> reloadShellys()
        BackendIntent.OTFSearchLatestVersion -> otfSearchLatestVersion()
        BackendIntent.OTFRun -> {
            val version = state.value.latestVersion ?: return Unit
            state.update { it.copy(otfState = OtfState.UPDATING) }
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
        log.debug("finished onIntent: $intent")
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

    private suspend fun otfSearchLatestVersion() {
        state.update { it.copy(otfState = OtfState.SEARCHING) }
        try {
            val version = otfService?.searchLatestVersion()
            state.update { it.copy(latestVersion = version) }
        } catch (_: CancellationException) {
        } catch (e: Exception) {
            log.error("error while searching for latest version", e)
        } finally {
            state.update { it.copy(otfState = defaultOtfState) }
        }
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
        endpoints: Set<NetworkEndpoint>? = null,
    ) {
        val endpoints = endpoints ?: sweepPort(rootIp = backendConfig.backendIPv4).toSet()
        state.update { it.copy(isSearchingShellys = true) }
        backendMessageFlow.emit(BackendMessage.ShellySearching("searching for shellys (${endpoints.size} endpoints)"))
        try {
            log.debug("searching for shellys (${endpoints.size} endpoints)")

            var found = 0
            val newShellyStates = endpoints.mapIndexedNotNull { idx, ip ->
                val result = shellyService.findShelly(ip)?.let { ip to it }
                if (result != null) found++
                val ipsLeft = endpoints.size - idx - 1
                backendMessageFlow.emit(BackendMessage.ShellySearching("$found Shelly's found, $ipsLeft IP's left"))
                result
            }.associate { it.second.mac to it.second }
            backendMessageFlow.emit(
                BackendMessage.ShellySearching("${newShellyStates.size} Shelly(s) not found")
            )
            if (newShellyStates.isNotEmpty())
                state.update { s ->
                    val newShellys = s.shellys.mapValues {
                        it.value.update(newShellyStates[it.key])
                    } + newShellyStates.filterKeys { it !in s.shellys.keys }
                    s.copy(
                        shellys = newShellys,
                        isSearchingShellys = false,
                    )
                }
            log.info("${newShellyStates.size} shellys updated (searched in ${endpoints.size} IP's)")
        } catch (_: CancellationException) {
        } catch (e: Exception) {
            log.error("reload shellys failed", e)
        } finally {
            state.update { it.copy(isSearchingShellys = false) }
        }
    }

    private fun updateShellyState(mac: Mac, block: (ShellyState) -> ShellyState) = state.update { backendState ->
        val state = backendState.shellys[mac] ?: return@update backendState
        val newState = block(state)
        backendState.copy(shellys = backendState.shellys + (mac to newState))
    }

    private val Mac.ipOrNull get() = state.value.shellys[this]?.endpoint
}
