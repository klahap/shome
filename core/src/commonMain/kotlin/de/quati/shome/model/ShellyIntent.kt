package de.quati.shome.model

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.Instant

@Serializable
sealed interface ShellyIntent {
    @Serializable
    data object Reload : ShellyIntent

    @Serializable
    data object Delete : ShellyIntent

    @Serializable
    data class MoveTo(val pos: Position) : ShellyIntent

    @Serializable
    data class Update(
        val name: String?,
        val totalDurationClose: Duration?,
        val totalDurationOpen: Duration?,
        val maxCloseDuration: Duration?,
        val maxOpenDuration: Duration?,
        val swapInputs: Boolean?,
        val invertDirections: Boolean?,
        val fixWebhooks: Boolean,
    ) : ShellyIntent {
        val sysSetConfig
            get() = name?.let { name ->
                ShellyRpcRequest.Params.SysSetConfig(
                    ShellyRpcRequest.Params.SysSetConfig.Config(
                        device = ShellyRpcRequest.Params.SysSetConfig.Config.Device(
                            name = name
                        )
                    )
                )
            }
        val coverSetConfig
            get() = ShellyRpcRequest.Params.CoverSetConfig(
                id = 0,
                ShellyRpcRequest.Params.CoverSetConfig.Config(
                    maxOpenDuration = maxOpenDuration?.toDouble(DurationUnit.SECONDS),
                    maxCloseDuration = maxCloseDuration?.toDouble(DurationUnit.SECONDS),
                    swapInputs = swapInputs,
                    invertDirections = invertDirections
                )
            ).takeIf { !it.config.isEmpty }


        val kvsEntries: List<ShellyRpcRequest.Params.KvsEntry>
            get() = listOfNotNull(
                totalDurationClose?.let {
                    ShellyRpcRequest.Params.KvsEntry.totalDuration(
                        direction = Direction.CLOSE,
                        duration = it
                    )
                },
                totalDurationOpen?.let {
                    ShellyRpcRequest.Params.KvsEntry.totalDuration(
                        direction = Direction.OPEN,
                        duration = totalDurationOpen
                    )
                },
            )
    }

    @Serializable
    data class WebhookEventReceived(
        val event: WebhookEventType.Quati,
        val timestamp: Instant,
    ) : ShellyIntent {
        val direction: Direction = when (event) {
            WebhookEventType.CoverClosing -> Direction.CLOSE
            WebhookEventType.CoverOpening -> Direction.OPEN
        }
    }

}