package com.kglabs28.yapnplay.backend.contract

import com.kglabs28.yapnplay.backend.support.testJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.test.assertEquals

/**
 * Golden files: `src/test/resources/golden/<Message>.json` is the exact
 * JSON the contract promises. For every message the server must write
 * exactly that JSON and read it back to the same value.
 *
 * Changing a message on purpose: run `gradlew test -Dgolden.update=true`,
 * check the changed files in the diff, bump the contract version, and copy
 * the files to the app so its test agrees.
 */
class GoldenContractTest {
    private val dir = File(GOLDEN_DIR)
    private val update = System.getProperty(UPDATE_PROPERTY).toBoolean()
    private val pretty = Json(testJson) { prettyPrint = true }

    @TestFactory
    fun `every message matches its golden file`(): List<DynamicTest> =
        GoldenSamples.all.map { case -> DynamicTest.dynamicTest(case.name) { check(case) } }

    private fun <T> check(case: GoldenCase<T>) {
        val file = File(dir, case.name + EXTENSION)
        val written: JsonElement = testJson.encodeToJsonElement(case.serializer, case.sample)
        if (update || !file.exists()) {
            dir.mkdirs()
            file.writeText(pretty.encodeToString(JsonElement.serializer(), written) + "\n")
        }
        val golden = testJson.parseToJsonElement(file.readText())
        assertEquals(golden, written, "${case.name}: server writes different JSON than the golden file")
        assertEquals(case.sample, testJson.decodeFromJsonElement(case.serializer, golden), "${case.name}: golden file reads back differently")
    }

    private companion object {
        const val GOLDEN_DIR = "src/test/resources/golden"
        const val EXTENSION = ".json"
        const val UPDATE_PROPERTY = "golden.update"
    }
}
