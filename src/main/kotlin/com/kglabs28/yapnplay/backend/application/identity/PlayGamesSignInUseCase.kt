package com.kglabs28.yapnplay.backend.application.identity

import org.slf4j.LoggerFactory
import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.PlayGamesSignedIn
import com.kglabs28.yapnplay.backend.domain.identity.PlayGamesVerifier
import com.kglabs28.yapnplay.backend.domain.identity.SecretGenerator
import com.kglabs28.yapnplay.backend.domain.identity.SecretHasher
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository

/**
 * `POST /auth/play-games` (contract 0.4.3), Clash-of-Clans style: play as a
 * guest, Play Games keeps the account. A Play Games player already linked to
 * an account gets that account back (new phone, reinstall); the caller's
 * guest is left as it is. A new player is linked to the caller's account
 * (or a fresh one without a bearer). Only a secret's hash is stored, so a
 * restored account gets a new secret: other phones using the old one must
 * sign in with Play Games again.
 */
class PlayGamesSignInUseCase(
    private val verifier: PlayGamesVerifier,
    private val users: UserRepository,
    private val authenticate: AuthenticateUseCase,
    private val createGuest: CreateGuestUseCase,
    private val secrets: SecretGenerator,
    private val hasher: SecretHasher,
) {
    /** [bearerToken] is the caller's `<userId>.<secret>`, when they sent one. */
    suspend operator fun invoke(serverAuthCode: String, bearerToken: String?): PlayGamesSignedIn {
        if (serverAuthCode.isBlank()) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.BAD_REQUEST)
        if (!verifier.available) {
            log.warn("Play Games sign-in: unavailable (no client secret on the server)")
            throw ServiceException(ErrorCode.PLAY_GAMES_UNAVAILABLE, BackendStrings.PLAY_GAMES_UNAVAILABLE)
        }
        val playerId = verifier.playerIdFor(serverAuthCode.trim())
            ?: throw ServiceException(ErrorCode.NOT_AUTHENTICATED, BackendStrings.PLAY_GAMES_REJECTED).also { log.warn("Play Games sign-in: Google did not accept the code") }
        val caller = bearerToken?.let { authenticate(it) }
        log.info("Play Games sign-in: caller ${caller?.id?.take(ID_PREFIX) ?: "none"}")

        users.findByPlayGamesId(playerId)?.let {
            log.info("Play Games sign-in: player already linked to ${it.id.take(ID_PREFIX)} (${if (it.id == caller?.id) "same account" else "restoring it"})")
            return signInAs(it.id, caller?.id, bearerToken)
        }

        // Link the caller's account; it fails when that account already belongs to
        // another Play Games player, or this player was linked a moment ago.
        if (caller != null && users.linkPlayGames(caller.id, playerId)) {
            log.info("Play Games sign-in: linked the caller's account ${caller.id.take(ID_PREFIX)}")
            return PlayGamesSignedIn(caller.id, secretOf(bearerToken), restored = false)
        }
        log.info("Play Games sign-in: could not link the caller's account (none, or linked to another player)")
        users.findByPlayGamesId(playerId)?.let { return signInAs(it.id, caller?.id, bearerToken) }
        // No usable caller account: a fresh one for this player.
        val guest = createGuest()
        if (users.linkPlayGames(guest.userId, playerId)) return PlayGamesSignedIn(guest.userId, guest.secret, restored = false)
        val linked = users.findByPlayGamesId(playerId) ?: throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.BAD_REQUEST)
        return signInAs(linked.id, caller?.id, bearerToken)
    }

    /** The account linked to the player: the caller's own (secret unchanged) or a restore with a new secret. */
    private suspend fun signInAs(linkedId: String, callerId: String?, bearerToken: String?): PlayGamesSignedIn {
        if (linkedId == callerId) return PlayGamesSignedIn(linkedId, secretOf(bearerToken), restored = false)
        val secret = secrets.newSecret()
        users.updateSecretHash(linkedId, hasher.hash(secret))
        return PlayGamesSignedIn(linkedId, secret, restored = true)
    }

    private fun secretOf(bearerToken: String?): String =
        bearerToken.orEmpty().substringAfter(SEPARATOR, missingDelimiterValue = "")

    private companion object {
        const val SEPARATOR = "."
        /** How much of an id goes in the logs. */
        const val ID_PREFIX = 8
        val log = LoggerFactory.getLogger(PlayGamesSignInUseCase::class.java)
    }
}
