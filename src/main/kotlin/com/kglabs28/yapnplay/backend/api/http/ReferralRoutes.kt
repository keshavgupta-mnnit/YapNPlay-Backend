package com.kglabs28.yapnplay.backend.api.http

import com.kglabs28.yapnplay.backend.api.auth.userId
import com.kglabs28.yapnplay.backend.application.economy.ClaimReferralUseCase
import com.kglabs28.yapnplay.backend.contract.ClaimReferralBody
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

/** Signed-in: a new player claims the invite bonus (call inside `authenticate { }`). */
fun Route.referralRoutes(claim: ClaimReferralUseCase) {
    post(HttpPaths.REFERRAL_CLAIM) { call.respond(claim(call.userId, call.receive<ClaimReferralBody>().referrerCode)) }
}
