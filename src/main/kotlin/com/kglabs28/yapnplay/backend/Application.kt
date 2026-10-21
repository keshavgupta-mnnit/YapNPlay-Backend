package com.kglabs28.yapnplay.backend

import com.kglabs28.yapnplay.backend.application.settings.SettingsService
import com.kglabs28.yapnplay.backend.infrastructure.monitoring.ErrorTracking
import com.kglabs28.yapnplay.backend.application.rooms.PlayRoomActors
import com.kglabs28.yapnplay.backend.config.BackendConfig
import com.kglabs28.yapnplay.backend.config.BackendConfigLoader
import com.kglabs28.yapnplay.backend.config.DotEnvFile
import com.kglabs28.yapnplay.backend.di.ROOMS_SCOPE
import com.kglabs28.yapnplay.backend.di.BackendOverrides
import com.kglabs28.yapnplay.backend.di.adminModule
import com.kglabs28.yapnplay.backend.di.identityModule
import com.kglabs28.yapnplay.backend.di.infrastructureModule
import com.kglabs28.yapnplay.backend.di.realtimeModule
import com.kglabs28.yapnplay.backend.di.roomsModule
import com.kglabs28.yapnplay.backend.di.matchModule
import com.kglabs28.yapnplay.backend.di.socialModule
import com.kglabs28.yapnplay.backend.application.match.MatchRegistry
import com.kglabs28.yapnplay.backend.application.match.MatchService
import com.kglabs28.yapnplay.backend.infrastructure.db.DatabaseHandle
import com.kglabs28.yapnplay.backend.plugins.configureHttp
import com.kglabs28.yapnplay.backend.plugins.configureRouting
import com.kglabs28.yapnplay.backend.plugins.configureSecurity
import com.kglabs28.yapnplay.backend.plugins.configureSockets
import com.kglabs28.yapnplay.backend.plugins.configureStatusPages
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.koin.dsl.koinApplication
import org.koin.logger.slf4jLogger

/** Starts the server: `gradlew run` on the laptop, the fat JAR in the cloud. */
fun main() {
    val config = BackendConfigLoader(DotEnvFile().withEnvironment()).load()
    // Server errors go to Sentry when SENTRY_DSN is set (otherwise only to the log).
    ErrorTracking.start(config)
    embeddedServer(Netty, port = config.port, host = ALL_INTERFACES) { module(config) }.start(wait = true)
}

/** Wires everything together. Tests call this with their own [config] and [overrides]. */
fun Application.module(config: BackendConfig, overrides: BackendOverrides = BackendOverrides()) {
    val koin = koinApplication {
        slf4jLogger()
        modules(
            infrastructureModule(config, overrides),
            identityModule,
            roomsModule(config),
            matchModule,
            socialModule(config),
            realtimeModule,
            adminModule(config),
        )
    }.koin
    // Open the database (and run migrations) at startup, not on the first request.
    koin.get<DatabaseHandle>()
    // Matches that were running when the server last stopped carry on.
    // Settings first: restored matches and new ones read them.
    runBlocking { koin.get<SettingsService>().load() }
    val restored = runBlocking { koin.get<MatchService>().restoreSaved() }
    if (restored > 0) log.info("Brought back $restored running match(es)")

    configureHttp(koin.get())
    configureStatusPages()
    configureSecurity(koin.get(), config.devMode, config.guestCreationsPerHour)
    configureSockets()
    configureRouting(koin)

    monitor.subscribe(ApplicationStopped) {
        koin.get<PlayRoomActors>().shutdown()
        koin.get<MatchRegistry>().shutdown()
        koin.get<CoroutineScope>(ROOMS_SCOPE).cancel()
        if (overrides.database == null) koin.get<DatabaseHandle>().close()
        koin.close()
    }
}

private const val ALL_INTERFACES = "0.0.0.0"
