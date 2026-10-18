package com.kglabs28.yapnplay.backend.games.base.rules

/**
 * The fixed, non-negotiable part of a game's rules — its identity.
 * Every game defines its own mandatory set; rooms can never toggle these.
 */
interface MandatoryRules

/**
 * The toggleable part of a game's rules.
 * Every game defines its own house set; private rooms may override these.
 */
interface HouseRules

/**
 * Where a match is played. It decides whether house rules may change:
 * private rooms may override house rules, public matches lock everything.
 */
enum class PlayMode {
    PRIVATE_ROOM,
    PUBLIC_MATCH,
}

/**
 * Base rules contract every game must implement.
 * Each game splits its rules into [mandatory] (always on) and
 * [house] (toggleable in private rooms, locked in public matches).
 */
interface GameRules {
    val mandatory: MandatoryRules
    val house: HouseRules

    /**
     * Whether this rules config is allowed in [mode].
     * Private rooms accept anything; public matches accept only the
     * traditional set (mandatory AND house both fixed).
     */
    fun isAllowed(mode: PlayMode): Boolean
}
