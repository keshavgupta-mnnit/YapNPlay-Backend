package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.application.match.MatchCheckpointStore
import com.kglabs28.yapnplay.backend.application.match.actor.MatchCheckpoint
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.ActiveMatchesTable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert
import org.slf4j.LoggerFactory

/** [MatchCheckpointStore] on PostgreSQL: one JSON row per running match. */
class ExposedMatchCheckpointStore(private val db: DbQuery, private val json: Json) : MatchCheckpointStore {

    override suspend fun save(checkpoint: MatchCheckpoint, at: Long) {
        val text = json.encodeToString(MatchCheckpoint.serializer(), checkpoint)
        db.run {
            ActiveMatchesTable.upsert {
                it[id] = checkpoint.setup.matchId
                it[gameId] = checkpoint.setup.gameId
                it[roomId] = checkpoint.setup.roomId
                it[version] = checkpoint.version
                it[updatedAt] = at
                it[state] = text
            }
        }
    }

    override suspend fun delete(matchId: String) {
        db.run { ActiveMatchesTable.deleteWhere { id eq matchId } }
    }

    override suspend fun loadAll(): List<MatchCheckpoint> {
        val rows = db.run { ActiveMatchesTable.selectAll().map { it[ActiveMatchesTable.id] to it[ActiveMatchesTable.state] } }
        return rows.mapNotNull { (matchId, text) ->
            runCatching { json.decodeFromString(MatchCheckpoint.serializer(), text) }
                .onFailure {
                    log.warn("Saved match $matchId no longer reads; dropping it", it)
                    delete(matchId)
                }
                .getOrNull()
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(ExposedMatchCheckpointStore::class.java)
    }
}
