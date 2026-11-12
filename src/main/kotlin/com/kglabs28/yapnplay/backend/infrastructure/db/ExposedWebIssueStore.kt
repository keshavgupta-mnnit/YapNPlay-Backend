package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.weblog.WebIssueState
import com.kglabs28.yapnplay.backend.domain.weblog.WebIssueStore
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.WebIssuesTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

class ExposedWebIssueStore(private val db: DbQuery) : WebIssueStore {
    override suspend fun closed(): Map<String, WebIssueState> = db.run {
        WebIssuesTable.selectAll().associate {
            it[WebIssuesTable.fingerprint] to WebIssueState(
                fingerprint = it[WebIssuesTable.fingerprint],
                closedAt = it[WebIssuesTable.closedAt],
                closedBy = it[WebIssuesTable.closedBy],
                note = it[WebIssuesTable.note],
            )
        }
    }

    override suspend fun close(state: WebIssueState) {
        db.run {
            WebIssuesTable.upsert {
                it[fingerprint] = state.fingerprint
                it[closedAt] = state.closedAt
                it[closedBy] = state.closedBy
                it[note] = state.note
            }
        }
    }

    override suspend fun reopen(fingerprint: String) {
        db.run { WebIssuesTable.deleteWhere { WebIssuesTable.fingerprint eq fingerprint } }
    }
}
