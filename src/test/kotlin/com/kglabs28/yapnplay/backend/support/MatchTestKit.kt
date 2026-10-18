package com.kglabs28.yapnplay.backend.support

import com.kglabs28.yapnplay.backend.application.match.MatchRegistry
import com.kglabs28.yapnplay.backend.application.match.MatchService
import com.kglabs28.yapnplay.backend.application.match.RoomGateway
import com.kglabs28.yapnplay.backend.application.match.actor.MatchDeps
import com.kglabs28.yapnplay.backend.application.match.games.citybazaar.CityBazaarSessionFactory
import com.kglabs28.yapnplay.backend.application.match.games.royalludo.RoyalLudoSessionFactory
import com.kglabs28.yapnplay.backend.application.realtime.EnvelopeCodec
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.identity.ProgressionPolicy
import com.kglabs28.yapnplay.backend.domain.match.FinishedMatch
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarEngine
import com.kglabs28.yapnplay.backend.games.royalludo.engine.RoyalLudoEngine
import kotlinx.coroutines.test.TestScope
import kotlin.random.Random

/**
 * A match service on virtual time, with recorded pushes and results, for
 * match, matchmaking and moderation tests.
 */
class MatchTestKit(scope: TestScope, seed: Int = SEED) {
    val clock = Clock { scope.testScheduler.currentTime }
    val notifier = RecordingNotifier()
    val finished = mutableListOf<FinishedMatch>()
    val present = mutableMapOf<String, String>()
    var leader: String? = null
    val registry = MatchRegistry()
    private var ids = 0
    private val random = Random(seed)
    private val gameRandom = object : GameRandom {
        override fun nextInt(bound: Int): Int = random.nextInt(bound)
    }
    val codec = EnvelopeCodec(testJson) { "id-${++ids}" }
    private val rooms = object : RoomGateway {
        override fun leaderOf(roomId: String): String? = leader
        override fun isPresent(userId: String, roomId: String): Boolean = present[userId] == roomId
        override suspend fun matchEnded(roomId: String, matchId: String) = Unit
    }
    val deps = MatchDeps(notifier, codec, clock, rooms) { finished += it }
    val service = MatchService(
        factories = listOf(
            RoyalLudoSessionFactory(RoyalLudoEngine(), gameRandom, testJson),
            CityBazaarSessionFactory(CityBazaarEngine(), gameRandom, testJson, clock),
        ),
        registry = registry,
        deps = deps,
        users = InMemoryUserRepository(),
        rooms = InMemoryPlayRoomRepository(),
        ids = { "match-${++ids}" },
        clock = clock,
        progression = ProgressionPolicy(),
        scope = scope.backgroundScope,
    )

    private companion object {
        const val SEED = 7
    }
}
