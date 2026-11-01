package com.kglabs28.yapnplay.backend.api.http

import com.kglabs28.yapnplay.backend.application.match.GetMatchHistoryUseCase
import com.kglabs28.yapnplay.backend.contract.AdRewardBody
import com.kglabs28.yapnplay.backend.application.economy.CoinsService
import io.ktor.server.routing.post
import com.kglabs28.yapnplay.backend.contract.InviteToRoomBody
import com.kglabs28.yapnplay.backend.application.rooms.RoomInvitesUseCase
import com.kglabs28.yapnplay.backend.api.auth.userId
import com.kglabs28.yapnplay.backend.application.identity.GetMeUseCase
import com.kglabs28.yapnplay.backend.application.identity.UpdateProfileUseCase
import com.kglabs28.yapnplay.backend.application.match.GetActiveMatchUseCase
import com.kglabs28.yapnplay.backend.application.rooms.GetMyRoomsUseCase
import com.kglabs28.yapnplay.backend.application.rooms.PreviewPlayRoomUseCase
import com.kglabs28.yapnplay.backend.application.stats.GetOnlineStatsUseCase
import com.kglabs28.yapnplay.backend.contract.UpdateProfileBody
import io.ktor.http.HttpStatusCode
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
    activeMatch: GetActiveMatchUseCase,
    roomInvites: RoomInvitesUseCase,
    coins: CoinsService,
    history: GetMatchHistoryUseCase,
) {
    get(HttpPaths.ME) { call.respond(getMe(call.userId)) }
    patch(HttpPaths.ME) { call.respond(updateProfile(call.userId, call.receive<UpdateProfileBody>())) }
    get(HttpPaths.MY_ROOMS) { call.respond(myRooms(call.userId)) }
    get(HttpPaths.ROOM_PREVIEW) {
        call.respond(preview(call.userId, call.parameters[HttpPaths.CODE_PARAM].orEmpty()))
    }
    get(HttpPaths.ONLINE_STATS) { call.respond(onlineStats()) }
    post(HttpPaths.AD_REWARD) { call.respond(coins.adReward(call.userId, call.receive<AdRewardBody>().adId)) }
    get(HttpPaths.LEAGUES) { call.respond(coins.leagues(call.userId)) }
    get(HttpPaths.EMOJI_GIFTS) { call.respond(coins.emojiGiftPrices(call.userId)) }
    get(HttpPaths.AD_STATUS) { call.respond(coins.adStatus(call.userId)) }
    get(HttpPaths.ROOM_INVITES) { call.respond(roomInvites.list(call.userId)) }
    post(HttpPaths.INVITE_TO_ROOM) {
        val roomId = call.parameters[HttpPaths.ROOM_ID_PARAM].orEmpty()
        val body = call.receive<InviteToRoomBody>()
        call.respond(roomInvites.invite(call.userId, roomId, body.userIds, body.userCodes))
    }
    post(HttpPaths.ACCEPT_INVITE) {
        call.respond(roomInvites.accept(call.userId, call.parameters[HttpPaths.INVITE_ID_PARAM].orEmpty()))
    }
    post(HttpPaths.DECLINE_INVITE) {
        roomInvites.decline(call.userId, call.parameters[HttpPaths.INVITE_ID_PARAM].orEmpty())
        call.respond(HttpStatusCode.NoContent)
    }
    get(HttpPaths.MATCH_HISTORY) { call.respond(history(call.userId)) }
    get(HttpPaths.ACTIVE_MATCH) {
        val match = activeMatch(call.userId)
        if (match == null) call.respond(HttpStatusCode.NoContent) else call.respond(match)
    }
}
