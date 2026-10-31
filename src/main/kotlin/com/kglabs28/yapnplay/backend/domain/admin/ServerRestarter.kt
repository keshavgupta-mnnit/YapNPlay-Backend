package com.kglabs28.yapnplay.backend.domain.admin

/** Stops this server so the host starts it again (a clean slate without a redeploy). */
fun interface ServerRestarter {
    fun restartSoon()
}
