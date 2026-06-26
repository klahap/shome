package de.quati.shome

import de.quati.shome.model.BackendIntent
import io.ktor.server.application.Application
import io.ktor.server.application.log
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZoneId

class CronJobService(
    private val app: Application,
    private val backendStateService: BackendStateService,
    private val timeZoneId: ZoneId = ZoneId.of("Europe/Berlin"),
) {
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineName("CronJobService"))

    init {
        scope.launch {
            cronTimeFlow(timeZoneId).collect { cronJobTime ->
                try {
                    backendStateService.state.value.profiles.values
                        .filter { it.cronJobTime == cronJobTime }
                        .map { BackendIntent.ExecuteProfile(it.id) }
                        .forEach {
                            scope.launch {
                                backendStateService.onIntent(it)
                            }
                        }
                } catch (e: Exception) {
                    app.log.error("Error executing cron job", e)
                }
            }
        }

    }
}