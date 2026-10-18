package com.kglabs28.yapnplay.backend.di

import com.kglabs28.yapnplay.backend.application.match.MatchLifecycleListener
import com.kglabs28.yapnplay.backend.application.match.MatchPresenceUseCase
import com.kglabs28.yapnplay.backend.application.match.MatchRegistry
import com.kglabs28.yapnplay.backend.application.match.MatchResultRecorder
import com.kglabs28.yapnplay.backend.application.match.MatchService
import com.kglabs28.yapnplay.backend.application.match.SendMatchActionUseCase
import com.kglabs28.yapnplay.backend.application.match.actor.MatchDeps
import com.kglabs28.yapnplay.backend.application.match.games.GameSessionFactory
import com.kglabs28.yapnplay.backend.application.match.games.citybazaar.CityBazaarSessionFactory
import com.kglabs28.yapnplay.backend.application.match.games.royalludo.RoyalLudoSessionFactory
import com.kglabs28.yapnplay.backend.application.matchmaking.MatchmakingService
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarEngine
import com.kglabs28.yapnplay.backend.games.royalludo.engine.RoyalLudoEngine
import com.kglabs28.yapnplay.backend.infrastructure.security.SecureGameRandom
import org.koin.dsl.module

/** The games the server runs, matches and Quick Play. A new game adds its factory to the list. */
val matchModule = module {
    single<GameRandom> { SecureGameRandom() }
    single<List<GameSessionFactory>> {
        listOf(
            RoyalLudoSessionFactory(RoyalLudoEngine(), get(), get()),
            CityBazaarSessionFactory(CityBazaarEngine(), get(), get(), get()),
        )
    }
    single { MatchRegistry() }
    single<MatchLifecycleListener> { MatchResultRecorder(get(), get(), get()) }
    single { MatchDeps(get(), get(), get(), get(), get()) }
    single { MatchService(get(), get(), get(), get(), get(), get(), get(), get(), get(ROOMS_SCOPE)) }
    single { SendMatchActionUseCase(get()) }
    single { MatchPresenceUseCase(get()) }
    single { MatchmakingService(get(), get(), get(), get(), get(), get(), get(ROOMS_SCOPE)) }
}
