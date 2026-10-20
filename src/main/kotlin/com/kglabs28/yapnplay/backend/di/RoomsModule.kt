package com.kglabs28.yapnplay.backend.di

import com.kglabs28.yapnplay.backend.application.economy.CoinsService
import com.kglabs28.yapnplay.backend.application.rooms.RoomInvitesUseCase
import org.slf4j.LoggerFactory
import kotlinx.coroutines.CoroutineExceptionHandler
import com.kglabs28.yapnplay.backend.application.match.MatchService
import com.kglabs28.yapnplay.backend.application.match.RoomGateway
import com.kglabs28.yapnplay.backend.application.match.games.GameSessionFactory
import com.kglabs28.yapnplay.backend.application.rooms.CreatePlayRoomUseCase
import com.kglabs28.yapnplay.backend.application.rooms.ExitCurrentRoomUseCase
import com.kglabs28.yapnplay.backend.application.rooms.GameCatalog
import com.kglabs28.yapnplay.backend.application.rooms.GetMyRoomsUseCase
import com.kglabs28.yapnplay.backend.application.rooms.JoinPlayRoomUseCase
import com.kglabs28.yapnplay.backend.application.rooms.PlayRoomActors
import com.kglabs28.yapnplay.backend.application.rooms.PlayRoomGateway
import com.kglabs28.yapnplay.backend.application.rooms.PreviewPlayRoomUseCase
import com.kglabs28.yapnplay.backend.application.rooms.RoomPresence
import com.kglabs28.yapnplay.backend.application.rooms.SendRoomActionUseCase
import com.kglabs28.yapnplay.backend.application.rooms.actor.PlayRoomDeps
import com.kglabs28.yapnplay.backend.config.BackendConfig
import com.kglabs28.yapnplay.backend.domain.rooms.RolePolicy
import com.kglabs28.yapnplay.backend.domain.rooms.RoomCodeGenerator
import com.kglabs28.yapnplay.backend.domain.rooms.StartPolicy
import com.kglabs28.yapnplay.backend.games.base.GameRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Qualifier of the coroutine scope that runs room actors, matches and timers. */
val ROOMS_SCOPE = named("rooms-scope")

/** Games, rooms and their actors. */
fun roomsModule(config: BackendConfig) = module {
    single { GameRegistry(get<List<GameSessionFactory>>().map { it.game }) }
    single { GameCatalog(get(), config.disabledGames) }
    single { RoomCodeGenerator(get()) }
    single { RolePolicy() }
    single { StartPolicy() }
    single { RoomPresence() }
    single<CoroutineScope>(ROOMS_SCOPE) {
        val log = LoggerFactory.getLogger(BACKGROUND_LOGGER)
        // Anything that crashes in background work (timers, actors) is logged at ERROR, so it reaches Sentry.
        val onCrash = CoroutineExceptionHandler { _, e -> log.error(BACKGROUND_CRASH, e) }
        CoroutineScope(SupervisorJob() + Dispatchers.Default + onCrash)
    }
    single {
        PlayRoomDeps(
            repository = get(),
            notifier = get(),
            presence = get(),
            games = get(),
            codec = get(),
            startPolicy = get(),
            rolePolicy = get(),
            matchStarter = get<MatchService>(),
            clock = get(),
            joinRequests = get(),
            friends = get(),
            blocks = get(),
            activity = get(),
            maxRoomsPerUser = config.maxRoomsPerUser,
            countdownSeconds = config.countdownSeconds,
            fees = get<CoinsService>(),
        )
    }
    single { PlayRoomActors(get(), get(ROOMS_SCOPE)) }
    // Lazy: matches need the room actors, and rooms need matches (MatchStarter).
    single<RoomGateway> { PlayRoomGateway(lazy { get<PlayRoomActors>() }, get()) }
    single { ExitCurrentRoomUseCase(get(), get()) }
    single { CreatePlayRoomUseCase(get(), get(), get(), get(), get(), get(), get(), config.maxRoomsPerUser, get(), get()) }
    single { RoomInvitesUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), config.maxRoomsPerUser) }
    single { JoinPlayRoomUseCase(get(), get(), get(), get(), get()) }
    single { SendRoomActionUseCase(get(), get()) }
    single { GetMyRoomsUseCase(get(), get()) }
    single { PreviewPlayRoomUseCase(get(), get(), get()) }
}

private const val BACKGROUND_LOGGER = "Background"
private const val BACKGROUND_CRASH = "Background work crashed"
