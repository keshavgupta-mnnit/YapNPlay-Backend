package com.kglabs28.yapnplay.backend.application

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.moderation.ModerationService
import com.kglabs28.yapnplay.backend.application.rooms.RoomPresence
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.ReportPlayer
import com.kglabs28.yapnplay.backend.contract.ReportResolved
import com.kglabs28.yapnplay.backend.contract.ReportVoteRequested
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.support.InMemoryModerationRepository
import com.kglabs28.yapnplay.backend.support.InMemoryUserRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Reports decided by the people present; the loser gets a penalty. */
@OptIn(ExperimentalCoroutinesApi::class)
class ModerationServiceTest {
    private val repository = InMemoryModerationRepository()
    private val presence = RoomPresence()

    private fun TestScope.service(kit: MatchTestKit) = ModerationService(
        repository, InMemoryUserRepository(), kit.registry, presence, kit.notifier, kit.codec, { "r-${kit.notifier.sent.size}" }, kit.clock, backgroundScope,
    )

    private fun MatchTestKit.resolved(): ReportResolved =
        notifier.sent.last { it.second.type == MessageType.REPORT_RESOLVED }.let { testJson.decodeFromJsonElement(ReportResolved.serializer(), it.second.payload) }

    private fun room(vararg users: String) = users.forEach { presence.enter(it, ROOM) }

    @Test
    fun `two agreements uphold the report and penalise the reported person`() = runTest {
        val kit = MatchTestKit(this)
        val moderation = service(kit)
        room("rep", "bad", "v1", "v2")
        moderation.report("rep", ReportPlayer(ROOM, "bad", REASON))
        val reportId = testJson.decodeFromJsonElement(
            ReportVoteRequested.serializer(),
            kit.notifier.sent.first { it.first == "v1" }.second.payload,
        ).reportId
        assertFalse(kit.notifier.sent.any { it.first == "bad" && it.second.type == MessageType.REPORT_VOTE_REQUESTED })
        moderation.vote("v1", reportId, agree = true)
        moderation.vote("v2", reportId, agree = true)
        runCurrent()
        assertTrue(kit.resolved().upheld)
        assertEquals("bad", repository.penalties.single().userId)
        assertTrue(MessageType.PENALTY_ISSUED in kit.notifier.typesFor("bad"))
    }

    @Test
    fun `no answers count as no vote and the false report penalises the reporter`() = runTest {
        val kit = MatchTestKit(this)
        val moderation = service(kit)
        room("rep", "bad", "v1", "v2", "v3")
        moderation.report("rep", ReportPlayer(ROOM, "bad", REASON))
        advanceTimeBy(VOTE_WINDOW_MS)
        runCurrent()
        val resolved = kit.resolved()
        assertFalse(resolved.upheld)
        assertEquals(3, resolved.noResponse)
        assertEquals("rep", repository.penalties.single().userId)
    }

    @Test
    fun `fewer than two voters means no report`() = runTest {
        val kit = MatchTestKit(this)
        room("rep", "bad", "v1")
        val e = assertFailsWith<ServiceException> { service(kit).report("rep", ReportPlayer(ROOM, "bad", REASON)) }
        assertEquals(ErrorCode.BAD_REQUEST, e.code)
    }

    private companion object {
        const val ROOM = "room-1"
        const val REASON = "voice_chat"
        const val VOTE_WINDOW_MS = 16_000L
    }
}
