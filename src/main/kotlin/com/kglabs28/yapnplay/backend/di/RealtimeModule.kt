package com.kglabs28.yapnplay.backend.di

import com.kglabs28.yapnplay.backend.application.identity.OnlineTimeTracker
import com.kglabs28.yapnplay.backend.api.ws.ConnectionRegistry
import com.kglabs28.yapnplay.backend.api.ws.EnvelopeRouter
import com.kglabs28.yapnplay.backend.api.ws.ConnectionHooks
import com.kglabs28.yapnplay.backend.api.ws.MatchChannelHandler
import com.kglabs28.yapnplay.backend.api.ws.MatchmakingChannelHandler
import com.kglabs28.yapnplay.backend.api.ws.ModerationChannelHandler
import com.kglabs28.yapnplay.backend.api.ws.RoomChannelHandler
import com.kglabs28.yapnplay.backend.application.realtime.ClientNotifier
import com.kglabs28.yapnplay.backend.application.realtime.EnvelopeCodec
import com.kglabs28.yapnplay.backend.application.stats.GetOnlineStatsUseCase
import kotlinx.serialization.json.Json
import org.koin.dsl.bind
import org.koin.dsl.module

/** JSON rules, live connections and message routing. */
val realtimeModule = module {
    // Contract: camelCase names, unknown fields ignored, defaults always written.
    single {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
    single { EnvelopeCodec(get(), get()) }
    single { ConnectionRegistry(get()) } bind ClientNotifier::class
    single { RoomChannelHandler(get(), get(), get(), get(), get(), get()) }
    single { MatchChannelHandler(get(), get(), get()) }
    single { MatchmakingChannelHandler(get(), get()) }
    single { ModerationChannelHandler(get(), get()) }
    single { EnvelopeRouter(get(), get(), get(), get(), get()) }
    single { OnlineTimeTracker(get(), get()) }
    single { ConnectionHooks(get(), get(), get(), get()) }
    single { GetOnlineStatsUseCase(get()) }
}
