package com.kglabs28.yapnplay.backend.application.common

import com.kglabs28.yapnplay.backend.contract.ErrorCode

/**
 * A request the server refuses, with the contract's error [code]. HTTP
 * turns it into a status + error body, the WebSocket into `Rejected`.
 */
class ServiceException(
    val code: ErrorCode,
    override val message: String,
) : RuntimeException(message)
