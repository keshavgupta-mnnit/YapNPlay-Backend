package com.kglabs28.yapnplay.backend.api.http

import com.kglabs28.yapnplay.backend.api.auth.userId
import com.kglabs28.yapnplay.backend.application.feedback.FeedbackUseCase
import com.kglabs28.yapnplay.backend.contract.SubmitFeedbackBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** Feedback and support (call inside `authenticate { }`): send one, list yours, read one. */
fun Route.feedbackRoutes(feedback: FeedbackUseCase) {
    post(HttpPaths.FEEDBACK) {
        val body = call.receive<SubmitFeedbackBody>()
        call.respond(HttpStatusCode.Created, feedback.submit(call.userId, body.kind, body.message))
    }
    get(HttpPaths.FEEDBACK) { call.respond(feedback.mine(call.userId)) }
    get(HttpPaths.FEEDBACK_TICKET) { call.respond(feedback.thread(call.userId, call.parameters[HttpPaths.TICKET_ID_PARAM].orEmpty())) }
}
