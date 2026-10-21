package com.kglabs28.yapnplay.backend.arena

import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.ApplyResult
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarBot
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarBotProfile
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarBotTuning
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarEconomy
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarEngine
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarHouseRules
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarPhase
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarRules
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarState
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.CloseAuction
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.EndMatch
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.PlaceBid
import com.kglabs28.yapnplay.backend.games.royalludo.engine.Phase
import com.kglabs28.yapnplay.backend.games.royalludo.engine.RoyalLudoBot
import com.kglabs28.yapnplay.backend.games.royalludo.engine.RoyalLudoBotTuning
import com.kglabs28.yapnplay.backend.games.royalludo.engine.RoyalLudoEngine
import com.kglabs28.yapnplay.backend.games.royalludo.engine.RoyalLudoRules
import com.kglabs28.yapnplay.backend.games.royalludo.engine.RoyalLudoState
import kotlin.random.Random

/**
 * Offline bot arena (never part of the server): many seeded bot-vs-bot
 * games, setting set A in odd seats against set B in even seats, with win
 * rates and averages, to pick the default bot settings.
 *
 * Run: ./gradlew arena -Parena="game=citybazaar games=200 B.ruinWeightPct=150"
 * (game=ludo for Royal Ludo; B.<field>=<number> changes one tuning field of set B).
 */
fun main(args: Array<String>) {
    val options = args.flatMap { it.split(' ') }.filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
    val games = options["games"]?.toInt() ?: DEFAULT_GAMES
    val variant = options.filterKeys { it.startsWith("B.") }.mapKeys { it.key.removePrefix("B.") }.mapValues { it.value.toInt() }
    val report = when (options["game"] ?: "citybazaar") {
        "ludo" -> ludoArena(games, ludoTuning(variant))
        else -> cityBazaarArena(games, cityBazaarTuning(variant))
    }
    println(report)
}

private const val DEFAULT_GAMES = 100
private const val CITY_BAZAAR_SEATS = 4
private const val ROUND_LIMIT = 20
private const val MAX_STEPS = 50_000

private class SeededRandom(seed: Long) : GameRandom {
    private val random = Random(seed)
    override fun nextInt(bound: Int): Int = random.nextInt(bound)
}

/** What a run of games showed for each setting set. */
data class ArenaReport(val game: String, val games: Int, val winsA: Int, val winsB: Int, val avgScoreA: Double, val avgScoreB: Double, val avgLength: Double) {
    override fun toString() = "$game, $games games: A won $winsA, B won $winsB; average score A ${avgScoreA.toInt()}, B ${avgScoreB.toInt()}; average length ${"%.1f".format(avgLength)}"
}

fun cityBazaarArena(games: Int, tuningB: CityBazaarBotTuning, tuningA: CityBazaarBotTuning = CityBazaarBotTuning()): ArenaReport {
    val engine = CityBazaarEngine()
    val botA = CityBazaarBot(CityBazaarBotProfile.CPU.copy(tuning = tuningA))
    val botB = CityBazaarBot(CityBazaarBotProfile.CPU.copy(tuning = tuningB))
    var winsA = 0
    var winsB = 0
    var scoreA = 0.0
    var scoreB = 0.0
    var length = 0.0
    repeat(games) { game ->
        val random = SeededRandom(game.toLong())
        val rules = CityBazaarRules.STANDARD.copy(house = CityBazaarHouseRules(roundLimit = ROUND_LIMIT, timeLimitMinutes = null))
        var s = engine.start(rules, CITY_BAZAAR_SEATS, random) as CityBazaarState
        fun bot(seat: Int) = if (seat % 2 == 1) botA else botB
        fun apply(seat: Int, action: Action) {
            val result = engine.apply(s, SeatNumber(seat), action, random)
            s = if (result is ApplyResult.Success) result.state as CityBazaarState else s.also { error("Arena: $action refused for seat $seat") }
        }
        var steps = 0
        while (s.phase != CityBazaarPhase.FINISHED && steps++ < MAX_STEPS) {
            when (s.phase) {
                CityBazaarPhase.AUCTION -> {
                    val bidder = s.active.firstOrNull { bot(it).wantsToBid(s, it) }
                    if (bidder != null) apply(bidder, PlaceBid) else apply(s.currentSeat, CloseAuction)
                }
                CityBazaarPhase.EXTENSION_VOTE -> apply(s.currentSeat, EndMatch)
                else -> apply(s.currentSeat, bot(s.currentSeat).chooseAction(s, SeatNumber(s.currentSeat)) ?: error("Arena: no action"))
            }
        }
        val winner = CityBazaarEngine.ranking(s).first()
        if (winner % 2 == 1) winsA++ else winsB++
        (1..CITY_BAZAAR_SEATS).forEach { seat ->
            val worth = CityBazaarEconomy.netWorth(s, seat).toDouble()
            if (seat % 2 == 1) scoreA += worth else scoreB += worth
        }
        length += s.round
    }
    val perSet = games * CITY_BAZAAR_SEATS / 2.0
    return ArenaReport("City Bazaar (net worth, rounds)", games, winsA, winsB, scoreA / perSet, scoreB / perSet, length / games)
}

fun ludoArena(games: Int, tuningB: RoyalLudoBotTuning, tuningA: RoyalLudoBotTuning = RoyalLudoBotTuning()): ArenaReport {
    val engine = RoyalLudoEngine()
    val botA = RoyalLudoBot(tuningA)
    val botB = RoyalLudoBot(tuningB)
    var winsA = 0
    var winsB = 0
    var scoreA = 0.0
    var scoreB = 0.0
    var length = 0.0
    repeat(games) { game ->
        val random = SeededRandom(game.toLong())
        var s = engine.start(RoyalLudoRules.TRADITIONAL, 4, random) as RoyalLudoState
        var steps = 0
        while (s.phase != Phase.FINISHED && steps++ < MAX_STEPS) {
            val bot = if (s.currentSeat % 2 == 1) botA else botB
            val result = engine.autoplayWith(bot, s, SeatNumber(s.currentSeat), random)
            s = (result as? ApplyResult.Success)?.state as? RoyalLudoState ?: error("Arena: Royal Ludo move refused")
        }
        val winner = s.finishingOrder.firstOrNull() ?: return@repeat
        if (winner % 2 == 1) winsA++ else winsB++
        s.seats.map { it.value }.forEach { seat ->
            val progress = s.piecesForSeat(seat).sumOf { it.coerceAtLeast(0) }.toDouble()
            if (seat % 2 == 1) scoreA += progress else scoreB += progress
        }
        length += steps
    }
    return ArenaReport("Royal Ludo (progress, steps)", games, winsA, winsB, scoreA / (games * 2), scoreB / (games * 2), length / games)
}

private fun cityBazaarTuning(changes: Map<String, Int>): CityBazaarBotTuning = changes.entries.fold(CityBazaarBotTuning()) { t, (key, value) ->
    when (key) {
        "ruinWeightPct" -> t.copy(ruinWeightPct = value)
        "denialWeightPct" -> t.copy(denialWeightPct = value)
        "ruinTargetPct" -> t.copy(ruinTargetPct = value)
        "pressureValue" -> t.copy(pressureValue = value)
        "comfortPct" -> t.copy(comfortPct = value)
        "jailPostponePct" -> t.copy(jailPostponePct = value)
        "standingMinPct" -> t.copy(standingMinPct = value)
        "standingMaxPct" -> t.copy(standingMaxPct = value)
        "defaultRounds" -> t.copy(defaultRounds = value)
        else -> error("Unknown City Bazaar tuning field $key")
    }
}

private fun ludoTuning(changes: Map<String, Int>): RoyalLudoBotTuning = changes.entries.fold(RoyalLudoBotTuning()) { t, (key, value) ->
    when (key) {
        "baseCost" -> t.copy(baseCost = value)
        "captureWeightPct" -> t.copy(captureWeightPct = value)
        "endgameCapturePct" -> t.copy(endgameCapturePct = value)
        "extraTurnBonus" -> t.copy(extraTurnBonus = value)
        "finishBonus" -> t.copy(finishBonus = value)
        "leaveBaseBonus" -> t.copy(leaveBaseBonus = value)
        "flexBonus" -> t.copy(flexBonus = value)
        "riskWeightPct" -> t.copy(riskWeightPct = value)
        "chaseBonus" -> t.copy(chaseBonus = value)
        "standingMinPct" -> t.copy(standingMinPct = value)
        "standingMaxPct" -> t.copy(standingMaxPct = value)
        else -> error("Unknown Royal Ludo tuning field $key")
    }
}
