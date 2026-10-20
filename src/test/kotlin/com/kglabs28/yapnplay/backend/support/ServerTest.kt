package com.kglabs28.yapnplay.backend.support

import com.kglabs28.yapnplay.backend.domain.identity.PlayGamesVerifier
import com.kglabs28.yapnplay.backend.contract.GuestCreated
import com.kglabs28.yapnplay.backend.config.BackendConfig
import com.kglabs28.yapnplay.backend.di.BackendOverrides
import com.kglabs28.yapnplay.backend.module
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.post
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json

/** JSON rules identical to the server's. */
val testJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Runs [block] against a fresh server on the shared test database, emptied
 * first unless [resetDatabase] is false (to test a restart).
 */
fun serverTest(
    config: BackendConfig = testConfig(),
    resetDatabase: Boolean = true,
    /** Replaces Google's Play Games check (tests never call Google). */
    playGames: PlayGamesVerifier? = null,
    block: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
) {
    if (resetDatabase) TestDatabase.reset()
    testApplication {
        application { module(config, BackendOverrides(database = TestDatabase.handle, playGames = playGames)) }
        val client = createClient {
            install(ContentNegotiation) { json(testJson) }
            install(WebSockets)
        }
        block(client)
    }
}

/** A new guest's bearer token `<userId>.<secret>`. */
suspend fun HttpClient.newGuestToken(): String {
    val guest = post("/auth/guest").body<GuestCreated>()
    return guest.userId + "." + guest.secret
}

fun String.userIdOfToken(): String = substringBefore('.')
