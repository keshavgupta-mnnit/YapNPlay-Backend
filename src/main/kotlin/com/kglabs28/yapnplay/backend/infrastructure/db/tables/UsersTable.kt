package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import com.kglabs28.yapnplay.backend.config.BackendConstants
import org.jetbrains.exposed.sql.Table

/** Mirrors V1__create_users.sql. */
object UsersTable : Table("users") {
    val id = uuid("id")
    val displayName = varchar("display_name", BackendConstants.DISPLAY_NAME_MAX_LENGTH)
    val secretHash = char("secret_hash", SHA256_HEX_LENGTH)
    val createdAt = long("created_at")
    val xp = integer("xp")
    val username = varchar("username", USERNAME_MAX_LENGTH)
    val userCode = char("user_code", USER_CODE_LENGTH).nullable()
    val avatarId = varchar("avatar_id", AVATAR_ID_MAX_LENGTH)
    /** V10: the linked Google Play Games player (unique), or null. */
    val playGamesPlayerId = varchar("play_games_player_id", PLAY_GAMES_ID_MAX_LENGTH).nullable()
    /** V11: coins and the daily / ad reward counters (days in India time since 1970). */
    val coins = integer("coins")
    val dailyBonusDay = integer("daily_bonus_day")
    val adRewardDay = integer("ad_reward_day")
    val adRewardsToday = integer("ad_rewards_today")
    /** V12: completion coins paid today, for the daily cap. */
    val completionDay = integer("completion_day")
    val completionsToday = integer("completions_today")
    /** V13: when the last rewarded ad paid (epoch millis). */
    val lastAdAt = long("last_ad_at")

    override val primaryKey = PrimaryKey(id)

    private const val SHA256_HEX_LENGTH = 64
    private const val USERNAME_MAX_LENGTH = 20
    private const val USER_CODE_LENGTH = 6
    private const val AVATAR_ID_MAX_LENGTH = 8
    private const val PLAY_GAMES_ID_MAX_LENGTH = 128
}
