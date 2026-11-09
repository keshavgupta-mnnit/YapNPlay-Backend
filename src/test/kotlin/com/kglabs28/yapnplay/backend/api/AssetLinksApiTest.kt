package com.kglabs28.yapnplay.backend.api

import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.support.serverTest
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssetLinksApiTest {

    @Test
    fun `every listed fingerprint is a SHA-256 in AA colon BB form, with no duplicates`() {
        val fingerprints = BackendConstants.ANDROID_CERT_SHA256S
        assertTrue(fingerprints.size > 1, "debug plus the Play Console keys")
        assertTrue(fingerprints.all { Regex("([0-9A-F]{2}:){31}[0-9A-F]{2}").matches(it) }, "mistyped fingerprint: $fingerprints")
        assertEquals(fingerprints.size, fingerprints.toSet().size)
    }

    @Test
    fun `assetlinks json lets the signed app handle the host's links, without sign-in`() = serverTest { client ->
        val response = client.get("/.well-known/assetlinks.json")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()?.match(ContentType.Application.Json) == true)

        val statement = Json.parseToJsonElement(response.bodyAsText()).jsonArray.single().jsonObject
        assertEquals(
            listOf("delegate_permission/common.handle_all_urls"),
            statement.getValue("relation").jsonArray.map { it.jsonPrimitive.content },
        )
        val target = statement.getValue("target").jsonObject
        assertEquals("android_app", target.getValue("namespace").jsonPrimitive.content)
        assertEquals("com.kglabs28.yapnplay", target.getValue("package_name").jsonPrimitive.content)
        assertEquals(
            BackendConstants.ANDROID_CERT_SHA256S,
            target.getValue("sha256_cert_fingerprints").jsonArray.map { it.jsonPrimitive.content },
        )
    }
}
