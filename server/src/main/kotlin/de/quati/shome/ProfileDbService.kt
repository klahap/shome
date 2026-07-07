package de.quati.shome

import de.quati.shome.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert

private object Profiles : Table("profiles") {
    val id = text("id")
    val name = text("name").nullable()
    val cronTime = text("cron_time").nullable()
    val positions = text("positions")
    override val primaryKey = PrimaryKey(id)
}

class ProfileDbService {
    private val db = Database.connect("jdbc:sqlite:./db.sqlite", "org.sqlite.JDBC")
    val profileState: StateFlow<Map<ProfileId, Profile>>
        field = MutableStateFlow(emptyMap())

    private suspend fun <T> transaction(
        readOnly: Boolean,
        statement: suspend Transaction.() -> T,
    ): T = withContext(Dispatchers.IO) {
        suspendTransaction(db = db, readOnly = readOnly) {
            statement().also {
                if (readOnly) return@also
                profileState.update { // sync state
                    loadProfilesStmt().associateBy { it.id }
                }
            }
        }
    }

    init {
        runBlocking {
            transaction(readOnly = false) {
                SchemaUtils.create(Profiles)
            }
        }
    }

    private fun loadProfilesStmt(
        id: ProfileId? = null,
    ) = Profiles.selectAll()
        .where(id?.let { Profiles.id eq it.value } ?: Op.TRUE)
        .map { row ->
            Profile(
                id = ProfileId(row[Profiles.id]),
                name = row[Profiles.name],
                positions = Json.decodeFromString<Map<Mac, Position>>(row[Profiles.positions]),
                cronJobTime = row[Profiles.cronTime]?.let(CronJobTime::parse)
            )
        }

    suspend fun upsertProfile(profile: Profile): Unit = transaction(readOnly = false) {
        Profiles.upsert {
            it[Profiles.id] = profile.id.value
            it[Profiles.name] = profile.name
            it[Profiles.cronTime] = profile.cronJobTime?.toString()
            it[Profiles.positions] = Json.encodeToString(profile.positions)
        }
    }

    suspend fun deleteProfile(profileId: ProfileId): Unit = transaction(readOnly = false) {
        Profiles.deleteWhere { Profiles.id eq profileId.value }
    }
}
