package com.kglabs28.yapnplay.backend.application

import com.kglabs28.yapnplay.backend.application.voice.RelayUsage
import com.kglabs28.yapnplay.backend.application.voice.RelayUsageMeter
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.voice.RelayUsageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RelayUsageMeterTest {

    private class MemoryStore : RelayUsageStore {
        val months = mutableMapOf<String, Long>()
        var writes = 0
        override suspend fun add(month: String, bytes: Long, at: Long) {
            writes++
            months[month] = (months[month] ?: 0) + bytes
        }
        override suspend fun bytes(month: String): Long = months[month] ?: 0
    }

    /** 2026-11-13 12:00 UTC. */
    private val now = 1_794_571_200_000L

    @Test
    fun `reports add up per UTC month and are written only when there is something new`() = runTest {
        val store = MemoryStore()
        val scope = CoroutineScope(Job())
        val meter = RelayUsageMeter(store, Clock { now }, scope)
        meter.record(1_000)
        meter.record(500)
        meter.record(-5)
        meter.record(Long.MAX_VALUE)
        assertEquals(RelayUsage("2026-11", 1_500), meter.thisMonth())
        meter.flush()
        meter.flush()
        assertEquals(1, store.writes)
        assertEquals(RelayUsage("2026-11", 1_500), meter.thisMonth())
        scope.cancel()
    }
}
