package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.social.BlockRecord
import com.kglabs28.yapnplay.backend.domain.social.BlockRepository
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.BlocksTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll

/** [BlockRepository] on PostgreSQL. */
class ExposedBlockRepository(private val db: DbQuery) : BlockRepository {

    override suspend fun block(blockerId: String, blockedId: String, at: Long) {
        db.run {
            BlocksTable.insertIgnore {
                it[BlocksTable.blockerId] = blockerId.toUuid()
                it[BlocksTable.blockedId] = blockedId.toUuid()
                it[createdAt] = at
            }
        }
    }

    override suspend fun unblock(blockerId: String, blockedId: String) {
        val blocker = blockerId.toUuidOrNull() ?: return
        val blocked = blockedId.toUuidOrNull() ?: return
        db.run { BlocksTable.deleteWhere { (BlocksTable.blockerId eq blocker) and (BlocksTable.blockedId eq blocked) } }
    }

    override suspend fun isBlocked(blockerId: String, blockedId: String): Boolean {
        val blocker = blockerId.toUuidOrNull() ?: return false
        val blocked = blockedId.toUuidOrNull() ?: return false
        return db.run {
            BlocksTable.selectAll().where { (BlocksTable.blockerId eq blocker) and (BlocksTable.blockedId eq blocked) }.count() > 0
        }
    }

    override suspend fun blockedBy(blockerId: String): List<BlockRecord> = db.run {
        BlocksTable.selectAll().where { BlocksTable.blockerId eq blockerId.toUuid() }
            .orderBy(BlocksTable.createdAt, SortOrder.DESC)
            .map { BlockRecord(it[BlocksTable.blockedId].toString(), it[BlocksTable.createdAt]) }
    }
}
