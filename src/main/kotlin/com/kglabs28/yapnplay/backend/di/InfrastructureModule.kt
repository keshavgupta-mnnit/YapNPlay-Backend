package com.kglabs28.yapnplay.backend.di

import com.kglabs28.yapnplay.backend.infrastructure.db.ExposedFeedbackRepository
import com.kglabs28.yapnplay.backend.domain.feedback.FeedbackRepository
import com.kglabs28.yapnplay.backend.domain.settings.GameSettings
import com.kglabs28.yapnplay.backend.application.settings.SettingsService
import com.kglabs28.yapnplay.backend.infrastructure.db.ExposedAdminAuditRepository
import com.kglabs28.yapnplay.backend.infrastructure.db.ExposedSettingsRepository
import com.kglabs28.yapnplay.backend.domain.admin.AdminAuditRepository
import com.kglabs28.yapnplay.backend.domain.settings.Settings
import com.kglabs28.yapnplay.backend.domain.settings.SettingsRepository
import com.kglabs28.yapnplay.backend.application.economy.CoinsService
import com.kglabs28.yapnplay.backend.infrastructure.db.ExposedCoinStore
import com.kglabs28.yapnplay.backend.domain.economy.CoinStore
import com.kglabs28.yapnplay.backend.infrastructure.identity.GooglePlayGamesVerifier
import com.kglabs28.yapnplay.backend.domain.identity.PlayGamesVerifier
import com.kglabs28.yapnplay.backend.infrastructure.db.ExposedRoomInviteRepository
import com.kglabs28.yapnplay.backend.domain.rooms.RoomInviteRepository
import com.kglabs28.yapnplay.backend.config.BackendConfig
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.common.IdGenerator
import com.kglabs28.yapnplay.backend.domain.common.RandomSource
import com.kglabs28.yapnplay.backend.domain.identity.SecretGenerator
import com.kglabs28.yapnplay.backend.domain.identity.SecretHasher
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoomRepository
import com.kglabs28.yapnplay.backend.infrastructure.db.DatabaseFactory
import com.kglabs28.yapnplay.backend.infrastructure.db.DatabaseHandle
import com.kglabs28.yapnplay.backend.infrastructure.db.DbQuery
import com.kglabs28.yapnplay.backend.infrastructure.db.ExposedPlayRoomRepository
import com.kglabs28.yapnplay.backend.infrastructure.db.ExposedUserRepository
import com.kglabs28.yapnplay.backend.infrastructure.ids.UuidIdGenerator
import com.kglabs28.yapnplay.backend.infrastructure.security.SecureRandomSecretGenerator
import com.kglabs28.yapnplay.backend.infrastructure.security.SecureRandomSource
import com.kglabs28.yapnplay.backend.infrastructure.security.Sha256SecretHasher
import com.kglabs28.yapnplay.backend.infrastructure.time.SystemClock
import com.kglabs28.yapnplay.backend.domain.match.MatchResultRepository
import com.kglabs28.yapnplay.backend.domain.moderation.ModerationRepository
import com.kglabs28.yapnplay.backend.domain.rooms.JoinRequestRepository
import com.kglabs28.yapnplay.backend.domain.social.DeviceRepository
import com.kglabs28.yapnplay.backend.domain.social.FriendRepository
import com.kglabs28.yapnplay.backend.domain.social.FriendRequestRepository
import com.kglabs28.yapnplay.backend.domain.social.BlockRepository
import com.kglabs28.yapnplay.backend.infrastructure.db.ExposedFriendRequestRepository
import com.kglabs28.yapnplay.backend.infrastructure.db.ExposedBlockRepository
import com.kglabs28.yapnplay.backend.infrastructure.db.ExposedDeviceRepository
import com.kglabs28.yapnplay.backend.infrastructure.db.ExposedFriendRepository
import com.kglabs28.yapnplay.backend.infrastructure.db.ExposedJoinRequestRepository
import com.kglabs28.yapnplay.backend.infrastructure.db.ExposedMatchResultRepository
import com.kglabs28.yapnplay.backend.infrastructure.db.ExposedModerationRepository
import org.koin.dsl.module

/** Database, clock, ids, randomness and hashing. */
fun infrastructureModule(config: BackendConfig, overrides: BackendOverrides) = module {
    single { config }
    single<Clock> { overrides.clock ?: SystemClock() }
    single<PlayGamesVerifier> { overrides.playGames ?: GooglePlayGamesVerifier(config.playGamesClientId, config.playGamesClientSecret) }
    single<IdGenerator> { UuidIdGenerator() }
    single<RandomSource> { SecureRandomSource() }
    single<DatabaseHandle> { overrides.database ?: DatabaseFactory().open(config.database) }
    single { DbQuery(get<DatabaseHandle>().database) }
    single<UserRepository> { ExposedUserRepository(get()) }
    single<PlayRoomRepository> { ExposedPlayRoomRepository(get()) }
    single<MatchResultRepository> { ExposedMatchResultRepository(get()) }
    single<FriendRepository> { ExposedFriendRepository(get()) }
    single<FeedbackRepository> { ExposedFeedbackRepository(get()) }
    single<FriendRequestRepository> { ExposedFriendRequestRepository(get()) }
    single<BlockRepository> { ExposedBlockRepository(get()) }
    single<DeviceRepository> { ExposedDeviceRepository(get()) }
    single<JoinRequestRepository> { ExposedJoinRequestRepository(get()) }
    single<RoomInviteRepository> { ExposedRoomInviteRepository(get()) }
    single<CoinStore> { ExposedCoinStore(get()) }
    single { CoinsService(get(), get(), settings = get()) }
    single<SettingsRepository> { ExposedSettingsRepository(get()) }
    single<AdminAuditRepository> { ExposedAdminAuditRepository(get()) }
    single {
        // Env values set on Render stay the defaults until changed in the admin dashboard.
        val config = get<BackendConfig>()
        SettingsService(
            get(), get(), get(),
            defaults = mapOf(
                GameSettings.CB_ROUND_LIMIT.key to config.cityBazaarRoundLimit,
                GameSettings.CB_EXTENSION_ROUNDS.key to config.cityBazaarExtensionRounds,
                GameSettings.CB_BANK_START.key to config.cityBazaarBankStart,
            ),
        )
    }
    single<Settings> { get<SettingsService>() }
    single<ModerationRepository> { ExposedModerationRepository(get()) }
    single<SecretHasher> { Sha256SecretHasher() }
    single<SecretGenerator> { SecureRandomSecretGenerator() }
}
