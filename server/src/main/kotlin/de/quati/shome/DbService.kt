package de.quati.shome

import de.quati.shome.model.Profile
import de.quati.shome.model.ProfileId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText


class DbService {
    private val path = Path("./db.json")
    private val json = Json
    private val mutex = Mutex()

    val state: StateFlow<DbData>
        field = MutableStateFlow(DbData())

    suspend fun load() = updateDb { null }
    suspend fun loadProfile(id: ProfileId) = load().profiles[id]

    suspend fun upsertProfile(profile: Profile) = updateDb { data ->
        data.copy(profiles = data.profiles + (profile.id to profile))
    }

    suspend fun deleteProfile(profileId: ProfileId) = updateDb { data ->
        data.copy(profiles = data.profiles - profileId)
    }

    private suspend fun updateDb(block: (DbData) -> DbData?): DbData = mutex.withLock {
        val oldData = path.takeIf { it.exists() }?.readText()
            ?.let { json.decodeFromString<DbData>(it) }
            ?: DbData()
        val newData = block(oldData)
        newData?.let { json.encodeToString(it) }?.also { text ->
            updateFileAtomic(path) { write(text.toByteArray()) }
            val tmp = path.resolveSibling("${path.name}.tmp").also { it.writeText(text) }
            Files.move(
                tmp, path,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        }
        (newData ?: oldData).also { currentData ->
            state.update { currentData }
        }
    }
}
