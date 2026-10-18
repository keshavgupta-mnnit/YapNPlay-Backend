package com.kglabs28.yapnplay.backend.domain

import com.kglabs28.yapnplay.backend.domain.rooms.RoomCodeGenerator
import com.kglabs28.yapnplay.backend.infrastructure.security.SecureRandomSource
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomCodeGeneratorTest {
    private val generator = RoomCodeGenerator(SecureRandomSource())

    @Test
    fun `every code is six digits`() {
        repeat(10_000) {
            val code = generator.newCode()
            assertTrue(code.length == 6 && code.all(Char::isDigit), code)
        }
    }

    @Test
    fun `codes are spread out, not repeating`() {
        // 1,000 codes out of a million possible: a handful of repeats at most.
        val distinct = List(1_000) { generator.newCode() }.toSet().size
        assertTrue(distinct > 990, "only $distinct distinct codes")
    }

    @Test
    fun `validation accepts six digits only`() {
        assertTrue(generator.isValid("482193"))
        assertFalse(generator.isValid("48219"))
        assertFalse(generator.isValid("48219A"))
        assertFalse(generator.isValid("4821933"))
    }
}
