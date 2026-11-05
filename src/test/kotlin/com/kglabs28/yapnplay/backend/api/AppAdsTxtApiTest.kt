package com.kglabs28.yapnplay.backend.api

import com.kglabs28.yapnplay.backend.support.serverTest
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppAdsTxtApiTest {

    @Test
    fun `app-ads txt names Google as a direct seller for the app's publisher, without sign-in`() = serverTest { client ->
        val response = client.get("/app-ads.txt")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()?.match(ContentType.Text.Plain) == true)
        assertEquals("google.com, pub-9904831041173979, DIRECT, f08c47fec0942fa0", response.bodyAsText().trim())
    }
}
