package com.kglabs28.yapnplay.backend.api

import com.kglabs28.yapnplay.backend.contract.ActiveMatch
import com.kglabs28.yapnplay.backend.contract.JoinQueue
import com.kglabs28.yapnplay.backend.contract.MatchFound
import com.kglabs28.yapnplay.backend.contract.MatchRequestSnapshot
import com.kglabs28.yapnplay.backend.contract.MatchSnapshot
import com.kglabs28.yapnplay.backend.contract.transport.Channel
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.games.royalludo.utils.ROYAL_LUDO_ID
import com.kglabs28.yapnplay.backend.support.TestDatabase
import com.kglabs28.yapnplay.backend.support.WsTestClient
import io.ktor.client.HttpClient
import io.ktor.server.testing.ApplicationTestBuilder
import com.kglabs28.yapnplay.backend.support.newGuestToken
import com.kglabs28.yapnplay.backend.support.serverTest
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Quick Play the way the app plays it: queue alone, CPUs fill at the deadline, then the table runs. */
class QuickPlayWebSocketTest {

    @Test
    fun `a lone player gets a CPU-filled Royal Ludo table that plays`() = fastTable { client ->
        val token = client.newGuestToken()
        assertEquals(HttpStatusCode.NoContent, client.get("/matches/active") { bearerAuth(token) }.status)
        client.webSocket("/ws", request = { bearerAuth(token) }) {
            val ws = WsTestClient(this)
            val join = ws.sendOn(Channel.MATCHMAKING, MessageType.JOIN_QUEUE, JoinQueue.serializer(), JoinQueue(ROYAL_LUDO_ID))
            assertEquals(MessageType.ACK, ws.reply(join).type)

            val found = ws.decode(ws.next(MessageType.MATCH_FOUND, SEARCH_WAIT_MS), MatchFound.serializer())
            assertEquals(1, found.humanPlayers)
            assertEquals(3, found.cpuPlayers)
            // Closing the app now, the player could rejoin this table.
            val active = client.get("/matches/active") { bearerAuth(token) }.body<ActiveMatch>()
            assertEquals(found.matchId, active.matchId)
            assertEquals(ROYAL_LUDO_ID, active.gameId)

            val ask = ws.sendOn(Channel.MATCH, MessageType.MATCH_REQUEST_SNAPSHOT, MatchRequestSnapshot.serializer(), MatchRequestSnapshot(found.matchId, 0))
            val first = ws.decode(ws.reply(ask), MatchSnapshot.serializer())
            assertEquals(4, first.seats.size)
            assertEquals(1, first.seats.count { !it.cpu })
            // Everyone has an avatar: the guest got a random preset, CPUs the robot ones.
            assertTrue(first.seats.single { !it.cpu }.avatarId.matches(Regex("a(0[1-9]|1[0-8])")))
            assertEquals(listOf("a09", "a18", "a09"), first.seats.filter { it.cpu }.sortedBy { it.displayName }.map { it.avatarId })

            // Play goes on: CPUs take their turns and the viewer's turn comes round with a deadline.
            val mySeat = first.viewer.seat!!
            val myTurn = ws.decode(
                ws.next(TABLE_WAIT_MS) { it.type == MessageType.MATCH_SNAPSHOT && ws.decode(it, MatchSnapshot.serializer()).let { s -> s.turn.seat == mySeat && s.turn.phase == "ROLL" } },
                MatchSnapshot.serializer(),
            )
            assertTrue(myTurn.turn.deadline != null, "my turn has a deadline")

            val roll = ws.sendOn(Channel.MATCH, MessageType.ROLL_DICE, JsonObject.serializer(), buildJsonObject { put("matchId", found.matchId) })
            assertEquals(MessageType.ACK, ws.reply(roll).type)
        }
    }

    /**
     * Real time on a shared CI runner is slow: a short search and CPUs that don't stop to think
     * (runtime settings written before the server starts), and generous waits for the dice and
     * move animations of up to three CPU turns.
     */
    private fun fastTable(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) {
        TestDatabase.reset()
        TestDatabase.execute(
            "INSERT INTO game_settings (key, value, updated_at, updated_by) VALUES " +
                "('match.quick_play_search_seconds', '$SEARCH_SECONDS', 0, 'test'), ('rl.cpu_think_ms', '0', 0, 'test')",
        )
        serverTest(resetDatabase = false, block = block)
    }

    private companion object {
        const val SEARCH_SECONDS = 5
        const val SEARCH_WAIT_MS = (SEARCH_SECONDS + 15) * 1_000L
        const val TABLE_WAIT_MS = 120_000L
    }
}
