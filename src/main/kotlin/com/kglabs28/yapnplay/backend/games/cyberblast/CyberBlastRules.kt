package com.kglabs28.yapnplay.backend.games.cyberblast

import com.kglabs28.yapnplay.backend.games.base.rules.GameRules
import com.kglabs28.yapnplay.backend.games.base.rules.HouseRules
import com.kglabs28.yapnplay.backend.games.base.rules.MandatoryRules
import com.kglabs28.yapnplay.backend.games.base.rules.PlayMode
import kotlinx.serialization.Serializable

/**
 * Everything about a Cyber Blast match, fixed when it starts (admin
 * settings). Speeds are thousandths of a tile per second.
 */
@Serializable
data class CyberBlastMandatoryRules(
    val lives: Int = 5,
    val startRadius: Int = 1,
    val maxRadius: Int = 7,
    /** Tiles a player's blast grows by for each player they hit in a row (back to [startRadius] when they lose a heart). */
    val radiusPerHit: Int = 1,
    /** Bombs one player may have on the board at once; 0: no limit. */
    val bombs: Int = 0,
    val fuseMs: Long = 3_000,
    val flameMs: Long = 500,
    val speed: Int = 4_000,
    val boostSpeed: Int = 5_600,
    val boostMs: Long = 30_000,
    /** After coming back from a hit: can't be hurt for this long. */
    val shieldMs: Long = 2_000,
    val hitPoints: Int = 100,
    val hitPenalty: Int = 25,
    val selfPenalty: Int = 15,
    val cratePoints: Int = 10,
    /** Of the free tiles (away from where players start), this % get a breakable blocker. */
    val crateDensityPct: Int = 60,
    /** A speed boot shows up every [itemEveryMs] plus up to [itemJitterMs]; it lies there [itemLifeMs]; at most [maxItems] at once. */
    val itemEveryMs: Long = 12_000,
    val itemJitterMs: Long = 8_000,
    val itemLifeMs: Long = 12_000,
    val maxItems: Int = 2,
    /** "3, 2, 1, Go" before anyone may move. */
    val countdownMs: Long = 3_000,
    /** The arena starts closing in after this long (0: never), one tile every [closeStepMs]. */
    val closeAfterMs: Long = 180_000,
    val closeStepMs: Long = 600,
    /** The board's size (a Big Arena is about twice as big each way). */
    val cols: Int = BLAST_COLS,
    val rows: Int = BLAST_ROWS,
) : MandatoryRules

/** A room's toggles (Quick Play: all off). Blocker density and the board's size go into the mandatory rules when the match starts. */
@Serializable
data class CyberBlastHouseRules(
    val bigArena: Boolean = false,
    val miniMap: Boolean = false,
    val fewerBlockers: Boolean = false,
    val moreBlockers: Boolean = false,
) : HouseRules

@Serializable
data class CyberBlastRules(
    override val mandatory: CyberBlastMandatoryRules = CyberBlastMandatoryRules(),
    override val house: CyberBlastHouseRules = CyberBlastHouseRules(),
) : GameRules {
    override fun isAllowed(mode: PlayMode): Boolean = true
}
