package com.kglabs28.yapnplay.backend.application.voice

import com.kglabs28.yapnplay.backend.contract.IceServer

/** The STUN / TURN servers handed out with every voice pass. */
fun interface IceServerSource {
    /** STUN, plus the TURN relays when [withRelay]. */
    suspend fun current(withRelay: Boolean): List<IceServer>
}
