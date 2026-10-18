package com.kglabs28.yapnplay.backend.api.errors

import com.kglabs28.yapnplay.backend.contract.ErrorCode
import kotlinx.serialization.Serializable

/** HTTP error body from the contract: `{"code": "<ErrorCode>", "message": "…"}`. */
@Serializable
data class ErrorBody(
    val code: ErrorCode,
    val message: String,
)
