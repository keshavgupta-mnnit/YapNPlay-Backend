package com.kglabs28.yapnplay.backend.api.http

import com.kglabs28.yapnplay.backend.api.auth.userId
import com.kglabs28.yapnplay.backend.application.rooms.GetRoomLeaderboardUseCase
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** A room's standings (call inside `authenticate { }`). */
fun Route.roomStatsRoutes(leaderboard: GetRoomLeaderboardUseCase) {
    get(HttpPaths.ROOM_LEADERBOARD) { call.respond(leaderboard(call.userId, call.parameters[HttpPaths.ROOM_ID_PARAM].orEmpty())) }
}
