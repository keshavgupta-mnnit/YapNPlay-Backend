package com.kglabs28.yapnplay.backend.infrastructure.voice

import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.contract.IceServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Cloudflare TURN: asks for credentials that last [ttlSeconds] and returns the
 * relay servers. Port-53 addresses are dropped (browsers wait them out, and the
 * app sends its offers only after gathering). The API token is never logged.
 */
class CloudflareTurnClient(private val keyId: String, private val apiToken: String) {

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(BackendConstants.TURN_HTTP_TIMEOUT_SECONDS)).build()
    private val json = Json { ignoreUnknownKeys = true }

    /** The relay servers, or null when Cloudflare can't be reached or refuses. */
    suspend fun iceServers(ttlSeconds: Long): List<IceServer>? = withContext(Dispatchers.IO) {
        runCatching {
            val response = http.send(
                HttpRequest.newBuilder(URI.create(BackendConstants.CLOUDFLARE_TURN_URL.format(keyId)))
                    .timeout(Duration.ofSeconds(BackendConstants.TURN_HTTP_TIMEOUT_SECONDS))
                    .header(AUTHORIZATION, BEARER + apiToken)
                    .header(CONTENT_TYPE, JSON_TYPE)
                    .POST(HttpRequest.BodyPublishers.ofString("{\"ttl\":$ttlSeconds}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            if (response.statusCode() !in HTTP_SUCCESS) {
                log.warn("Cloudflare TURN: credentials refused (HTTP ${response.statusCode()})")
                return@runCatching null
            }
            json.decodeFromString(Generated.serializer(), response.body()).iceServers
                .map { server -> server.copy(urls = server.urls.filterNot { PORT_53.containsMatchIn(it) }) }
                .filter { it.urls.isNotEmpty() }
        }.onFailure { log.warn("Cloudflare TURN: unreachable (${it.javaClass.simpleName})") }.getOrNull()
    }

    @Serializable
    private data class Generated(val iceServers: List<IceServer> = emptyList())

    private companion object {
        val log = LoggerFactory.getLogger(CloudflareTurnClient::class.java)
        const val AUTHORIZATION = "Authorization"
        const val BEARER = "Bearer "
        const val CONTENT_TYPE = "Content-Type"
        const val JSON_TYPE = "application/json"
        /** ":53" as the whole port (not 5349). */
        val PORT_53 = Regex(":53(\\?|$)")
        val HTTP_SUCCESS = 200..299
    }
}
