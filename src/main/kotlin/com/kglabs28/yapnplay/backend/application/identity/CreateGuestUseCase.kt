package com.kglabs28.yapnplay.backend.application.identity

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
 * `POST /auth/guest`: a new account with a server-picked name. The secret
 * is returned this once; only its hash is stored.
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
    suspend operator fun invoke(): GuestCreated {
        val secret = secrets.newSecret()
        val user = User(
            id = ids.newId(),
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
}
