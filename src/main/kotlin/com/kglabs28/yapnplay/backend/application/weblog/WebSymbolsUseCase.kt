package com.kglabs28.yapnplay.backend.application.weblog

import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.weblog.WebSymbolStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/** What an upload kept: the build and how many function names its map has. */
@Serializable
data class WebSymbolsUploaded(val version: String, val functions: Int)

/**
 * Web builds' symbol maps: each release uploads its own (the gzipped file the build wrote, first
 * line naming the build), kept in the database so every report of that build decodes at once, old
 * ones too, with no backend deploy. The newest [KEEP] builds are kept; the few in use are held
 * read (parsed) in memory.
 */
class WebSymbolsUseCase(private val store: WebSymbolStore, private val clock: Clock) {
    private val lock = Mutex()
    /** Parsed maps by build, most recently used last; a build without a map is remembered as null. */
    private val cache = LinkedHashMap<String, WebSymbolMap?>(CACHED, LOAD_FACTOR, true)

    /** Keeps [gzipped] as [version]'s map; refused (null) when it isn't a map of that build. */
    suspend fun upload(version: String, gzipped: ByteArray): WebSymbolsUploaded? {
        val map = WebSymbolMap.fromGzip(gzipped)?.takeIf { it.version == version } ?: return null
        store.put(version, clock.nowMillis(), gzipped)
        store.keepNewest(KEEP)
        lock.withLock { cache[version] = map }
        return WebSymbolsUploaded(version, map.names.size)
    }

    /** [version]'s map, or null while its release hasn't uploaded one. */
    suspend fun mapFor(version: String): WebSymbolMap? = lock.withLock {
        if (cache.containsKey(version)) return@withLock cache[version]
        val map = store.get(version)?.let(WebSymbolMap::fromGzip)
        cache[version] = map
        while (cache.size > CACHED) cache.remove(cache.keys.first())
        map
    }

    private companion object {
        const val KEEP = 20
        const val CACHED = 4
        const val LOAD_FACTOR = 0.75f
    }
}
