package de.quati.shome.model

import kotlinx.serialization.Serializable


@Serializable
data class BackendState(
    val shellySearchState: ShellySearchState = ShellySearchState.None,
    val shellys: Map<Mac, ShellyState> = emptyMap(),
) {
    val profiles: Map<ProfileName, Map<Mac, Position>>
        get() = shellys.values.flatMap { s ->
            s.profiles.map { (k, v) ->
                ProfileTriple(mac = s.mac, profileName = k, position = v)
            }
        }.groupBy { it.profileName }
            .mapValues { (_, v) -> v.associate { it.mac to it.position } }

    private data class ProfileTriple(
        val mac: Mac,
        val profileName: ProfileName,
        val position: Position,
    )

    @Serializable
    sealed interface ShellySearchState {

        @Serializable
        data object None : ShellySearchState

        @Serializable
        data object Searching : ShellySearchState

        @Serializable
        data class Result(
            val macBefore: Set<Mac>,
            val macFound: Set<Mac>,
        ) : ShellySearchState
    }

}

