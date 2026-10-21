package com.kglabs28.yapnplay.backend.games.citybazaar.engine

import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CityBazaarBoard
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.START_BONUS
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.TILE_COUNT
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.TileKind

/**
 * How a person sizes up the board: what their cities should still earn,
 * what the next two rolls are likely to cost, and the chance of running out
 * of cash. Pure functions over the state; the bot decides with them.
 */
object CityBazaarOutlook {
    private const val MIN_ROUNDS = 3
    private const val MAX_ROUNDS = 30
    private const val HOT_MIN = 0.6
    private const val HOT_MAX = 1.8
    private const val DICE_WAYS = 36.0
    /** Average two-dice roll: a player lands on a given square about once in 7 turns. */
    private const val AVERAGE_ROLL = 7.0
    private const val PERCENT = 100.0

    /** Ways to roll each total with two dice (2..12). */
    private val WAYS = mapOf(2 to 1, 3 to 2, 4 to 3, 5 to 4, 6 to 5, 7 to 6, 8 to 5, 9 to 4, 10 to 3, 11 to 2, 12 to 1)

    fun p(total: Int): Double = (WAYS[total] ?: 0) / DICE_WAYS

    /** Rounds still to play: to the round limit, else [defaultRounds]; between 3 and 30. */
    fun roundsLeft(s: CityBazaarState, defaultRounds: Int): Int {
        val left = s.cbRules.house.roundLimit?.let { it - s.round } ?: defaultRounds
        return left.coerceIn(MIN_ROUNDS, MAX_ROUNDS)
    }

    /**
     * Rent [seat]'s cities should still bring in: each city's rent now (with
     * its colour double or company pair) × opponents × rounds left / 7 (the
     * chance a player lands on a given square in a turn), weighted by how
     * well it has earned so far compared with the seat's other cities
     * (0.6–1.8).
     */
    fun future(s: CityBazaarState, seat: Int, defaultRounds: Int): Double {
        val mine = s.owners.filter { it.value == seat }.keys
        if (mine.isEmpty()) return 0.0
        val opponents = (s.active.size - 1).coerceAtLeast(0)
        val rounds = roundsLeft(s, defaultRounds)
        val avgEarned = mine.map { (s.rentByTile[it] ?: 0).toDouble() }.average()
        return mine.sumOf { tile ->
            val rent = CityBazaarEconomy.rent(s, tile).toDouble()
            val hot = (1 + ((s.rentByTile[tile] ?: 0) - avgEarned) / (avgEarned + rent + 1)).coerceIn(HOT_MIN, HOT_MAX)
            rent * opponents * rounds / AVERAGE_ROLL * hot
        }
    }

    /**
     * What landing on [tile] costs [seat] (negative: it pays them): a
     * rival's rent, a tax bill, Club (pay every other player) or Rest House
     * (every other player pays you) when those rules are on.
     */
    fun owedAt(s: CityBazaarState, seat: Int, tile: Int): Int {
        val spec = CityBazaarBoard.tile(tile)
        val owner = s.owners[tile]
        val others = s.active.count { it != seat }
        val house = s.cbRules.house
        return when {
            owner != null && owner != seat && owner in s.active -> CityBazaarEconomy.rent(s, tile)
            spec.kind == TileKind.TAX ->
                spec.taxPerProperty * CityBazaarEconomy.properties(s, seat) + spec.taxPerBuilding * CityBazaarEconomy.buildings(s, seat)
            spec.kind == TileKind.CLUB && house.clubFee -> house.clubRestEach * others
            spec.kind == TileKind.REST_HOUSE && house.restHouseSkips -> -house.clubRestEach * others
            else -> 0
        }
    }

    /** The rolls and what they cost, over the next two turns: (chance, cost after the first roll, cost after both), START bonus credited. */
    private fun twoTurns(s: CityBazaarState, seat: Int): List<Triple<Double, Int, Int>> {
        val pos = s.positions[seat] ?: 0
        return WAYS.keys.flatMap { d1 ->
            val first = owedAt(s, seat, (pos + d1) % TILE_COUNT) - if (pos + d1 >= TILE_COUNT) START_BONUS else 0
            WAYS.keys.map { d2 ->
                val passes = pos + d1 < TILE_COUNT && pos + d1 + d2 >= TILE_COUNT
                val second = owedAt(s, seat, (pos + d1 + d2) % TILE_COUNT) - if (passes) START_BONUS else 0
                Triple(p(d1) * p(d2), first, first + second)
            }
        }
    }

    /** Expected rent, tax, Club and Rest House over the next roll (negative: income). */
    fun expectedOwedNextRoll(s: CityBazaarState, seat: Int): Double {
        val pos = s.positions[seat] ?: 0
        return WAYS.keys.sumOf { d -> p(d) * owedAt(s, seat, (pos + d) % TILE_COUNT) }
    }

    /** Chance [seat] runs out of cash (below 0) in the next two turns, holding [cash]. */
    fun ruinChance(s: CityBazaarState, seat: Int, cash: Int): Double =
        twoTurns(s, seat).sumOf { (chance, first, both) -> if (cash - first < 0 || cash - both < 0) chance else 0.0 }

    /**
     * The cash that keeps the chance of running out at or below
     * [ruinTargetPct] %, plus [buffer]; never less than [reserve].
     */
    fun keepCash(s: CityBazaarState, seat: Int, reserve: Int, buffer: Int, ruinTargetPct: Int): Int {
        val needs = twoTurns(s, seat).map { (chance, first, both) -> maxOf(first, both, 0) to chance }.sortedByDescending { it.first }
        var tail = 0.0
        var cash = 0
        for ((need, chance) in needs) {
            if (tail + chance > ruinTargetPct / PERCENT) {
                cash = need
                break
            }
            tail += chance
        }
        return maxOf(reserve, cash + buffer)
    }

    /** Chance a seat at [from] lands on [tile] within its next two rolls. */
    fun landsWithinTwo(from: Int, tile: Int): Double {
        val distance = (tile - from + TILE_COUNT) % TILE_COUNT
        val once = p(distance)
        val twice = WAYS.keys.sumOf { d1 -> p(d1) * p(distance - d1) }
        return (once + twice).coerceAtMost(1.0)
    }
}
