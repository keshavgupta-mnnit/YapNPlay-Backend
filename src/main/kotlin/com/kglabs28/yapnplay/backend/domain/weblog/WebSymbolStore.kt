package com.kglabs28.yapnplay.backend.domain.weblog

/** Where web builds' symbol maps are kept: the gzipped file as uploaded, one a [version]. */
interface WebSymbolStore {
    suspend fun put(version: String, uploadedAt: Long, gzipped: ByteArray)
    suspend fun get(version: String): ByteArray?
    /** Keeps the [count] newest uploads, drops the rest. */
    suspend fun keepNewest(count: Int)
}
