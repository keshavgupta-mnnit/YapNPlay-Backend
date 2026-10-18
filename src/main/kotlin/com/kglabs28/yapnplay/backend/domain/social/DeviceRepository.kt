package com.kglabs28.yapnplay.backend.domain.social

/** Firebase push tokens. */
interface DeviceRepository {
    suspend fun upsert(device: Device, at: Long)
    suspend fun devicesOf(userIds: Collection<String>): List<Device>
    /** Forgets a token Firebase says is no longer valid. */
    suspend fun deleteToken(token: String)
}
