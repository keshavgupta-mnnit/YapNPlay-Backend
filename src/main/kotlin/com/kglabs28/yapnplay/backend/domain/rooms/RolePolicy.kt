package com.kglabs28.yapnplay.backend.domain.rooms

/**
 * Who leads and who administers (decisions.md, "room lobby: roles").
 * Pure functions, so every hand-over case is unit-tested.
 */
class RolePolicy {

    /**
     * The next leader when the current one goes offline or leaves:
     * the online member who has been online longest. Null when nobody
     * else is online (the leader then stays until someone returns).
     */
    fun nextLeader(members: Collection<LiveMember>, outgoing: String): String? =
        members.filter { it.online && it.userId != outgoing }
            .minByOrNull { it.onlineSince ?: Long.MAX_VALUE }
            ?.userId

    /** Whoever comes online takes over from a leader who is offline. */
    fun leaderAfterArrival(members: Map<String, LiveMember>, leaderId: String, arriving: String): String =
        if (members[leaderId]?.online == true) leaderId else arriving

    /**
     * The next admin when the admin leaves: the longest-standing member.
     * (The contract says "top of the room leaderboard"; there is no
     * leaderboard yet, so seniority stands in.)
     */
    fun nextAdmin(members: Collection<LiveMember>, outgoing: String): String? =
        members.filter { it.userId != outgoing }.minByOrNull { it.joinedAt }?.userId
}
