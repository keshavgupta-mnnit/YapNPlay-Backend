package com.kglabs28.yapnplay.backend.application.match

import com.kglabs28.yapnplay.backend.application.match.actor.MatchActor
import com.kglabs28.yapnplay.backend.application.match.actor.MatchDeps
import com.kglabs28.yapnplay.backend.application.match.actor.MatchSeatState
import com.kglabs28.yapnplay.backend.application.match.actor.MatchState
import com.kglabs28.yapnplay.backend.application.match.games.GameSessionFactory
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.common.IdGenerator
import com.kglabs28.yapnplay.backend.domain.identity.ProgressionPolicy
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository
import com.kglabs28.yapnplay.backend.domain.rooms.MatchStarter
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoomRepository
import com.kglabs28.yapnplay.backend.domain.rooms.SeatPlan
import kotlinx.coroutines.CoroutineScope

/**
 * Opens matches: from a room countdown ([MatchStarter]) or from Quick
 * Play. Seats and colours are assigned randomly by the server; CPU seats
 * get "cpu-<n>" ids and are played by the game's bot.
 */
class MatchService(
    private val factories: List<GameSessionFactory>,
    private val registry: MatchRegistry,
    private val deps: MatchDeps,
    private val users: UserRepository,
    private val rooms: PlayRoomRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val progression: ProgressionPolicy,
    private val scope: CoroutineScope,
) : MatchStarter {

    override suspend fun start(roomId: String, plan: SeatPlan): String {
        val title = rooms.findById(roomId)?.name ?: BackendStrings.QUICK_PLAY_TITLE
        return open(
            MatchSetup(
                matchId = ids.newId(),
                gameId = plan.gameId,
                roomId = roomId,
                title = title,
                players = holders(plan.players),
                cpuCount = plan.botCount,
                houseRules = plan.houseRules,
            ),
        )
    }

    /** A Quick Play table: public rules, no room, no spectators. */
    suspend fun startQuickPlay(gameId: String, userIds: List<String>, cpuCount: Int): String = open(
        MatchSetup(
            matchId = ids.newId(),
            gameId = gameId,
            roomId = null,
            title = BackendStrings.QUICK_PLAY_TITLE,
            players = holders(userIds),
            cpuCount = cpuCount,
            houseRules = null,
        ),
    )

    fun find(matchId: String): MatchActor? = registry.find(matchId)

    fun isPlaying(userId: String): Boolean = registry.activeMatchOf(userId) != null

    private fun open(setup: MatchSetup): String {
        val factory = factories.first { it.game.id.value == setup.gameId }
        val seatCount = setup.players.size + setup.cpuCount
        val session = factory.start(seatCount, setup.houseRules)
        val holders = (setup.players.map { it } + List(setup.cpuCount) { null }).shuffled()
        val seats = holders.mapIndexed { index, holder ->
            val seat = index + 1
            val cpuNumber = holders.take(index + 1).count { it == null }
            MatchSeatState(
                seat = seat,
                userId = holder?.userId ?: (BackendConstants.CPU_USER_PREFIX + cpuNumber),
                displayName = holder?.displayName ?: BackendStrings.cpuName(cpuNumber),
                level = holder?.level ?: BackendConstants.CPU_LEVEL,
                colour = session.colourOf(seat),
                cpu = holder == null,
            )
        }
        val now = clock.nowMillis()
        val state = MatchState(setup, session, seats.toMutableList(), now)
        // The first turn's timer runs from the start, even when a human moves first.
        state.deadline = now + session.turnSeconds * BackendConstants.MILLIS_PER_SECOND
        registry.add(MatchActor(state, deps, scope, onClosed = registry::remove))
        return setup.matchId
    }

    private suspend fun holders(userIds: List<String>): List<SeatHolder> = userIds.map { id ->
        val user = users.findById(id)
        SeatHolder(id, user?.displayName ?: id, progression.levelFor(user?.xp ?: 0))
    }
}
