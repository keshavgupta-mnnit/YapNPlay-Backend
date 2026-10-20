package com.kglabs28.yapnplay.backend.infrastructure.identity

import com.kglabs28.yapnplay.backend.domain.identity.PlayGamesVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Google's side of Play Games sign-in: the server auth code is exchanged at
 * Google's OAuth token endpoint (with this server's web client id and
 * secret), then the access token asks Play Games who the player is.
 * Nothing secret is logged.
 */
class GooglePlayGamesVerifier(
    private val clientId: String,
    private val clientSecret: String?,
) : PlayGamesVerifier {

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS)).build()
    private val json = Json { ignoreUnknownKeys = true }

    override val available: Boolean get() = !clientSecret.isNullOrBlank()

    override suspend fun playerIdFor(serverAuthCode: String): String? = withContext(Dispatchers.IO) {
        val secret = clientSecret ?: return@withContext null
        val form = mapOf(
            "code" to serverAuthCode,
            "client_id" to clientId,
            "client_secret" to secret,
            // Server auth codes from Android Play Games have no redirect.
            "redirect_uri" to "",
            "grant_type" to "authorization_code",
        ).entries.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, Charsets.UTF_8) }
        val tokenResponse = http.send(
            HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        if (tokenResponse.statusCode() != HTTP_OK) {
            log.warn("Play Games code exchange refused: HTTP ${tokenResponse.statusCode()}")
            return@withContext null
        }
        val accessToken = json.decodeFromString(TokenReply.serializer(), tokenResponse.body()).access_token
        val playerResponse = http.send(
            HttpRequest.newBuilder(URI.create(PLAYER_URL))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Authorization", "Bearer $accessToken")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        if (playerResponse.statusCode() != HTTP_OK) {
            log.warn("Play Games player lookup refused: HTTP ${playerResponse.statusCode()}")
            return@withContext null
        }
        json.decodeFromString(PlayerReply.serializer(), playerResponse.body()).playerId.takeIf { it.isNotBlank() }
    }

    @Suppress("PropertyName")
    @Serializable
    private data class TokenReply(val access_token: String)

    @Serializable
    private data class PlayerReply(val playerId: String = "")

    private companion object {
        val log = LoggerFactory.getLogger(GooglePlayGamesVerifier::class.java)
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val PLAYER_URL = "https://www.googleapis.com/games/v1/players/me"
        const val TIMEOUT_SECONDS = 10L
        const val HTTP_OK = 200
    }
}
