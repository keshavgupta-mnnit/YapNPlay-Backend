package com.kglabs28.yapnplay.backend.config

import java.io.File

/**
 * Reads a `.env` file (KEY=value per line, `#` comments) for running on
 * the laptop. The file is git-ignored, so secrets typed into it never
 * reach GitHub. Real environment variables (the host's settings) always
 * win over the file; on a host without the file nothing changes.
 */
class DotEnvFile(private val file: File = File(DEFAULT_NAME)) {

    fun read(): Map<String, String> {
        if (!file.isFile) return emptyMap()
        return file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith(COMMENT) && SEPARATOR in it }
            .associate { line ->
                val key = line.substringBefore(SEPARATOR).trim()
                val value = line.substringAfter(SEPARATOR).trim().removeSurrounding(QUOTE)
                key to value
            }
            .filterValues { it.isNotEmpty() }
    }

    /** Environment first, then the file. */
    fun withEnvironment(environment: (String) -> String? = System::getenv): (String) -> String? {
        val values = read()
        return { key -> environment(key) ?: values[key] }
    }

    private companion object {
        const val DEFAULT_NAME = ".env"
        const val COMMENT = "#"
        const val SEPARATOR = '='
        const val QUOTE = "\""
    }
}
