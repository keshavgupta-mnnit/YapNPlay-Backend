package com.kglabs28.yapnplay.backend.application.identity

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.domain.identity.User
import com.kglabs28.yapnplay.backend.domain.identity.UserCodeGenerator
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository

/**
 * Makes sure a user has their unique friend ID. New guests get one at
 * sign-up; accounts made before profiles get one the first time they ask.
 */
class UserCodeAssigner(
    private val users: UserRepository,
    private val codes: UserCodeGenerator,
) {
    suspend fun ensure(user: User): User {
        if (user.userCode != null) return user
        repeat(BackendConstants.USER_CODE_MAX_ATTEMPTS) {
            if (users.assignCode(user.id, codes.newCode())) return users.findById(user.id) ?: user
        }
        throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.CODE_SPACE_EXHAUSTED)
    }
}
