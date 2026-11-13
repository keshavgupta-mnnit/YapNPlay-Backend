package com.kglabs28.yapnplay.backend.domain

import com.kglabs28.yapnplay.backend.config.DotEnvFile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DotEnvFileTest {

    private fun fileWith(text: String): File = File.createTempFile("env", ".txt").apply {
        writeText(text)
        deleteOnExit()
    }

    @Test
    fun `reads keys, skips comments and blanks, strips quotes`() {
        val env = DotEnvFile(fileWith("# comment\nTURN_URLS=turn:turn.example.com:3478\n\nNAME=\"quoted\"\nEMPTY=\n")).read()
        assertEquals("turn:turn.example.com:3478", env["TURN_URLS"])
        assertEquals("quoted", env["NAME"])
        assertNull(env["EMPTY"])
    }

    @Test
    fun `real environment variables win over the file`() {
        val lookup = DotEnvFile(fileWith("PORT=1111\nDEV_MODE=true")).withEnvironment { key -> if (key == "PORT") "2222" else null }
        assertEquals("2222", lookup("PORT"))
        assertEquals("true", lookup("DEV_MODE"))
    }

    @Test
    fun `a missing file changes nothing`() {
        val lookup = DotEnvFile(File("does-not-exist.env")).withEnvironment { null }
        assertNull(lookup("TURN_URLS"))
    }
}
