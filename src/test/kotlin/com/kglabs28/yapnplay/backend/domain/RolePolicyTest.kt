package com.kglabs28.yapnplay.backend.domain

import com.kglabs28.yapnplay.backend.domain.rooms.LiveMember
import com.kglabs28.yapnplay.backend.domain.rooms.RolePolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RolePolicyTest {
    private val policy = RolePolicy()

    private fun member(id: String, joinedAt: Long, onlineSince: Long? = null) =
        LiveMember(id, id, joinedAt, online = onlineSince != null, onlineSince = onlineSince)

    @Test
    fun `next leader is the member online longest`() {
        val members = listOf(member("lead", 0, 5), member("a", 1, 30), member("b", 2, 10))
        assertEquals("b", policy.nextLeader(members, outgoing = "lead"))
    }

    @Test
    fun `no next leader when nobody else is online`() {
        val members = listOf(member("lead", 0, 5), member("a", 1))
        assertNull(policy.nextLeader(members, outgoing = "lead"))
    }

    @Test
    fun `an arrival takes over from an offline leader but not an online one`() {
        val offlineLeader = mapOf("lead" to member("lead", 0), "a" to member("a", 1, 9))
        assertEquals("a", policy.leaderAfterArrival(offlineLeader, "lead", arriving = "a"))
        val onlineLeader = mapOf("lead" to member("lead", 0, 5), "a" to member("a", 1, 9))
        assertEquals("lead", policy.leaderAfterArrival(onlineLeader, "lead", arriving = "a"))
    }

    @Test
    fun `next admin is the longest-standing member`() {
        val members = listOf(member("admin", 0), member("late", 9), member("early", 3))
        assertEquals("early", policy.nextAdmin(members, outgoing = "admin"))
    }
}
