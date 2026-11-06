package com.kglabs28.yapnplay.backend.application.identity

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.GoogleSignedIn
import com.kglabs28.yapnplay.backend.domain.identity.GoogleIdVerifier
import com.kglabs28.yapnplay.backend.domain.identity.SecretGenerator
import com.kglabs28.yapnplay.backend.domain.identity.SecretHasher
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository
import org.slf4j.LoggerFactory

/**
 * `POST /auth/google` (contract 0.12.5): the web app's sign-in. A Google
 * account already linked gets its account back (with a new secret, since
 * only a hash is kept); a new one gets a fresh account linked to it.
 */
class GoogleSignInUseCase(
    private val verifier: GoogleIdVerifier,
    private val users: UserRepository,
    private val createGuest: CreateGuestUseCase,
    private val secrets: SecretGenerator,
    private val hasher: SecretHasher,
) {
    suspend operator fun invoke(idToken: String): GoogleSignedIn {
        if (idToken.isBlank()) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.BAD_REQUEST)
        if (!verifier.available) {
            log.warn("Google sign-in: unavailable (no web client id on the server)")
            throw ServiceException(ErrorCode.GOOGLE_SIGN_IN_UNAVAILABLE, BackendStrings.GOOGLE_SIGN_IN_UNAVAILABLE)
        }
        val subject = verifier.subjectFor(idToken.trim())
            ?: throw ServiceException(ErrorCode.NOT_AUTHENTICATED, BackendStrings.GOOGLE_SIGN_IN_REJECTED)
        users.findByGoogleSubject(subject)?.let { return restore(it.id) }
        val guest = createGuest()
        if (users.linkGoogle(guest.userId, subject)) {
            log.info("Google sign-in: new account ${guest.userId.take(ID_PREFIX)}")
            return GoogleSignedIn(guest.userId, guest.secret, restored = false)
        }
        // Linked by another sign-in a moment ago: that account wins.
        val linked = users.findByGoogleSubject(subject) ?: throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.BAD_REQUEST)
        return restore(linked.id)
    }

    private suspend fun restore(userId: String): GoogleSignedIn {
        log.info("Google sign-in: account ${userId.take(ID_PREFIX)} brought back")
        val secret = secrets.newSecret()
        users.updateSecretHash(userId, hasher.hash(secret))
        return GoogleSignedIn(userId, secret, restored = true)
    }

    private companion object {
        /** How much of an id goes in the logs. */
        const val ID_PREFIX = 8
        val log = LoggerFactory.getLogger(GoogleSignInUseCase::class.java)
    }
}
