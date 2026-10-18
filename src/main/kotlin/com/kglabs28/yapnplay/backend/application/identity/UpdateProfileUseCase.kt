package com.kglabs28.yapnplay.backend.application.identity

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.rooms.PlayRoomActors
import com.kglabs28.yapnplay.backend.application.rooms.RoomPresence
import com.kglabs28.yapnplay.backend.application.rooms.actor.RoomAction
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.Me
import com.kglabs28.yapnplay.backend.contract.UpdateProfileBody
import com.kglabs28.yapnplay.backend.domain.identity.DisplayNamePolicy
import com.kglabs28.yapnplay.backend.domain.identity.ProfilePolicy
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository

/**
 * `PATCH /me`: only the fields sent change (name, @username, avatar).
 * A new name or avatar shows at once in the room the user is in.
 */
class UpdateProfileUseCase(
    private val users: UserRepository,
    private val names: DisplayNamePolicy,
    private val profile: ProfilePolicy,
    private val presence: RoomPresence,
    private val actors: PlayRoomActors,
    private val getMe: GetMeUseCase,
) {
    suspend operator fun invoke(userId: String, body: UpdateProfileBody): Me {
        val name = body.displayName?.let {
            names.normalize(it) ?: throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.INVALID_NAME)
        }
        val username = body.username?.let {
            profile.normalizeUsername(it) ?: throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.INVALID_USERNAME)
        }
        val avatar = body.avatarId?.also {
            if (it.isNotEmpty() && !profile.isValidAvatar(it)) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.INVALID_AVATAR)
        }
        users.findById(userId) ?: throw ServiceException(ErrorCode.NOT_AUTHENTICATED, BackendStrings.NOT_AUTHENTICATED)
        users.updateProfile(userId, name, username, avatar)
        if (name != null || avatar != null) {
            presence.roomOf(userId)?.let { actors.get(it)?.ask(userId, RoomAction.UpdateProfile(name, avatar)) }
        }
        return getMe(userId)
    }
}
