package com.kglabs28.yapnplay.backend.application.push

/** A data push (FCM data message): the keys are the contract's PushKeys. */
data class PushMessage(
    val data: Map<String, String>,
)
