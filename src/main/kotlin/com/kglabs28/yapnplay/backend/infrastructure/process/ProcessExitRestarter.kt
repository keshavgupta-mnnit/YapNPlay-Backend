package com.kglabs28.yapnplay.backend.infrastructure.process

import com.kglabs28.yapnplay.backend.domain.admin.ServerRestarter
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

/**
 * Restarts by ending the process a moment after the reply has gone out. The
 * normal shutdown runs first (running matches are saved and come back), then
 * Render, seeing the service stopped, starts it again.
 */
class ProcessExitRestarter : ServerRestarter {
    override fun restartSoon() {
        Thread {
            Thread.sleep(REPLY_GRACE_MILLIS)
            log.warn("Restarting: ending the process for the host to start it again")
            exitProcess(RESTART_EXIT_CODE)
        }.apply { isDaemon = false; name = THREAD_NAME }.start()
    }

    private companion object {
        val log = LoggerFactory.getLogger(ProcessExitRestarter::class.java)
        const val REPLY_GRACE_MILLIS = 1_000L
        /** Non-zero, so the host treats it as a stop to recover from. */
        const val RESTART_EXIT_CODE = 1
        const val THREAD_NAME = "admin-restart"
    }
}
