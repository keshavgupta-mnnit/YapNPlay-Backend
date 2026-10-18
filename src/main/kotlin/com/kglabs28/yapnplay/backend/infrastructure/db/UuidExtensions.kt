package com.kglabs28.yapnplay.backend.infrastructure.db

import java.util.UUID

/** Ids are UUID text on the wire and UUID columns in the database. */
fun String.toUuid(): UUID = UUID.fromString(this)

fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
