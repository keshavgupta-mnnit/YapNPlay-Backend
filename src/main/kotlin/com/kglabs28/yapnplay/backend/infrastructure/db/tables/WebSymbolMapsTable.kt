package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

/** Mirrors V30__web_symbol_maps.sql. */
object WebSymbolMapsTable : Table("web_symbol_maps") {
    val version = varchar("version", 40)
    val uploadedAt = long("uploaded_at")
    val data = binary("data")
    override val primaryKey = PrimaryKey(version)
}
