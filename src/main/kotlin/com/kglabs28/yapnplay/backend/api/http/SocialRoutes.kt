package com.kglabs28.yapnplay.backend.api.http

import com.kglabs28.yapnplay.backend.api.auth.userId
import com.kglabs28.yapnplay.backend.application.moderation.ModerationService
import com.kglabs28.yapnplay.backend.application.social.FriendsUseCase
import com.kglabs28.yapnplay.backend.application.social.RegisterDeviceUseCase
import com.kglabs28.yapnplay.backend.application.voice.IssueVoiceTokenUseCase
import com.kglabs28.yapnplay.backend.contract.BlockUserBody
import com.kglabs28.yapnplay.backend.contract.RegisterDevice
import com.kglabs28.yapnplay.backend.contract.SendFriendRequestBody
import com.kglabs28.yapnplay.backend.contract.SendFriendRequestReply
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** Devices, friends, voice passes and penalties (signed in). */
fun Route.socialRoutes(
    registerDevice: RegisterDeviceUseCase,
    friends: FriendsUseCase,
    voice: IssueVoiceTokenUseCase,
    moderation: ModerationService,
) {
    post(HttpPaths.DEVICES) {
        registerDevice(call.userId, call.receive<RegisterDevice>())
        call.respond(HttpStatusCode.NoContent)
    }
    get(HttpPaths.FRIENDS) { call.respond(friends.list(call.userId)) }
    get(HttpPaths.FRIEND_REQUESTS) { call.respond(friends.incoming(call.userId)) }
    post(HttpPaths.FRIEND_REQUESTS) {
        val body = call.receive<SendFriendRequestBody>()
        val outcome = friends.send(call.userId, body.userCode, body.userId)
        call.respond(SendFriendRequestReply(outcome))
    }
    post(HttpPaths.FRIEND_REQUEST_ACCEPT) {
        friends.accept(call.userId, call.parameters[HttpPaths.REQUEST_ID_PARAM].orEmpty())
        call.respond(HttpStatusCode.NoContent)
    }
    post(HttpPaths.FRIEND_REQUEST_DECLINE) {
        friends.decline(call.userId, call.parameters[HttpPaths.REQUEST_ID_PARAM].orEmpty())
        call.respond(HttpStatusCode.NoContent)
    }
    post(HttpPaths.BLOCKS) {
        friends.block(call.userId, call.receive<BlockUserBody>().userId)
        call.respond(HttpStatusCode.NoContent)
    }
    get(HttpPaths.BLOCKS) { call.respond(friends.blocked(call.userId)) }
    delete(HttpPaths.BLOCK) {
        friends.unblock(call.userId, call.parameters[HttpPaths.USER_ID_PARAM].orEmpty())
        call.respond(HttpStatusCode.NoContent)
    }
    delete(HttpPaths.FRIEND) {
        friends.remove(call.userId, call.parameters[HttpPaths.USER_ID_PARAM].orEmpty())
        call.respond(HttpStatusCode.NoContent)
    }
    post(HttpPaths.ROOM_VOICE_TOKEN) {
        call.respond(voice.forRoom(call.userId, call.parameters[HttpPaths.ROOM_ID_PARAM].orEmpty()))
    }
    post(HttpPaths.MATCH_VOICE_TOKEN) {
        call.respond(voice.forMatch(call.userId, call.parameters[HttpPaths.MATCH_ID_PARAM].orEmpty()))
    }
    post(HttpPaths.CLEAR_PENALTY_WITH_AD) {
        moderation.clearWithAd(call.userId)
        call.respond(HttpStatusCode.NoContent)
    }
}
