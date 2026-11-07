package com.kglabs28.yapnplay.backend.games.cyberblast

import com.kglabs28.yapnplay.backend.games.base.Game
import com.kglabs28.yapnplay.backend.games.base.identity.GameId
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.match.GameState
import com.kglabs28.yapnplay.backend.games.base.match.MatchOutcome
import com.kglabs28.yapnplay.backend.games.base.match.RankedSeat
import com.kglabs28.yapnplay.backend.games.base.match.SeatStatus
import com.kglabs28.yapnplay.backend.games.base.match.Viewer
import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.ApplyResult
import com.kglabs28.yapnplay.backend.games.base.play.GameBot
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.base.play.GameView
import com.kglabs28.yapnplay.backend.games.base.play.TypedRejection
import com.kglabs28.yapnplay.backend.games.base.rules.GameRules
import com.kglabs28.yapnplay.backend.games.base.rules.GameShowcase
import com.kglabs28.yapnplay.backend.games.base.rules.MatchmakingConfig
import com.kglabs28.yapnplay.backend.games.base.rules.TimingConfig
import com.kglabs28.yapnplay.backend.games.base.rules.TurnPolicy

/**
 * Cyber Blast: everyone on one board at once, dropping bombs to break
 * blockers and catch each other. It runs in real time on its own clock
 * ([BlastAdvance], sent by the session every frame); steering and bombs
 * come in at any moment. Pure: time and randomness come in from outside.
 * The CPU players live in the session ([com.kglabs28.yapnplay.backend.games.cyberblast.bot.BlastBot]),
 * since they act every frame, not on turns.
 */
class CyberBlastEngine : Game {
    override val id = GameId(CYBER_BLAST_ID)
    override val displayName = CYBER_BLAST_DISPLAY_NAME
    override val timing = TimingConfig(turnSeconds = TURN_SECONDS)
    override val matchmaking = MatchmakingConfig(seats = CYBER_BLAST_QUICK_PLAY_SEATS, searchSeconds = CYBER_BLAST_SEARCH_SECONDS)
    override val showcase = GameShowcase(taglines = CYBER_BLAST_TAGLINES, artKey = CYBER_BLAST_ID)
    override val houseRuleOptions = CYBER_BLAST_HOUSE_RULES
    override val fixedRules = CYBER_BLAST_FIXED_RULES
    override val minPlayers = CYBER_BLAST_MIN_PLAYERS
    override val maxPlayers = CYBER_BLAST_MAX_PLAYERS
    override val maxSpectators: Int? = null
    override val turnPolicy: TurnPolicy = TurnPolicy.Simultaneous
    override val bot: GameBot = object : GameBot {
        override fun chooseAction(state: GameState, seat: SeatNumber): Action? = null
    }

    override fun defaultRules(): GameRules = CyberBlastRules()

    override fun rulesSummary(): String = CYBER_BLAST_FIXED_RULES.joinToString(". ")

    override fun start(rules: GameRules, seatCount: Int, random: GameRandom): GameState {
        require(seatCount in 1..CYBER_BLAST_MAX_PLAYERS) { "Cyber Blast needs 1-8 seats" }
        val blastRules = rules as? CyberBlastRules ?: CyberBlastRules()
        val m = blastRules.mandatory
        val spawns = BlastSpawns.startTiles(m.cols, m.rows, seatCount, random)
        val crates = BlastLayout.crates(m.cols, m.rows, m.crateDensityPct, random) - spawns.toSet()
        val players = spawns.mapIndexed { i, tile ->
            BlastPlayer(seat = i + 1, x = BlastGrid.centreX(tile), y = BlastGrid.centreY(tile), lives = m.lives, radius = m.startRadius)
        }
        var state = CyberBlastState(gameId = id, blastRules = blastRules, seatCount = seatCount, players = players, crates = crates, cols = m.cols, rows = m.rows)
        // Everyone can get away from their first bomb: a way out of their own reach is opened.
        spawns.forEach { tile -> if (!BlastSpawns.hasEscape(state, tile, m.startRadius, emptyMap())) state = BlastSpawns.openEscape(state, tile, m.startRadius) }
        return state
    }

    override fun apply(state: GameState, seat: SeatNumber, action: Action, random: GameRandom): ApplyResult {
        val s = state as CyberBlastState
        if (s.phase == BlastPhase.FINISHED) return reject("MATCH_ENDED", "Match ended")
        return when (action) {
            is BlastAdvance -> BlastSimulation.advance(s, action.elapsedMs, random).let { (next, events) -> ApplyResult.Success(next, events) }
            is BlastSteer -> {
                val p = s.player(seat.value)?.takeIf { it.inPlay } ?: return reject("ILLEGAL_ACTION", "Not on the board")
                ApplyResult.Success(s.copy(players = s.players.map { if (it.seat == p.seat) it.copy(held = action.dir) else it }), emptyList())
            }
            is BlastDropBomb -> dropBomb(s, seat.value, action.at)
            BlastEnd -> ApplyResult.Success(s.copy(phase = BlastPhase.FINISHED), listOf(BlastFinished))
            else -> reject("BAD_REQUEST", "Unknown action for Cyber Blast")
        }
    }

    /** A bomb where [seat] stands: only while playing, on a tile with no bomb yet, and (when the match has a limit) with a bomb left. */
    private fun dropBomb(s: CyberBlastState, seat: Int, at: Int?): ApplyResult {
        val p = s.player(seat)?.takeIf { it.inPlay } ?: return reject("ILLEGAL_ACTION", "Not on the board")
        // Where their screen shows them, when that is right beside where the server has them.
        val tile = at?.takeIf { BlastGrid.distance(it, p.tile) <= SEEN_TILE_REACH && BlastGrid.inside(s, BlastGrid.col(it), BlastGrid.row(it)) } ?: p.tile
        if (s.phase != BlastPhase.PLAYING || (s.mandatory.bombs > 0 && s.bombsOf(seat) >= s.mandatory.bombs) || !BlastGrid.isOpen(s, tile)) {
            return reject("ILLEGAL_ACTION", "Can't drop a bomb now")
        }
        val bomb = BlastBomb(s.nextBombId, tile, seat, p.radius, s.time + s.mandatory.fuseMs)
        return ApplyResult.Success(s.copy(bombs = s.bombs + bomb, nextBombId = s.nextBombId + 1), emptyList())
    }

    override fun currentActor(state: GameState) = (state as CyberBlastState).turn.seat

    override fun autoplay(state: GameState, seat: SeatNumber, random: GameRandom): ApplyResult =
        reject("ILLEGAL_ACTION", "Cyber Blast bots play every frame")

    /** A player left: off the board at once (their bombs still go off); under two left, it's over. */
    override fun removeSeat(state: GameState, seat: SeatNumber): GameState {
        val s = state as CyberBlastState
        if (s.phase == BlastPhase.FINISHED || seat.value in s.removed) return s
        val wasIn = s.player(seat.value)?.inPlay == true
        val left = s.copy(
            removed = s.removed + seat.value,
            players = s.players.map { if (it.seat == seat.value) it.copy(removed = true, held = null) else it },
            outOrder = if (wasIn) s.outOrder + seat.value else s.outOrder,
        )
        return BlastSimulation.endIfDecided(left)
    }

    override fun viewFor(state: GameState, viewer: Viewer): GameView = object : GameView {}

    override fun isFinished(state: GameState): Boolean = (state as CyberBlastState).phase == BlastPhase.FINISHED

    override fun outcome(state: GameState): MatchOutcome {
        val s = state as CyberBlastState
        return MatchOutcome(ranking(s).mapIndexed { i, seat ->
            RankedSeat(SeatNumber(seat), if (seat in s.removed) SeatStatus.REMOVED else SeatStatus.FINISHED, i + 1)
        })
    }

    private fun reject(code: String, message: String) = ApplyResult.Rejected(TypedRejection(code, message))

    companion object {
        /**
         * The last player with hearts left first (when the match is cut short, everyone still
         * in, by points), then the rest by points (the later one out first on a tie), players who left last.
         */
        fun ranking(s: CyberBlastState): List<Int> {
            val outAt = s.outOrder.withIndex().associate { (i, seat) -> seat to i }
            return s.players.sortedWith(
                compareBy<BlastPlayer>({ it.removed }, { !it.inPlay }, { -it.score }, { -(outAt[it.seat] ?: Int.MAX_VALUE) }, { it.seat }),
            ).map { it.seat }
        }

        private const val TURN_SECONDS = 60L
        private const val PERCENT = 100
        /** How far (tiles) the tile a player sees themselves on may be from the server's for a bomb to go there. */
        private const val SEEN_TILE_REACH = 1
    }
}
