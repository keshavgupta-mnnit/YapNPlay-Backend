package com.kglabs28.yapnplay.backend.games.cyberblast.bot

/**
 * How the Cyber Blast bots play (admin settings). Times are ms of match
 * time; values are in points, like the game's own scoring.
 */
data class BlastBotSettings(
    /** A person's reaction: a new look at the board every [reactMs] plus up to [reactJitterMs]. */
    val reactMs: Long = 180,
    val reactJitterMs: Long = 220,
    /** Least time between two of its bombs, plus up to [bombGapJitterMs]. */
    val bombGapMs: Long = 1_200,
    val bombGapJitterMs: Long = 1_300,
    /** What losing a heart is worth (the size of the loss in every risk). */
    val lifeValue: Double = 300.0,
    /** What one step of walking costs. */
    val stepCost: Double = 4.0,
    /** Chance a player in a bomb's line gets caught, and how much more when they have little room to run (%). */
    val hitChancePct: Int = 30,
    val cornerBonusPct: Int = 40,
    /** Worth of the speed boots. */
    val boostValue: Double = 60.0,
    /** Worth of each step closer to the nearest player when nothing else is near. */
    val huntValue: Double = 3.0,
    /** Chance (%) of being bombed for each player who has the tile in their line. */
    val threatPct: Int = 12,
    /** A spot it walks to is worth this % of the bomb it could drop there. */
    val plannedBombPct: Int = 70,
    /** Risk weight range: far behind the leader (bold) to leading (careful), %. */
    val standingMinPct: Int = 70,
    val standingMaxPct: Int = 130,
)
