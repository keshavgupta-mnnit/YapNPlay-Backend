package com.kglabs28.yapnplay.backend.api.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal

/** The authenticated user id; only used inside `authenticate { }` routes. */
val ApplicationCall.userId: String
    get() = checkNotNull(principal<UserPrincipal>()).userId
