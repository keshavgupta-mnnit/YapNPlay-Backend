package com.kglabs28.yapnplay.backend.di

import com.kglabs28.yapnplay.backend.application.match.GetMatchHistoryUseCase
import com.kglabs28.yapnplay.backend.games.monochaos.engine.MonoChaosEngine
import com.kglabs28.yapnplay.backend.games.mono.engine.MonoEngine
import com.kglabs28.yapnplay.backend.application.match.games.mono.MonoChaosSessionFactory
import com.kglabs28.yapnplay.backend.application.match.games.mono.MonoSessionFactory
import com.kglabs28.yapnplay.backend.domain.settings.Settings
import com.kglabs28.yapnplay.backend.application.economy.CoinsService
import com.kglabs28.yapnplay.backend.config.BackendConfig
import com.kglabs28.yapnplay.backend.application.match.MatchCheckpointStore
import com.kglabs28.yapnplay.backend.application.match.MatchLifecycleListener
import com.kglabs28.yapnplay.backend.application.match.MatchOutcomeStore
import com.kglabs28.yapnplay.backend.application.match.MatchPresenceUseCase
import com.kglabs28.yapnplay.backend.application.match.MatchRegistry
import com.kglabs28.yapnplay.backend.application.match.MatchResultRecorder
import com.kglabs28.yapnplay.backend.application.match.GetActiveMatchUseCase
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
import com.kglabs28.yapnplay.backend.infrastructure.db.ExposedMatchCheckpointStore
import com.kglabs28.yapnplay.backend.infrastructure.db.ExposedMatchOutcomeStore
import com.kglabs28.yapnplay.backend.infrastructure.security.SecureGameRandom
import org.koin.dsl.module

/** The games the server runs, matches and Quick Play. A new game adds its factory to the list. */
val matchModule = module {
    single<GameRandom> { SecureGameRandom() }
    single<List<GameSessionFactory>> {
        listOf(
            RoyalLudoSessionFactory(RoyalLudoEngine(), get(), get(), get<Settings>()),
            CityBazaarSessionFactory(CityBazaarEngine(), get(), get(), get(), get<Settings>()),
            MonoSessionFactory(MonoEngine(), get(), get(), get(), get<Settings>()),
            MonoChaosSessionFactory(MonoChaosEngine(), get(), get(), get(), get<Settings>()),
        )
    }
    single { MatchRegistry() }
    single<MatchLifecycleListener> { MatchResultRecorder(get(), get(), get()) }
    single<MatchCheckpointStore> { ExposedMatchCheckpointStore(get(), get()) }
    single<MatchOutcomeStore> { ExposedMatchOutcomeStore(get()) }
    single {
        MatchDeps(
            notifier = get(), codec = get(), clock = get(), rooms = get(), checkpoints = get(), outcomes = get(),
            settings = get(), progression = get(), economy = get<CoinsService>(), listener = get(),
        )
    }
    factory { GetActiveMatchUseCase(get()) }
    factory { GetMatchHistoryUseCase(get()) }
    single { MatchService(get(), get(), get(), get(), get(), get(), get(), get(), get(ROOMS_SCOPE), get<CoinsService>(), get<Settings>()) }
    single { SendMatchActionUseCase(get()) }
    single { MatchPresenceUseCase(get()) }
    single { MatchmakingService(get(), get(), get(), get(), get(), get(), get(ROOMS_SCOPE), get<CoinsService>(), get<Settings>()) }
}
