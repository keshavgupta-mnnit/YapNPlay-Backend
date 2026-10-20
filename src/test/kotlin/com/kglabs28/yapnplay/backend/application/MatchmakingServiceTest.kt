package com.kglabs28.yapnplay.backend.application

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.economy.EntryFees
import com.kglabs28.yapnplay.backend.application.matchmaking.MatchmakingService
import com.kglabs28.yapnplay.backend.application.moderation.BlockChecker
import com.kglabs28.yapnplay.backend.application.rooms.GameCatalog
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.MatchFound
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.games.base.GameRegistry
import com.kglabs28.yapnplay.backend.games.royalludo.engine.RoyalLudoEngine
import com.kglabs28.yapnplay.backend.games.royalludo.utils.ROYAL_LUDO_ID
import com.kglabs28.yapnplay.backend.support.InMemoryModerationRepository
import com.kglabs28.yapnplay.backend.support.MatchTestKit
import com.kglabs28.yapnplay.backend.support.testJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Quick Play on virtual time: early start, CPU fill at the deadline, leaving, blocks. */
@OptIn(ExperimentalCoroutinesApi::class)
class MatchmakingServiceTest {
    private val moderation = InMemoryModerationRepository()

    private fun TestScope.setup(fees: EntryFees = EntryFees.Free): Pair<MatchTestKit, MatchmakingService> {
        val kit = MatchTestKit(this)
        val service = MatchmakingService(
            games = GameCatalog(GameRegistry(listOf(RoyalLudoEngine())), emptySet()),
            matches = kit.service,
            blocks = BlockChecker(moderation, kit.clock),
            notifier = kit.notifier,
            codec = kit.codec,
            clock = kit.clock,
            scope = backgroundScope,
            fees = fees,
        )
        return kit to service
    }

    private fun MatchTestKit.found(userId: String): MatchFound? =
        notifier.sent.lastOrNull { it.first == userId && it.second.type == MessageType.MATCH_FOUND }
            ?.let { testJson.decodeFromJsonElement(MatchFound.serializer(), it.second.payload) }

    @Test
    fun `four players start at once with no CPUs`() = runTest {
        val (kit, mm) = setup()
        listOf("a", "b", "c", "d").forEach { mm.join(it, ROYAL_LUDO_ID) }
        val found = kit.found("d")!!
        assertEquals(4, found.humanPlayers)
        assertEquals(0, found.cpuPlayers)
        assertEquals(found.matchId, kit.found("a")!!.matchId)
    }

    @Test
    fun `at the deadline CPUs fill the empty seats`() = runTest {
        val (kit, mm) = setup()
        mm.join("a", ROYAL_LUDO_ID)
        assertTrue(MessageType.QUEUE_STATUS in kit.notifier.typesFor("a"))
        advanceTimeBy(DEADLINE_MS)
        runCurrent()
        assertEquals(3, kit.found("a")!!.cpuPlayers)
    }

    @Test
    fun `a player who leaves is not matched`() = runTest {
        val (kit, mm) = setup()
        mm.join("a", ROYAL_LUDO_ID)
        mm.leave("a")
        advanceTimeBy(DEADLINE_MS)
        runCurrent()
        assertEquals(null, kit.found("a"))
    }

    @Test
    fun `queued twice, unknown game and blocked players are refused`() = runTest {
        val (_, mm) = setup()
        mm.join("a", ROYAL_LUDO_ID)
        assertEquals(ErrorCode.ALREADY_IN_QUEUE, assertFailsWith<ServiceException> { mm.join("a", ROYAL_LUDO_ID) }.code)
        assertEquals(ErrorCode.GAME_UNAVAILABLE, assertFailsWith<ServiceException> { mm.join("b", "chess") }.code)
        moderation.blocked += "c"
        assertEquals(ErrorCode.BLOCKED, assertFailsWith<ServiceException> { mm.join("c", ROYAL_LUDO_ID) }.code)
    }

    @Test
    fun `leagues - only matched within the same fee, need the coins, and pay at the start`() = runTest {
        val charged = mutableListOf<Pair<String, Int>>()
        val fees = object : EntryFees {
            override suspend fun canEnter(userId: String, entryCoins: Int) = userId != "broke" || entryCoins == 0
            override suspend fun charge(userIds: List<String>, matchId: String, entryCoins: Int): List<String> {
                charged += userIds.map { it to entryCoins }
                return userIds
            }
        }
        val (kit, mm) = setup(fees)
        assertEquals(ErrorCode.NOT_ENOUGH_COINS, assertFailsWith<ServiceException> { mm.join("broke", ROYAL_LUDO_ID, 500) }.code)
        assertEquals(ErrorCode.BAD_REQUEST, assertFailsWith<ServiceException> { mm.join("a", ROYAL_LUDO_ID, 123) }.code)
        mm.join("broke", ROYAL_LUDO_ID, 0)
        listOf("a", "b", "c").forEach { mm.join(it, ROYAL_LUDO_ID, 500) }
        assertEquals(null, kit.found("a"))
        mm.join("d", ROYAL_LUDO_ID, 500)
        assertEquals(500, kit.found("d")!!.entryCoins)
        assertEquals(null, kit.found("broke"))
        assertEquals(listOf("a", "b", "c", "d").map { it to 500 }, charged.sortedBy { it.first })
    }

    private companion object {
        const val DEADLINE_MS = 31_000L
    }
}
