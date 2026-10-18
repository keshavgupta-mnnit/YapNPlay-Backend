package com.kglabs28.yapnplay.backend.application.rooms.actor

import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.JoinRoomResult
import com.kglabs28.yapnplay.backend.contract.RoomSnapshot

/** What a room answers. Small hierarchy, kept together. */
sealed interface RoomResult {
    data object Done : RoomResult
    data class Snapshot(val snapshot: RoomSnapshot) : RoomResult
    data class Joined(val result: JoinRoomResult) : RoomResult
    data class Failed(val code: ErrorCode, val message: String) : RoomResult
}
