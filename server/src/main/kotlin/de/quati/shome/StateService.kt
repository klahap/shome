package de.quati.shome

import de.quati.shome.model.Mac
import de.quati.shome.model.Position
import de.quati.shome.model.ShellyInfo
import de.quati.shome.model.ShellyState
import de.quati.shome.model.SmartHomeIntent
import de.quati.shome.model.SmartHomeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.collections.plus
import kotlin.time.Clock

class StateService(
    val shellyService: ShellyService,
) {
    val state: StateFlow<SmartHomeState>
        field = MutableStateFlow(SmartHomeState())

    suspend fun onIntent(intent: SmartHomeIntent): Any = when (intent) {
        is SmartHomeIntent.FixShellyWebhooks -> fixShellyWebhooks(intent.mac)
        SmartHomeIntent.StartSearchShellys -> searchShellys()
        is SmartHomeIntent.MoveTo -> moveTo(mac = intent.mac, pos = intent.pos)
        is SmartHomeIntent.ShellyWebhookEvent -> updateShellyState(intent.mac) {
            it.update(intent)
        }
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
                shellyService.coverDrive(ip = info.ip, direction = currentDirection.opposite)
            return
        }

        val (distance, direction) = distanceAndDirection
        val delayStop = if (pos.isApproxEnd())
            null
        else
            info.computeDuration(direction = direction, distance = distance)
        shellyService.coverDrive(ip = info.ip, direction = direction)
        if (delayStop != null) {
            delay(delayStop)
            shellyService.coverDrive(ip = info.ip, direction = direction.opposite)
        }
    }

    private suspend fun fixShellyWebhooks(mac: Mac) {
        val ip = state.value.shellys[mac]?.info?.ip ?: return
        shellyService.fixWebhooks(ip)
        updateInvalidInfo(mac) { info ->
            info.copy(webhooksValid = true)
        }
    }

    private suspend fun searchShellys() {
        if (state.value.isSearchingShellys) return
        state.update { it.copy(isSearchingShellys = true) }
        val states = shellyService.findAllShellys()
        state.update { s ->
            val prevEvents = s.shellys.mapValues { it.value.latestEvent }
            val newShellys = states.map { state ->
                val prevEvent = prevEvents[state.info.mac] ?: return@map state
                state.copy(latestEvent = prevEvent)
            }.associateBy { it.info.mac }
            s.copy(shellys = newShellys, isSearchingShellys = false)
        }
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
}
