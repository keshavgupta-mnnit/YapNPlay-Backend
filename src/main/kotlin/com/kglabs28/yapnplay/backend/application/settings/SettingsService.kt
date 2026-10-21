package com.kglabs28.yapnplay.backend.application.settings

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.domain.admin.AdminAuditEntry
import com.kglabs28.yapnplay.backend.domain.admin.AdminAuditRepository
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.settings.GameSetting
import com.kglabs28.yapnplay.backend.domain.settings.GameSettings
import com.kglabs28.yapnplay.backend.domain.settings.IntListSetting
import com.kglabs28.yapnplay.backend.domain.settings.IntSetting
import com.kglabs28.yapnplay.backend.domain.settings.Settings
import com.kglabs28.yapnplay.backend.domain.settings.SettingsRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Runtime settings: saved in the database, kept in memory, changed from the
 * admin dashboard with no deploy. A setting nobody changed uses its default
 * ([defaults] may replace a built-in default, e.g. from an env variable).
 * Matches read the values when they start, so a change applies to new ones.
 */
class SettingsService(
    private val repository: SettingsRepository,
    private val audit: AdminAuditRepository,
    private val clock: Clock,
    private val defaults: Map<String, Any> = emptyMap(),
) : Settings {
    private val values = ConcurrentHashMap<String, Any>()

    /** At startup: reads every saved value (a bad or unknown one is skipped and logged). */
    suspend fun load() {
        repository.loadAll().forEach { (key, text) ->
            val setting = GameSettings.find(key) ?: return@forEach log.warn("Unknown saved setting $key ignored")
            runCatching { parse(setting, kotlinx.serialization.json.Json.parseToJsonElement(text)) }
                .onSuccess { values[key] = it }
                .onFailure { log.warn("Saved setting $key is not valid; using the default", it) }
        }
    }

    override fun get(setting: IntSetting): Int = values[setting.key] as? Int ?: defaults[setting.key] as? Int ?: setting.default

    @Suppress("UNCHECKED_CAST")
    override fun get(setting: IntListSetting): List<Int> =
        values[setting.key] as? List<Int> ?: defaults[setting.key] as? List<Int> ?: setting.default

    /** Every setting with its current and default value, for the admin page. */
    fun list(): List<SettingView> = GameSettings.ALL.map(::view)

    /** Changes one setting after checking it; recorded in the admin audit. */
    suspend fun update(key: String, value: JsonElement, by: String, note: String?): SettingView {
        val setting = GameSettings.find(key) ?: throw ServiceException(ErrorCode.NOT_FOUND, "No setting $key")
        val parsed = runCatching { parse(setting, value) }.getOrElse { throw ServiceException(ErrorCode.BAD_REQUEST, it.message ?: "Not valid") }
        val old = text(current(setting))
        val now = clock.nowMillis()
        repository.save(key, text(parsed), now, by)
        values[key] = parsed
        audit.record(AdminAuditEntry(AUDIT_SETTING, key, old, text(parsed), by, note, now))
        return view(setting)
    }

    private fun current(setting: GameSetting<*>): Any = when (setting) {
        is IntSetting -> get(setting)
        is IntListSetting -> get(setting)
    }

    private fun defaultOf(setting: GameSetting<*>): Any = defaults[setting.key] ?: setting.default!!

    private fun view(setting: GameSetting<*>) = SettingView(
        key = setting.key,
        group = setting.group.label,
        label = setting.label,
        value = text(current(setting)),
        default = text(defaultOf(setting)),
        hint = when (setting) {
            is IntSetting -> "${setting.min} – ${setting.max}"
            is IntListSetting -> setting.hint
        },
    )

    private fun parse(setting: GameSetting<*>, value: JsonElement): Any = when (setting) {
        is IntSetting -> {
            val number = (value as? JsonPrimitive)?.intOrNull ?: error("Must be a whole number")
            setting.problem(number)?.let(::error)
            number
        }
        is IntListSetting -> {
            val list = (value as? JsonArray)?.map { it.jsonPrimitive.int } ?: error("Must be a list of whole numbers, like [50, 30, 20]")
            setting.problem(list)?.let(::error)
            list
        }
    }

    private fun text(value: Any): String = when (value) {
        is List<*> -> value.joinToString(prefix = "[", postfix = "]")
        else -> value.toString()
    }

    private companion object {
        val log = LoggerFactory.getLogger(SettingsService::class.java)
        const val AUDIT_SETTING = "SETTING"
    }
}

/** A setting on the admin page; values as JSON text ("10", "[50, 30, 20]"). */
@Serializable
data class SettingView(
    val key: String,
    val group: String,
    val label: String,
    val value: String,
    val default: String,
    val hint: String,
)
