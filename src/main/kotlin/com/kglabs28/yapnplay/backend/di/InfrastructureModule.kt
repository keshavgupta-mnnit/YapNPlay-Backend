package com.kglabs28.yapnplay.backend.di

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
    single<IdGenerator> { UuidIdGenerator() }
    single<RandomSource> { SecureRandomSource() }
    single<DatabaseHandle> { overrides.database ?: DatabaseFactory().open(config.database) }
    single { DbQuery(get<DatabaseHandle>().database) }
    single<UserRepository> { ExposedUserRepository(get()) }
    single<PlayRoomRepository> { ExposedPlayRoomRepository(get()) }
    single<MatchResultRepository> { ExposedMatchResultRepository(get()) }
    single<FriendRepository> { ExposedFriendRepository(get()) }
    single<FriendRequestRepository> { ExposedFriendRequestRepository(get()) }
    single<BlockRepository> { ExposedBlockRepository(get()) }
    single<DeviceRepository> { ExposedDeviceRepository(get()) }
    single<JoinRequestRepository> { ExposedJoinRequestRepository(get()) }
    single<ModerationRepository> { ExposedModerationRepository(get()) }
    single<SecretHasher> { Sha256SecretHasher() }
    single<SecretGenerator> { SecureRandomSecretGenerator() }
}
