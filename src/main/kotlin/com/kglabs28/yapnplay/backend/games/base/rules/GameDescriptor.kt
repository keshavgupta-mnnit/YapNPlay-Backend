package com.kglabs28.yapnplay.backend.games.base.rules

import com.kglabs28.yapnplay.backend.games.base.identity.GameId

/**
 * Base contract every game must follow. It describes the game's shape —
 * how many can play, how many can watch, how turns work, and which rules
 * apply — while the actual implementation stays game-specific.
 */
interface GameDescriptor {
    /** Stable identifier (e.g., "royal-ludo"). */
    val id: GameId

    /** Human-readable name (e.g., "Royal Ludo"). */
    val displayName: String

    /** Minimum players needed to start. */
    val minPlayers: Int

    /** Maximum players; extra ready members spectate. */
    val maxPlayers: Int

    /** Maximum spectators, or null for no limit. */
    val maxSpectators: Int?

    /** How turns work in this game. */
    val turnPolicy: TurnPolicy

    /** Timing values this game declares. */
    val timing: TimingConfig

    /** How Quick Play fills a public match (search time, CPU fill). */
    val matchmaking: MatchmakingConfig

    /** Taglines and art key shown in game pickers. */
    val showcase: GameShowcase

    /** CPU players may fill empty seats (a room's bot fill, Quick Play's CPU fill). Doodle King: only while testing. */
    val cpuFillAllowed: Boolean get() = true

    /**
     * How many seats a room match has for [players] humans when the room's CPU fill is on (CPUs take
     * the rest): a full table, as big as Quick Play's, never fewer than the game needs nor more
     * than it allows. Royal Ludo: its board's size (4, 6 or 8).
     */
    fun roomTableSize(players: Int): Int = maxOf(players, minPlayers, matchmaking.seats).coerceAtMost(maxPlayers)

    /**
     * A table of [seats] that needs an app newer than the game itself (Royal Ludo's 6- and 8-player
     * boards): the feature every player's app must report, or null.
     */
    fun featureNeeded(seats: Int): String? = null

    /** Toggles a room leader may change (shown in the lobby's house rules). */
    val houseRuleOptions: List<HouseRuleOption>

    /** The rules that never change, one line each (the lobby's rule sheet). */
    val fixedRules: List<String>

    /** The default rules for this game. */
    fun defaultRules(): GameRules

    /** Short human-readable summary of the default rules. */
    fun rulesSummary(): String
}

/**
 * How turns work in a game. The guideline is shared;
 * each game implements it in its own way.
 */
sealed interface TurnPolicy {
    /** One seat acts at a time (Royal Ludo). The engine reports the actor. */
    data object Sequential : TurnPolicy

    /** All seats act together within a deadline (future drawing games). */
    data object Simultaneous : TurnPolicy
}
