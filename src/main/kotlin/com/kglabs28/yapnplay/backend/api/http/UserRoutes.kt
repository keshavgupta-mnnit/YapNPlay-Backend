package com.kglabs28.yapnplay.backend.api.http

import com.kglabs28.yapnplay.backend.api.auth.userId
import com.kglabs28.yapnplay.backend.application.identity.GetMeUseCase
import com.kglabs28.yapnplay.backend.application.identity.UpdateProfileUseCase
import com.kglabs28.yapnplay.backend.application.rooms.GetMyRoomsUseCase
import com.kglabs28.yapnplay.backend.application.rooms.PreviewPlayRoomUseCase
import com.kglabs28.yapnplay.backend.application.stats.GetOnlineStatsUseCase
import com.kglabs28.yapnplay.backend.contract.UpdateProfileBody
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch

/** Signed-in HTTP routes (call inside `authenticate { }`). */
fun Route.userRoutes(
    getMe: GetMeUseCase,
    updateProfile: UpdateProfileUseCase,
    myRooms: GetMyRoomsUseCase,
    preview: PreviewPlayRoomUseCase,
    onlineStats: GetOnlineStatsUseCase,
) {
    get(HttpPaths.ME) { call.respond(getMe(call.userId)) }
    patch(HttpPaths.ME) { call.respond(updateProfile(call.userId, call.receive<UpdateProfileBody>())) }
    get(HttpPaths.MY_ROOMS) { call.respond(myRooms(call.userId)) }
    get(HttpPaths.ROOM_PREVIEW) {
        call.respond(preview(call.userId, call.parameters[HttpPaths.CODE_PARAM].orEmpty()))
    }
    get(HttpPaths.ONLINE_STATS) { call.respond(onlineStats()) }
}
