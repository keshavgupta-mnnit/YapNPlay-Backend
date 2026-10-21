package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.admin.AdminAuditEntry
import com.kglabs28.yapnplay.backend.domain.admin.AdminAuditRepository
import com.kglabs28.yapnplay.backend.domain.settings.SettingsRepository
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

/** Mirrors V12__ad_ref_and_settings.sql. */
object GameSettingsTable : Table("game_settings") {
    val key = varchar("key", KEY_MAX_LENGTH)
    val value = text("value")
    val updatedAt = long("updated_at")
    val updatedBy = varchar("updated_by", BY_MAX_LENGTH)

    override val primaryKey = PrimaryKey(key)
}

/** Mirrors V12__ad_ref_and_settings.sql. */
object AdminAuditTable : Table("admin_audit") {
    val id = long("id").autoIncrement()
    val action = varchar("action", ACTION_MAX_LENGTH)
    val target = varchar("target", KEY_MAX_LENGTH)
    val oldValue = text("old_value").nullable()
    val newValue = text("new_value").nullable()
    val changedBy = varchar("changed_by", BY_MAX_LENGTH)
    val note = varchar("note", NOTE_MAX_LENGTH).nullable()
    val changedAt = long("changed_at")

    override val primaryKey = PrimaryKey(id)
}

class ExposedSettingsRepository(private val db: DbQuery) : SettingsRepository {
    override suspend fun loadAll(): Map<String, String> =
        db.run { GameSettingsTable.selectAll().associate { it[GameSettingsTable.key] to it[GameSettingsTable.value] } }

    override suspend fun save(key: String, value: String, at: Long, by: String) {
        db.run {
            GameSettingsTable.upsert {
                it[GameSettingsTable.key] = key
                it[GameSettingsTable.value] = value
                it[updatedAt] = at
                it[updatedBy] = by.take(BY_MAX_LENGTH)
            }
        }
    }
}

class ExposedAdminAuditRepository(private val db: DbQuery) : AdminAuditRepository {
    override suspend fun record(entry: AdminAuditEntry) {
        db.run {
            AdminAuditTable.insert {
                it[action] = entry.action.take(ACTION_MAX_LENGTH)
                it[target] = entry.target.take(KEY_MAX_LENGTH)
                it[oldValue] = entry.oldValue
                it[newValue] = entry.newValue
                it[changedBy] = entry.changedBy.take(BY_MAX_LENGTH)
                it[note] = entry.note?.take(NOTE_MAX_LENGTH)
                it[changedAt] = entry.changedAt
            }
        }
    }

    override suspend fun recent(limit: Int): List<AdminAuditEntry> = db.run {
        AdminAuditTable.selectAll().orderBy(AdminAuditTable.changedAt, SortOrder.DESC).limit(limit).map {
            AdminAuditEntry(
                it[AdminAuditTable.action], it[AdminAuditTable.target], it[AdminAuditTable.oldValue], it[AdminAuditTable.newValue],
                it[AdminAuditTable.changedBy], it[AdminAuditTable.note], it[AdminAuditTable.changedAt],
            )
        }
    }
}

private const val KEY_MAX_LENGTH = 64
private const val BY_MAX_LENGTH = 64
private const val ACTION_MAX_LENGTH = 32
private const val NOTE_MAX_LENGTH = 200
