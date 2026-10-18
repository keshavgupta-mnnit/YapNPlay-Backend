package com.kglabs28.yapnplay.backend.api.auth

/** The signed-in user of a call or WebSocket. Never taken from a message body. */
data class UserPrincipal(val userId: String)
