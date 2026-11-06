package com.kglabs28.yapnplay.backend.infrastructure.identity

import com.kglabs28.yapnplay.backend.domain.identity.GoogleIdVerifier
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
 * Google's side of web sign-in: Google's tokeninfo endpoint checks the ID
 * token's signature and expiry, and this server checks it was issued by
 * Google to its own web client ([clientId]). The token is never logged.
 */
class GoogleIdTokenVerifier(private val clientId: String?) : GoogleIdVerifier {

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS)).build()
    private val json = Json { ignoreUnknownKeys = true }

    override val available: Boolean get() = !clientId.isNullOrBlank()

    override suspend fun subjectFor(idToken: String): String? = withContext(Dispatchers.IO) {
        val audience = clientId ?: return@withContext null.also { log.warn("Google sign-in: no web client id set (GOOGLE_WEB_CLIENT_ID)") }
        val response = http.send(
            HttpRequest.newBuilder(URI.create(TOKEN_INFO_URL + URLEncoder.encode(idToken, Charsets.UTF_8)))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        if (response.statusCode() != HTTP_OK) {
            log.warn("Google sign-in: token refused (HTTP ${response.statusCode()})")
            return@withContext null
        }
        val info = json.decodeFromString(TokenInfo.serializer(), response.body())
        when {
            info.aud != audience -> null.also { log.warn("Google sign-in: token is for another app") }
            info.iss !in ISSUERS -> null.also { log.warn("Google sign-in: token not issued by Google") }
            else -> info.sub.takeIf { it.isNotBlank() }
        }
    }

    @Serializable
    private data class TokenInfo(val aud: String = "", val iss: String = "", val sub: String = "")

    private companion object {
        val log = LoggerFactory.getLogger(GoogleIdTokenVerifier::class.java)
        const val TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token="
        val ISSUERS = setOf("accounts.google.com", "https://accounts.google.com")
        const val TIMEOUT_SECONDS = 10L
        const val HTTP_OK = 200
    }
}
