package com.kglabs28.yapnplay.backend.infrastructure.ids

import com.kglabs28.yapnplay.backend.domain.common.IdGenerator
import java.util.UUID

/** Random UUIDs. */
class UuidIdGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
