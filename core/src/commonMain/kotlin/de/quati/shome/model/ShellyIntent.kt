package de.quati.shome.model

import kotlinx.serialization.Serializable
import kotlin.time.Duration
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
        val fixWebhooks: Boolean,
    ) : ShellyIntent {
        val setConfig
            get() = name?.let { name ->
                ShellyRpcRequest.Params.SetConfig(
                    ShellyRpcRequest.Params.SetConfig.Config(
                        device = ShellyRpcRequest.Params.SetConfig.Config.Device(
                            name = name
                        )
                    )
                )
            }

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