package de.quati.shome.model

import kotlinx.serialization.Serializable


@Serializable
data class Profile(
    val id: ProfileId,
    val name: String? = null,
    val positions: Map<Mac, Position> = emptyMap(),
    val cronJobTime: CronJobTime? = null,
)