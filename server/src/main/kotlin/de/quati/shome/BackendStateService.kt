package de.quati.shome

import de.quati.shome.model.Mac
import de.quati.shome.model.NetworkEndpoint
import de.quati.shome.model.Position
import de.quati.shome.model.BackendConfig
import de.quati.shome.model.ShellyState
import de.quati.shome.model.BackendIntent
import de.quati.shome.model.BackendState
import de.quati.shome.model.ShellyIntent
import de.quati.shome.model.ShellyRpcRequest
import io.ktor.server.application.Application
import io.ktor.server.application.log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlin.collections.plus
import kotlin.time.Clock

class BackendStateService(
    backendConfigContext: BackendConfig.Context,
    val app: Application,
    val shellyService: ShellyService,
) : BackendConfig.Context by backendConfigContext {
    val state: StateFlow<BackendState>
        field = MutableStateFlow(BackendState())

    suspend fun onIntent(intent: BackendIntent): Any = when (intent) {
        BackendIntent.StartSearchShellysInSubnet -> reloadShellys()
        is BackendIntent.StartSearchShellys -> reloadShellys(intent.endpoints)
        is BackendIntent.Shelly -> when (val sIntent = intent.intent) {
            ShellyIntent.Delete -> state.update { s -> s.copy(shellys = s.shellys - intent.mac) }
            ShellyIntent.FixWebhooks -> fixShellyWebhooks(intent.mac)
            is ShellyIntent.MoveTo -> moveTo(mac = intent.mac, pos = sIntent.pos)
            is ShellyIntent.WebhookEventReceived -> updateShellyState(intent.mac) { it.update(sIntent) }
            is ShellyIntent.SetDurations -> setDurations(intent.mac, sIntent)
            is ShellyIntent.SetConfig -> setConfig(intent.mac, sIntent)
        }
    }.also {
        app.log.info("finished onIntent: $intent")
    }

    private suspend fun moveTo(mac: Mac, pos: Position) {
        val shelly = state.value.shellys[mac]
            ?.let { it as ShellyState.Valid }
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
        shellyService.coverDrive(ip = shelly.endpoint, direction = direction)
        if (delayStop != null) {
            delay(delayStop)
            shellyService.coverDrive(ip = shelly.endpoint, direction = direction.opposite)
        }
    }

    private suspend fun fixShellyWebhooks(mac: Mac) {
        val ip = mac.ipOrNull ?: return
        shellyService.fixWebhooks(ip)
        reloadShellys(endpoints = setOf(ip))
    }

    private suspend fun setConfig(mac: Mac, intent: ShellyIntent.SetConfig) {
        val ip = mac.ipOrNull ?: return
        shellyService.setConfig(
            ip = ip,
            entry = ShellyRpcRequest.Params.SetConfig(
                ShellyRpcRequest.Params.SetConfig.Config(
                    device = ShellyRpcRequest.Params.SetConfig.Config.Device(
                        name = intent.name
                    )
                )
            )
        )
        reloadShellys(endpoints = setOf(ip))
    }

    private suspend fun setDurations(mac: Mac, intent: ShellyIntent.SetDurations) {
        val ip = mac.ipOrNull ?: return
        intent.kvsEntries.forEach { entry ->
            shellyService.setKvs(ip, entry)
        }
        reloadShellys(endpoints = setOf(ip))
    }

    private suspend fun reloadShellys(
        endpoints: Set<NetworkEndpoint> = getSubnetEndpoints(),
    ) {
        val stateBefore = state.getAndUpdate { it.copy(shellySearchState = BackendState.ShellySearchState.Searching) }
            .shellySearchState
        if (stateBefore != BackendState.ShellySearchState.None) return // already searching
        app.log.info("searching for shellys (${endpoints.size} endpoints)")
        val newShellyStates = shellyService.findAllShellys(endpoints = endpoints)
            .associateBy { it.mac }
        state.update { s ->
            val newShellys = s.shellys.mapValues {
                it.value.update(newShellyStates[it.key])
            }
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
