package com.kglabs28.yapnplay.backend.application.rooms

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.rooms.actor.RoomResult

/** Turns a room failure back into the exception the callers handle. */
fun RoomResult.orThrow(): RoomResult {
    if (this is RoomResult.Failed) throw ServiceException(code, message)
    return this
}
