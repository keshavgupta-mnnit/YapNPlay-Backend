package com.kglabs28.yapnplay.backend.application.rooms.actor

import com.kglabs28.yapnplay.backend.games.base.rules.GameDescriptor
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendStrings

/** Permission and precondition checks shared by the room handlers. */
class RoomGuards(private val state: PlayRoomState, private val deps: PlayRoomDeps) {

    fun requireLeader(userId: String) {
        state.member(userId)
        if (state.room.leaderId != userId) throw ServiceException(ErrorCode.NOT_LEADER, BackendStrings.NOT_LEADER)
    }

    fun requireAdmin(userId: String) {
        state.member(userId)
        if (state.room.adminId != userId) throw ServiceException(ErrorCode.NOT_ADMIN, BackendStrings.NOT_ADMIN)
    }

    fun requireTarget(userId: String) {
        if (userId !in state.members) throw ServiceException(ErrorCode.NOT_MEMBER, BackendStrings.TARGET_NOT_MEMBER)
    }

    /** The selected game, which must exist and be switched on. */
    fun requireGame(): GameDescriptor {
        val gameId = state.room.selectedGame
            ?: throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.NO_GAME_SELECTED)
        return deps.games.findEnabled(gameId)
            ?: throw ServiceException(ErrorCode.GAME_UNAVAILABLE, BackendStrings.GAME_UNAVAILABLE)
    }
}
