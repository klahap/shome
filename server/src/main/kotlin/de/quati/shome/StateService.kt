package de.quati.shome

import de.quati.shome.model.Mac
import de.quati.shome.model.NetworkEndpoint
import de.quati.shome.model.Position
import de.quati.shome.model.ServerConfig
import de.quati.shome.model.ShellyInfo
import de.quati.shome.model.ShellyState
import de.quati.shome.model.SmartHomeIntent
import de.quati.shome.model.SmartHomeState
import io.ktor.server.application.Application
import io.ktor.server.application.log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlin.collections.plus
import kotlin.time.Clock

class StateService(
    serverConfigContext: ServerConfig.Context,
    val app: Application,
    val shellyService: ShellyService,
) : ServerConfig.Context by serverConfigContext {
    val state: StateFlow<SmartHomeState>
        field = MutableStateFlow(SmartHomeState())

    suspend fun onIntent(intent: SmartHomeIntent): Any = when (intent) {
        is SmartHomeIntent.FixShellyWebhooks -> fixShellyWebhooks(intent.mac)
        SmartHomeIntent.StartSearchShellysInSubnet -> searchShellys()
        is SmartHomeIntent.RemoveShelly -> state.update { s ->
            s.copy(shellys = s.shellys - intent.mac)
        }
        is SmartHomeIntent.StartSearchShellys -> searchShellys(intent.endpoints)
        is SmartHomeIntent.MoveTo -> moveTo(mac = intent.mac, pos = intent.pos)
        is SmartHomeIntent.ShellyWebhookEvent -> updateShellyState(intent.mac) {
            it.update(intent)
        }
    }.also {
        app.log.info("finished onIntent: $intent")
    }

    private suspend fun moveTo(mac: Mac, pos: Position) {
        val shelly = state.value.shellys[mac] ?: return
        val info = shelly.info as? ShellyInfo.Valid ?: return
        val now = Clock.System.now()
        val currentState = shelly.latestEvent.at(shellyInfo = info, newTimeStamp = now)
        val currentDirection = currentState.direction

        val distanceAndDirection = currentState.startPos.distanceAndDirectionTo(pos)
        if (distanceAndDirection == null) {
            if (currentDirection != null) // current position is correct, but we are moving -> so stop
                shellyService.coverDrive(ip = info.endpoint, direction = currentDirection.opposite)
            return
        }

        val (distance, direction) = distanceAndDirection
        val delayStop = if (pos.isApproxEnd())
            null
        else
            info.computeDuration(direction = direction, distance = distance)
        shellyService.coverDrive(ip = info.endpoint, direction = direction)
        if (delayStop != null) {
            delay(delayStop)
            shellyService.coverDrive(ip = info.endpoint, direction = direction.opposite)
        }
    }

    private suspend fun fixShellyWebhooks(mac: Mac) {
        val ip = state.value.shellys[mac]?.info?.endpoint ?: return
        shellyService.fixWebhooks(ip)
        updateInvalidInfo(mac) { info ->
            info.copy(webhooksValid = true)
        }
    }

    private suspend fun searchShellys(
        endpoints: Set<NetworkEndpoint> = getSubnetEndpoints(),
    ) {
        val stateBefore = state.getAndUpdate { it.copy(shellySearchState = SmartHomeState.ShellySearchState.Searching) }
            .shellySearchState
        if (stateBefore != SmartHomeState.ShellySearchState.None) return // already searching
        app.log.info("searching for shellys (${endpoints.size} endpoints)")
        val shellyStates = shellyService.findAllShellys(endpoints = endpoints)
        state.update { s ->
            val newShellys = shellyStates.updateLatestEvents(s)
            s.copy(
                shellys = s.shellys + newShellys,
                shellySearchState = SmartHomeState.ShellySearchState.Result(
                    macBefore = s.shellys.keys,
                    macFound = newShellys.keys,
                ),
            )
        }
        app.log.info("${shellyStates.size} shellys found and updated")
    }

    private fun updateShellyState(mac: Mac, block: (ShellyState) -> ShellyState) = state.update { backendState ->
        val state = backendState.shellys[mac] ?: return@update backendState
        val newState = block(state)
        backendState.copy(shellys = backendState.shellys + (mac to newState))
    }

    private fun updateInvalidInfo(mac: Mac, block: (ShellyInfo.Invalid) -> ShellyInfo) =
        updateShellyState(mac) { shellyState ->
            val newInfo = shellyState.info.updateInvalid(block)
            shellyState.copy(info = newInfo)
        }

    private fun getSubnetEndpoints(): Set<NetworkEndpoint> {
        val serverIp = serverConfig.serverIPv4
        return (1..254).map { it.toUByte() }.mapNotNull {
            val ip = serverIp.copy(d = it)
            if (ip == serverIp) return@mapNotNull null
            NetworkEndpoint(host = ip, port = null)
        }.toSet()
    }

    companion object {
        private fun List<ShellyState>.updateLatestEvents(state: SmartHomeState): Map<Mac, ShellyState> {
            val prevEvents = state.shellys.mapValues { it.value.latestEvent }
            return map { state ->
                val prevEvent = prevEvents[state.info.mac] ?: return@map state
                state.copy(latestEvent = prevEvent)
            }.associateBy { it.info.mac }
        }
    }
}
