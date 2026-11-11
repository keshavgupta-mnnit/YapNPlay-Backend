package com.kglabs28.yapnplay.backend.application.match.games.cyberblast

import com.kglabs28.yapnplay.backend.application.match.games.GameSession
import com.kglabs28.yapnplay.backend.application.match.games.GameSessionFactory
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.domain.settings.GameSettings
import com.kglabs28.yapnplay.backend.domain.settings.IntSetting
import com.kglabs28.yapnplay.backend.domain.settings.Settings
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.contract.cyberblast.CyberClientTuning
import com.kglabs28.yapnplay.backend.games.cyberblast.CyberBlastEngine
import com.kglabs28.yapnplay.backend.games.cyberblast.BLAST_BIG_ARENA_KEY
import com.kglabs28.yapnplay.backend.games.cyberblast.BLAST_BIG_COLS
import com.kglabs28.yapnplay.backend.games.cyberblast.BLAST_BIG_ROWS
import com.kglabs28.yapnplay.backend.games.cyberblast.BLAST_COLS
import com.kglabs28.yapnplay.backend.games.cyberblast.BLAST_FEWER_BLOCKERS_KEY
import com.kglabs28.yapnplay.backend.games.cyberblast.BLAST_MINI_MAP_KEY
import com.kglabs28.yapnplay.backend.games.cyberblast.BLAST_MORE_BLOCKERS_KEY
import com.kglabs28.yapnplay.backend.games.cyberblast.BLAST_ROWS
import com.kglabs28.yapnplay.backend.games.cyberblast.BLAST_STANDARD_PLAYERS
import com.kglabs28.yapnplay.backend.games.cyberblast.CyberBlastHouseRules
import com.kglabs28.yapnplay.backend.games.cyberblast.CyberBlastMandatoryRules
import com.kglabs28.yapnplay.backend.games.cyberblast.CyberBlastRules
import com.kglabs28.yapnplay.backend.games.cyberblast.CyberBlastState
import com.kglabs28.yapnplay.backend.games.cyberblast.bot.BlastBotSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Starts Cyber Blast sessions: every rule and the bots' weights from the admin settings, the board and its blockers from the room's toggles. */
class CyberBlastSessionFactory(
    override val game: CyberBlastEngine,
    private val random: GameRandom,
    private val json: Json,
    private val settings: Settings = Settings.Defaults,
) : GameSessionFactory {

    /** A room's toggles pick the board and how packed it is; Quick Play ([houseRules] null) has them all off. */
    override fun start(seatCount: Int, houseRules: Map<String, Boolean>?): GameSession {
        val house = CyberBlastHouseRules(
            bigArena = houseRules?.get(BLAST_BIG_ARENA_KEY) == true,
            miniMap = houseRules?.get(BLAST_MINI_MAP_KEY) == true,
            fewerBlockers = houseRules?.get(BLAST_FEWER_BLOCKERS_KEY) == true,
            moreBlockers = houseRules?.get(BLAST_MORE_BLOCKERS_KEY) == true,
        )
        return session(game.start(CyberBlastRules(mandatory(house, seatCount), house), seatCount, random) as CyberBlastState)
    }

    /** The room's amount of blockers (both toggles on cancel out), a few less for each player over 4 and more for each under. */
    private fun density(house: CyberBlastHouseRules, seatCount: Int): Int {
        val base = when {
            house.fewerBlockers && !house.moreBlockers -> settings[GameSettings.BLAST_DENSITY_FEW_PCT]
            house.moreBlockers && !house.fewerBlockers -> settings[GameSettings.BLAST_DENSITY_MANY_PCT]
            else -> settings[GameSettings.BLAST_CRATE_DENSITY_PCT]
        }
        val shift = (seatCount - BLAST_STANDARD_PLAYERS) * settings[GameSettings.BLAST_DENSITY_PER_PLAYER_PCT]
        return (base * (PERCENT - shift) / PERCENT).coerceIn(0, MAX_DENSITY_PCT)
    }

    override fun restore(saved: JsonObject): GameSession = session(json.decodeFromJsonElement(CyberBlastSaved.serializer(), saved).engine)

    private fun session(state: CyberBlastState) =
        CyberBlastSession(game, state, random, json, settings[GameSettings.BLAST_FRAME_MS].toLong(), ::botSettings, ::tuning)

    private fun mandatory(house: CyberBlastHouseRules, seatCount: Int) = CyberBlastMandatoryRules(
        lives = settings[GameSettings.BLAST_LIVES],
        startRadius = settings[GameSettings.BLAST_START_RADIUS],
        maxRadius = maxOf(settings[GameSettings.BLAST_MAX_RADIUS], settings[GameSettings.BLAST_START_RADIUS]),
        radiusPerHit = settings[GameSettings.BLAST_RADIUS_PER_HIT],
        bombs = settings[GameSettings.BLAST_BOMBS],
        fuseMs = ms(GameSettings.BLAST_FUSE_MS),
        flameMs = ms(GameSettings.BLAST_FLAME_MS),
        speed = settings[GameSettings.BLAST_SPEED],
        boostSpeed = settings[GameSettings.BLAST_BOOST_SPEED],
        boostMs = seconds(GameSettings.BLAST_BOOST_SECONDS),
        shieldMs = ms(GameSettings.BLAST_SHIELD_MS),
        hitPoints = settings[GameSettings.BLAST_HIT_POINTS],
        hitPenalty = settings[GameSettings.BLAST_HIT_PENALTY],
        selfPenalty = settings[GameSettings.BLAST_SELF_PENALTY],
        cratePoints = settings[GameSettings.BLAST_CRATE_POINTS],
        crateDensityPct = density(house, seatCount),
        itemEveryMs = seconds(GameSettings.BLAST_ITEM_EVERY_SECONDS),
        itemJitterMs = seconds(GameSettings.BLAST_ITEM_JITTER_SECONDS),
        itemLifeMs = seconds(GameSettings.BLAST_ITEM_LIFE_SECONDS),
        maxItems = settings[GameSettings.BLAST_MAX_ITEMS],
        countdownMs = seconds(GameSettings.BLAST_COUNTDOWN_SECONDS),
        closeAfterMs = seconds(if (house.bigArena) GameSettings.BLAST_BIG_CLOSE_AFTER_SECONDS else GameSettings.BLAST_CLOSE_AFTER_SECONDS),
        cols = if (house.bigArena) BLAST_BIG_COLS else BLAST_COLS,
        rows = if (house.bigArena) BLAST_BIG_ROWS else BLAST_ROWS,
        closeStepMs = ms(GameSettings.BLAST_CLOSE_STEP_MS),
        closeWhenCratesLeftPct = settings[GameSettings.BLAST_CLOSE_WHEN_CLEARED_PCT],
        closeWhenPlayersLeft = settings[GameSettings.BLAST_CLOSE_WHEN_PLAYERS_LEFT],
    )

    /** Read on every bot look, so a change on the admin page applies at once. */
    private fun botSettings() = BlastBotSettings(
        reactMs = ms(GameSettings.BLAST_BOT_REACT_MS),
        reactJitterMs = ms(GameSettings.BLAST_BOT_REACT_JITTER_MS),
        bombGapMs = ms(GameSettings.BLAST_BOT_BOMB_GAP_MS),
        bombGapJitterMs = ms(GameSettings.BLAST_BOT_BOMB_GAP_JITTER_MS),
        lifeValue = settings[GameSettings.BLAST_BOT_LIFE_VALUE].toDouble(),
        stepCost = settings[GameSettings.BLAST_BOT_STEP_COST].toDouble(),
        hitChancePct = settings[GameSettings.BLAST_BOT_HIT_CHANCE_PCT],
        cornerBonusPct = settings[GameSettings.BLAST_BOT_CORNER_BONUS_PCT],
        boostValue = settings[GameSettings.BLAST_BOT_BOOST_VALUE].toDouble(),
        huntValue = settings[GameSettings.BLAST_BOT_HUNT_VALUE].toDouble(),
        threatPct = settings[GameSettings.BLAST_BOT_THREAT_PCT],
        plannedBombPct = settings[GameSettings.BLAST_BOT_PLANNED_BOMB_PCT],
        standingMinPct = settings[GameSettings.BOT_STANDING_MIN_PCT],
        standingMaxPct = settings[GameSettings.BOT_STANDING_MAX_PCT],
    )

    /** How much of a Big Arena everyone sees, from the admin settings. */
    private fun tuning() = CyberClientTuning(
        viewCols = settings[GameSettings.BLAST_APP_VIEW_COLS],
        viewRows = settings[GameSettings.BLAST_APP_VIEW_ROWS],
    )

    private fun ms(setting: IntSetting): Long = settings[setting].toLong()

    private fun seconds(setting: IntSetting): Long = settings[setting] * BackendConstants.MILLIS_PER_SECOND

    private companion object {
        const val PERCENT = 100
        const val MAX_DENSITY_PCT = 90
    }
}
