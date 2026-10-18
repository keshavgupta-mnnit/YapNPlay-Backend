package com.kglabs28.yapnplay.backend.plugins

import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.api.errors.ErrorBody
import com.kglabs28.yapnplay.backend.api.errors.httpStatus
import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendStrings
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

/** Every failure becomes the contract's `{code, message}` body with the right status. */
fun Application.configureStatusPages() {
    val log = LoggerFactory.getLogger("StatusPages")
    install(StatusPages) {
        exception<ServiceException> { call, e ->
            call.respond(e.code.httpStatus(), ErrorBody(e.code, e.message))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorBody(ErrorCode.BAD_REQUEST, BackendStrings.BAD_REQUEST))
        }
        exception<Throwable> { call, e ->
            log.error("Unhandled error", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorBody(ErrorCode.BAD_REQUEST, BackendStrings.INTERNAL_ERROR))
        }
        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respond(status, ErrorBody(ErrorCode.RATE_LIMITED, BackendStrings.RATE_LIMITED))
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, ErrorBody(ErrorCode.NOT_FOUND, BackendStrings.NOT_FOUND))
        }
    }
}
