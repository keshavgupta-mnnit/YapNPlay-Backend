package com.kglabs28.yapnplay.backend.application.identity

import com.kglabs28.yapnplay.backend.config.BackendConstants
import java.util.UUID
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.contract.CreateGuestBody
import com.kglabs28.yapnplay.backend.domain.common.RandomSource
import com.kglabs28.yapnplay.backend.domain.identity.Avatars
import com.kglabs28.yapnplay.backend.contract.GuestCreated
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.common.IdGenerator
import com.kglabs28.yapnplay.backend.domain.identity.GuestNameGenerator
import com.kglabs28.yapnplay.backend.domain.identity.SecretGenerator
import com.kglabs28.yapnplay.backend.domain.identity.SecretHasher
import com.kglabs28.yapnplay.backend.domain.identity.User
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository

/**
 * `POST /auth/guest`: a new account with a server-picked name. Without
 * [CreateGuestBody] the server picks the id and secret (the secret is
 * returned this once). With it, the app's own id and secret are used, and
 * asking again with the same pair returns that same account: a crash after
 * the account was made but before the app saved it never makes another.
 * With a device id and [CreateGuestBody.restore], the account already made
 * on that phone (a reinstall) is handed back with the new secret instead
 * of making a second one; the app asks the player first ([FindDeviceAccountUseCase]).
 * Only the secret's hash is stored.
 */
class CreateGuestUseCase(
    private val users: UserRepository,
    private val ids: IdGenerator,
    private val secrets: SecretGenerator,
    private val hasher: SecretHasher,
    private val names: GuestNameGenerator,
    private val clock: Clock,
    private val codes: UserCodeAssigner,
    private val random: RandomSource,
) {
    suspend operator fun invoke(request: CreateGuestBody? = null): GuestCreated {
        if (request == null) return create(ids.newId(), secrets.newSecret())
        val id = request.userId.trim()
        if (!validId(id) || !SECRET_PATTERN.matches(request.secret)) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.INVALID_GUEST)
        users.findById(id)?.let { return same(it, request.secret) }
        val device = request.deviceId?.trim()?.takeIf { DEVICE_PATTERN.matches(it) }
        // The player chose to continue the account already made on this phone: it comes back with the new secret.
        device?.takeIf { request.restore }?.let { users.findByDeviceId(it) }?.let { existing ->
            users.updateSecretHash(existing.id, hasher.hash(request.secret))
            return GuestCreated(userId = existing.id, secret = request.secret)
        }
        return runCatching { create(id, request.secret).also { if (device != null) users.setDeviceId(id, device) } }
            // Two tries at once: the other one made it; hand back the same account.
            .getOrElse { error -> users.findById(id)?.let { same(it, request.secret) } ?: throw error }
    }

    private suspend fun create(id: String, secret: String): GuestCreated {
        val user = User(
            id = id,
            displayName = names.newName(),
            secretHash = hasher.hash(secret),
            createdAt = clock.nowMillis(),
            // A random preset avatar; the player can change it on their profile.
            avatarId = Avatars.random(random),
        )
        users.insert(user)
        // The unique friend ID is given at sign-up and never changes.
        codes.ensure(user)
        return GuestCreated(userId = user.id, secret = secret)
    }

    /** The account already exists: only its own secret gets it back. */
    private fun same(user: User, secret: String): GuestCreated {
        if (!hasher.matches(secret, user.secretHash)) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.GUEST_TAKEN)
        return GuestCreated(userId = user.id, secret = secret)
    }

    private fun validId(id: String): Boolean = runCatching { UUID.fromString(id).toString() == id.lowercase() }.getOrDefault(false)

    private companion object {
        /** What an app-picked secret may look like: long enough to guess never, short enough to store. */
        val SECRET_PATTERN = Regex("[A-Za-z0-9_-]{32,128}")
        /** Android's per-app id is 16 hex digits; anything else of this shape is accepted too. */
        val DEVICE_PATTERN = BackendConstants.DEVICE_ID_PATTERN
    }
}
