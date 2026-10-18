package com.kglabs28.yapnplay.backend.api.errors

import com.kglabs28.yapnplay.backend.contract.ErrorCode
import io.ktor.http.HttpStatusCode

/** The HTTP status for each contract error code. */
fun ErrorCode.httpStatus(): HttpStatusCode = when (this) {
    ErrorCode.NOT_AUTHENTICATED -> HttpStatusCode.Unauthorized
    ErrorCode.NOT_FOUND -> HttpStatusCode.NotFound
    ErrorCode.NOT_MEMBER, ErrorCode.NOT_LEADER, ErrorCode.NOT_ADMIN -> HttpStatusCode.Forbidden
    ErrorCode.ROOM_FULL, ErrorCode.ALREADY_IN_ROOM, ErrorCode.ROOM_LIMIT_REACHED,
    ErrorCode.ALREADY_IN_QUEUE, ErrorCode.STALE_VERSION -> HttpStatusCode.Conflict
    ErrorCode.RATE_LIMITED -> HttpStatusCode.TooManyRequests
    else -> HttpStatusCode.BadRequest
}
