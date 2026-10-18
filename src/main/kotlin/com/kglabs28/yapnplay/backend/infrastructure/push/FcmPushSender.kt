package com.kglabs28.yapnplay.backend.infrastructure.push

import com.kglabs28.yapnplay.backend.application.push.PushMessage
import com.kglabs28.yapnplay.backend.application.push.PushSender
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.social.DeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * Firebase Cloud Messaging (HTTP v1). The service account signs a short
 * JWT (RS256) that Google swaps for an access token (cached ~1 hour); each
 * device gets one data message. Tokens Firebase rejects as gone are deleted.
 */
class FcmPushSender(
    private val account: FirebaseServiceAccount,
    private val devices: DeviceRepository,
    private val clock: Clock,
    private val json: Json,
) : PushSender {
    private val http = HttpClient.newHttpClient()
    private val tokenLock = Mutex()
    private var accessToken: String? = null
    private var accessTokenExpiresAt = 0L

    override suspend fun send(userIds: Collection<String>, message: PushMessage) {
        runCatching {
            val targets = devices.devicesOf(userIds)
            if (targets.isEmpty()) return
            val token = accessToken()
            targets.forEach { device -> sendOne(token, device.token, message) }
        }.onFailure { log.warn("Push failed: ${it.message}") }
    }

    private suspend fun sendOne(accessToken: String, deviceToken: String, message: PushMessage) {
        val body = buildJsonObject {
            put(
                MESSAGE,
                buildJsonObject {
                    put(TOKEN, deviceToken)
                    put(DATA, JsonObject(message.data.mapValues { JsonPrimitive(it.value) }))
                    put(ANDROID, buildJsonObject { put(PRIORITY, HIGH) })
                },
            )
        }
        val request = HttpRequest.newBuilder(URI.create(SEND_URL.format(account.projectId)))
            .header(AUTHORIZATION, BEARER + accessToken)
            .header(CONTENT_TYPE, APPLICATION_JSON)
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(JsonObject.serializer(), body)))
            .build()
        val response = withContext(Dispatchers.IO) { http.send(request, HttpResponse.BodyHandlers.ofString()) }
        if (response.statusCode() == NOT_FOUND || UNREGISTERED in response.body()) devices.deleteToken(deviceToken)
    }

    private suspend fun accessToken(): String = tokenLock.withLock {
        val now = clock.nowMillis()
        accessToken?.takeIf { now < accessTokenExpiresAt }?.let { return it }
        val assertion = signedAssertion(now / MILLIS)
        val form = GRANT_TYPE + URLEncoder.encode(JWT_BEARER, Charsets.UTF_8) + ASSERTION + assertion
        val request = HttpRequest.newBuilder(URI.create(account.tokenUri))
            .header(CONTENT_TYPE, FORM)
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val response = withContext(Dispatchers.IO) { http.send(request, HttpResponse.BodyHandlers.ofString()) }
        val answer = json.parseToJsonElement(response.body()).jsonObject
        val token = answer.getValue(ACCESS_TOKEN).jsonPrimitive.content
        val lifetime = answer[EXPIRES_IN]?.jsonPrimitive?.long ?: TOKEN_LIFETIME_SECONDS
        accessToken = token
        accessTokenExpiresAt = now + (lifetime - REFRESH_EARLY_SECONDS) * MILLIS
        token
    }

    private fun signedAssertion(nowSeconds: Long): String {
        val header = buildJsonObject {
            put(ALG, RS256)
            put(TYP, JWT)
        }
        val claims = buildJsonObject {
            put(ISS, account.clientEmail)
            put(SCOPE, MESSAGING_SCOPE)
            put(AUD, account.tokenUri)
            put(IAT, nowSeconds)
            put(EXP, nowSeconds + TOKEN_LIFETIME_SECONDS)
        }
        val unsigned = base64(json.encodeToString(JsonObject.serializer(), header)) + DOT + base64(json.encodeToString(JsonObject.serializer(), claims))
        val signature = Signature.getInstance(SHA256_WITH_RSA).run {
            initSign(KeyFactory.getInstance(RSA).generatePrivate(PKCS8EncodedKeySpec(pemBytes(account.privateKey))))
            update(unsigned.toByteArray(Charsets.UTF_8))
            sign()
        }
        return unsigned + DOT + Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
    }

    private fun pemBytes(pem: String): ByteArray = Base64.getDecoder().decode(
        pem.lines().filterNot { it.startsWith(PEM_DASHES) }.joinToString("").trim(),
    )

    private fun base64(text: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(Charsets.UTF_8))

    private companion object {
        val log = LoggerFactory.getLogger(FcmPushSender::class.java)
        const val SEND_URL = "https://fcm.googleapis.com/v1/projects/%s/messages:send"
        const val MESSAGING_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
        const val JWT_BEARER = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        const val GRANT_TYPE = "grant_type="
        const val ASSERTION = "&assertion="
        const val FORM = "application/x-www-form-urlencoded"
        const val APPLICATION_JSON = "application/json"
        const val CONTENT_TYPE = "Content-Type"
        const val AUTHORIZATION = "Authorization"
        const val BEARER = "Bearer "
        const val ACCESS_TOKEN = "access_token"
        const val EXPIRES_IN = "expires_in"
        const val TOKEN_LIFETIME_SECONDS = 3_600L
        const val REFRESH_EARLY_SECONDS = 300L
        const val MILLIS = 1_000L
        const val NOT_FOUND = 404
        const val UNREGISTERED = "UNREGISTERED"
        const val MESSAGE = "message"
        const val TOKEN = "token"
        const val DATA = "data"
        const val ANDROID = "android"
        const val PRIORITY = "priority"
        const val HIGH = "high"
        const val ALG = "alg"
        const val RS256 = "RS256"
        const val TYP = "typ"
        const val JWT = "JWT"
        const val ISS = "iss"
        const val SCOPE = "scope"
        const val AUD = "aud"
        const val IAT = "iat"
        const val EXP = "exp"
        const val DOT = "."
        const val RSA = "RSA"
        const val SHA256_WITH_RSA = "SHA256withRSA"
        const val PEM_DASHES = "-----"
    }
}
