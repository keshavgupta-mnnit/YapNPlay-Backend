package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.social.Device
import com.kglabs28.yapnplay.backend.domain.social.DeviceRepository
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.DevicesTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

/** [DeviceRepository] on PostgreSQL. */
class ExposedDeviceRepository(private val db: DbQuery) : DeviceRepository {

    override suspend fun upsert(device: Device, at: Long) {
        db.run {
            DevicesTable.upsert {
                it[userId] = device.userId.toUuid()
                it[token] = device.token
                it[platform] = device.platform
                it[updatedAt] = at
            }
        }
    }

    override suspend fun devicesOf(userIds: Collection<String>): List<Device> {
        if (userIds.isEmpty()) return emptyList()
        return db.run {
            DevicesTable.selectAll().where { DevicesTable.userId inList userIds.map { it.toUuid() } }
                .map { Device(it[DevicesTable.userId].toString(), it[DevicesTable.token], it[DevicesTable.platform]) }
        }
    }

    override suspend fun deleteToken(token: String) {
        db.run { DevicesTable.deleteWhere { DevicesTable.token eq token } }
    }
}
