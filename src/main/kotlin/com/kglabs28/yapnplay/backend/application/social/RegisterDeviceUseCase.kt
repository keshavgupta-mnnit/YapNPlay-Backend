package com.kglabs28.yapnplay.backend.application.social

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.RegisterDevice
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.social.Device
import com.kglabs28.yapnplay.backend.domain.social.DeviceRepository

/** `POST /devices`: remembers this phone's Firebase token (re-sent whenever it changes). */
class RegisterDeviceUseCase(
    private val devices: DeviceRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(userId: String, body: RegisterDevice) {
        if (body.token.isBlank() || body.token.length > MAX_TOKEN_LENGTH) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.BAD_REQUEST)
        devices.upsert(Device(userId, body.token, body.platform), clock.nowMillis())
    }

    private companion object {
        const val MAX_TOKEN_LENGTH = 512
    }
}
