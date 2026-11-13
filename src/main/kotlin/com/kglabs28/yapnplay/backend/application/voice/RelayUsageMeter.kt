package com.kglabs28.yapnplay.backend.application.voice

import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.voice.RelayUsageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

/** This month's voice relay use, for the admin dashboard and the relay cap. */
data class RelayUsage(val month: String, val bytes: Long)

/**
 * Adds up the voice relay (TURN) traffic the phones report. The month's total is
 * read from the database once and kept in memory; new reports are written every
 * few minutes, and only when there is something new, so checking the total (the
 * relay cap, every few seconds) never wakes the database.
 */
class RelayUsageMeter(
    private val store: RelayUsageStore,
    private val clock: Clock,
    scope: CoroutineScope,
) {
    private val pending = AtomicLong()
    @Volatile private var savedMonth = ""
    @Volatile private var saved = 0L

    init {
        scope.launch {
            while (true) {
                delay(BackendConstants.RELAY_FLUSH_MS)
                flush()
            }
        }
    }

    /** A phone's relayed bytes since its last report; nonsense values are ignored. */
    fun record(bytes: Long) {
        if (bytes in 1..BackendConstants.RELAY_REPORT_MAX_BYTES) pending.addAndGet(bytes)
    }

    suspend fun thisMonth(): RelayUsage {
        val month = monthOf(clock.nowMillis())
        return RelayUsage(month, savedFor(month) + pending.get())
    }

    suspend fun flush() {
        val bytes = pending.getAndSet(0)
        if (bytes == 0L) return
        val now = clock.nowMillis()
        val month = monthOf(now)
        runCatching {
            val before = savedFor(month)
            store.add(month, bytes, now)
            saved = before + bytes
        }.onFailure {
            pending.addAndGet(bytes)
            log.warn("Voice relay usage not saved yet (${it.javaClass.simpleName})")
        }
    }

    /** The month's saved total, read from the database only when the month is new to this server. */
    private suspend fun savedFor(month: String): Long {
        if (month != savedMonth) {
            saved = runCatching { store.bytes(month) }.getOrElse { return saved }
            savedMonth = month
        }
        return saved
    }

    private fun monthOf(millis: Long): String = MONTH.format(Instant.ofEpochMilli(millis))

    private companion object {
        val log = LoggerFactory.getLogger(RelayUsageMeter::class.java)
        val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC)
    }
}
