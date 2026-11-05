package com.kglabs28.yapnplay.backend.application.rooms

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.common.IdGenerator
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoom
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoomRepository
import com.kglabs28.yapnplay.backend.domain.rooms.RoomCodeGenerator

/**
 * Stores a new room with its creator as the first member, leader and admin,
 * under a free code. Random codes collide rarely; the unique column catches
 * it and another code is tried.
 */
class PlayRoomInserter(
    private val rooms: PlayRoomRepository,
    private val codes: RoomCodeGenerator,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend fun insert(creatorId: String, name: String, icon: String, temporary: Boolean): PlayRoom {
        repeat(BackendConstants.ROOM_CODE_MAX_ATTEMPTS) {
            val now = clock.nowMillis()
            val room = PlayRoom(
                id = ids.newId(),
                code = codes.newCode(),
                name = name,
                description = null,
                icon = icon,
                createdBy = creatorId,
                leaderId = creatorId,
                adminId = creatorId,
                selectedGame = null,
                botFill = false,
                createdAt = now,
                temporary = temporary,
            )
            if (rooms.insertWithCreator(room, now)) return room
        }
        throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.CODE_SPACE_EXHAUSTED)
    }
}
