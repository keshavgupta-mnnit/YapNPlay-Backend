package com.kglabs28.yapnplay.backend.infrastructure

import com.kglabs28.yapnplay.backend.application.admin.AccountType
import com.kglabs28.yapnplay.backend.application.admin.AccountTypeCount
import com.kglabs28.yapnplay.backend.application.admin.DayCount
import com.kglabs28.yapnplay.backend.application.admin.StoredGameCounts
import com.kglabs28.yapnplay.backend.application.admin.StoredModerationCounts
import com.kglabs28.yapnplay.backend.infrastructure.db.DbQuery
import com.kglabs28.yapnplay.backend.infrastructure.db.ExposedAdminStatsQueries
import com.kglabs28.yapnplay.backend.support.TestDatabase
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminStatsQueriesTest {
    private val queries = ExposedAdminStatsQueries(DbQuery(TestDatabase.handle.database))
    private val zone = ZoneId.of(ZONE)

    @BeforeTest
    fun emptyDatabase() {
        TestDatabase.reset()
        // match_results has no foreign key to users, so the shared reset leaves it.
        sql("DELETE FROM match_results")
    }

    @Test
    fun `empty database gives zeros`() = runBlocking {
        assertEquals(emptyList(), queries.userCountsByType())
        assertEquals(0, queries.roomCount())
        assertEquals(emptyList(), queries.matchesByGame())
        assertEquals(StoredModerationCounts(0, 0, 0, 0, 0, 0), queries.moderationCounts(NOW))
        assertEquals(emptyList(), queries.mostReported(10))
        assertEquals(emptyList(), queries.usersPerDay(0, ZONE))
    }

    @Test
    fun `users and rooms are counted in total and per local day`() = runBlocking {
        // 18:45 UTC on 1 Oct is already 2 Oct in India.
        val a = user("Asha", millis("2026-10-01T10:00:00Z"))
        user("Bilal", millis("2026-10-01T18:45:00Z"))
        user("Chen", millis("2026-10-02T09:00:00Z"))
        user("Old", millis("2026-09-01T09:00:00Z"))
        room(a, millis("2026-10-02T05:00:00Z"))
        room(a, millis("2026-10-02T06:00:00Z"))

        val since = LocalDate.parse("2026-10-01").atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(listOf(AccountTypeCount(AccountType.GUEST, 4)), queries.userCountsByType())
        assertEquals(listOf(DayCount("2026-10-01", 1), DayCount("2026-10-02", 2)), queries.usersPerDay(since, ZONE))
        assertEquals(2, queries.roomCount())
        assertEquals(listOf(DayCount("2026-10-02", 2)), queries.roomsPerDay(since, ZONE))
    }

    @Test
    fun `matches are counted per game, busiest first, with Quick Play and human seats`() = runBlocking {
        val a = user("Asha")
        val b = user("Bilal")
        val roomId = room(a)
        match("royal-ludo", roomId = null, humans = listOf(a, b), cpus = 2)
        match("royal-ludo", roomId = roomId, humans = listOf(a), cpus = 1)
        match("royal-ludo", roomId = null, humans = listOf(b), cpus = 3)
        match("city-bazaar", roomId = roomId, humans = listOf(a, b), cpus = 0)

        assertEquals(
            listOf(
                StoredGameCounts("royal-ludo", total = 3, quickPlay = 2, humanSeats = 4),
                StoredGameCounts("city-bazaar", total = 1, quickPlay = 0, humanSeats = 2),
            ),
            queries.matchesByGame(),
        )
    }

    @Test
    fun `reports split into upheld, rejected and pending, penalties into issued and active`() = runBlocking {
        val reporter = user("Asha")
        val loud = user("Loud", code = "123456")
        val rude = user("Rude")
        val upheld1 = report(reporter, loud, upheld = true)
        report(reporter, loud, upheld = true)
        report(reporter, loud, upheld = false)
        report(reporter, rude, upheld = null)
        report(reporter, rude, upheld = false)
        penalty(loud, upheld1, blockedUntil = NOW + 60_000, cleared = false)
        penalty(loud, null, blockedUntil = NOW - 60_000, cleared = false)
        penalty(rude, null, blockedUntil = NOW + 60_000, cleared = true)

        assertEquals(
            StoredModerationCounts(reports = 5, upheld = 2, rejected = 2, pending = 1, penalties = 3, penaltiesActive = 1),
            queries.moderationCounts(NOW),
        )
        val top = queries.mostReported(10)
        assertEquals(listOf("Loud", "Rude"), top.map { it.displayName })
        assertEquals(listOf(3, 2), top.map { it.reports })
        assertEquals(listOf(2, 0), top.map { it.upheld })
        assertEquals("123456", top.first().userCode)
        assertEquals(1, queries.mostReported(1).size)
    }

    private fun user(name: String, createdAt: Long = NOW, code: String? = null): UUID {
        val id = UUID.randomUUID()
        sql(
            "INSERT INTO users (id, display_name, secret_hash, created_at, user_code) VALUES " +
                "('$id', '$name', '${"0".repeat(64)}', $createdAt, ${code?.let { "'$it'" } ?: "NULL"})",
        )
        return id
    }

    private fun room(owner: UUID, createdAt: Long = NOW): UUID {
        val id = UUID.randomUUID()
        val code = (100_000 + (Math.random() * 899_999).toInt()).toString()
        sql(
            "INSERT INTO play_rooms (id, code, name, icon, created_by, leader_id, admin_id, created_at) VALUES " +
                "('$id', '$code', 'Room', 'icon', '$owner', '$owner', '$owner', $createdAt)",
        )
        return id
    }

    private fun match(gameId: String, roomId: UUID?, humans: List<UUID>, cpus: Int) {
        val id = UUID.randomUUID()
        sql(
            "INSERT INTO match_results (id, game_id, room_id, started_at, ended_at) VALUES " +
                "('$id', '$gameId', ${roomId?.let { "'$it'" } ?: "NULL"}, $NOW, $NOW)",
        )
        val seats = humans.map { "'$it'" } + List(cpus) { "NULL" }
        seats.forEachIndexed { seat, userId ->
            sql("INSERT INTO match_result_seats (match_id, seat, user_id, place) VALUES ('$id', $seat, $userId, ${seat + 1})")
        }
    }

    private fun report(reporter: UUID, reported: UUID, upheld: Boolean?): UUID {
        val id = UUID.randomUUID()
        val resolvedAt = if (upheld == null) "NULL" else NOW.toString()
        sql(
            "INSERT INTO reports (id, context_id, reporter_id, reported_id, reason, required, created_at, resolved_at, upheld) VALUES " +
                "('$id', 'room', '$reporter', '$reported', 'ABUSE', 2, $NOW, $resolvedAt, ${upheld ?: "NULL"})",
        )
        return id
    }

    private fun penalty(user: UUID, reportId: UUID?, blockedUntil: Long, cleared: Boolean) {
        sql(
            "INSERT INTO penalties (id, user_id, report_id, points, blocked_until, created_at, cleared_at) VALUES " +
                "('${UUID.randomUUID()}', '$user', ${reportId?.let { "'$it'" } ?: "NULL"}, 10, $blockedUntil, $NOW, " +
                "${if (cleared) NOW.toString() else "NULL"})",
        )
    }

    private fun sql(statement: String) {
        transaction(TestDatabase.handle.database) { exec(statement) }
    }

    private fun millis(instant: String) = java.time.Instant.parse(instant).toEpochMilli()

    private companion object {
        const val ZONE = "Asia/Kolkata"
        const val NOW = 1_790_000_000_000L
    }
}
