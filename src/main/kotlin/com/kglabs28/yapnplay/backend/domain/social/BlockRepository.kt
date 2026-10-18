package com.kglabs28.yapnplay.backend.domain.social

/** Who blocked whom. */
interface BlockRepository {
    suspend fun block(blockerId: String, blockedId: String, at: Long)
    suspend fun unblock(blockerId: String, blockedId: String)
    suspend fun isBlocked(blockerId: String, blockedId: String): Boolean
    /** Everyone [blockerId] blocked, newest first. */
    suspend fun blockedBy(blockerId: String): List<BlockRecord>
}
