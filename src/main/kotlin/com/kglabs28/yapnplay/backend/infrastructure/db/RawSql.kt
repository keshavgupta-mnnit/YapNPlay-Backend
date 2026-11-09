package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.application.admin.StatsRange
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.LongColumnType
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.StatementType
import java.sql.ResultSet

/*
 * Plain read-only SQL for the admin dashboard's aggregates (GROUP BY and
 * FILTER read more clearly than the DSL there). Values always go in as `?`
 * arguments, never into the text.
 */

/** Every row of a SELECT, mapped. */
internal fun <T> Transaction.rows(
    sql: String,
    args: List<Pair<IColumnType<*>, Any?>> = emptyList(),
    map: (ResultSet) -> T,
): List<T> = exec(sql.trimIndent(), args, StatementType.SELECT) { rs ->
    buildList { while (rs.next()) add(map(rs)) }
}.orEmpty()

/** The one row of an aggregate SELECT, mapped. */
internal fun <T> Transaction.single(
    sql: String,
    args: List<Pair<IColumnType<*>, Any?>> = emptyList(),
    map: (ResultSet) -> T,
): T = rows(sql, args, map).first()

/** The two `?`s of a `x >= ? AND x < ?` filter: from (included), to (left out). */
internal fun StatsRange.args(): List<Pair<IColumnType<*>, Any?>> =
    listOf(LongColumnType() to fromMillis, LongColumnType() to toMillis)
