package com.kglabs28.yapnplay.backend.di

import com.kglabs28.yapnplay.backend.application.social.FriendPushes
import com.kglabs28.yapnplay.backend.application.rooms.GetRoomPendingInvitesUseCase
import com.kglabs28.yapnplay.backend.application.rooms.GetRoomLeaderboardUseCase
import com.kglabs28.yapnplay.backend.application.feedback.FeedbackUseCase
import com.kglabs28.yapnplay.backend.application.moderation.BlockChecker
import com.kglabs28.yapnplay.backend.application.moderation.ModerationService
import com.kglabs28.yapnplay.backend.application.push.PushSender
import com.kglabs28.yapnplay.backend.application.push.RoomActivityNotifier
import com.kglabs28.yapnplay.backend.application.social.FriendDirectory
import com.kglabs28.yapnplay.backend.application.social.FriendsUseCase
import com.kglabs28.yapnplay.backend.application.social.RegisterDeviceUseCase
import com.kglabs28.yapnplay.backend.application.voice.IssueVoiceTokenUseCase
import com.kglabs28.yapnplay.backend.config.BackendConfig
import com.kglabs28.yapnplay.backend.infrastructure.push.FcmPushSender
import com.kglabs28.yapnplay.backend.infrastructure.push.FirebaseServiceAccount
import com.kglabs28.yapnplay.backend.infrastructure.push.LoggingPushSender
import com.kglabs28.yapnplay.backend.infrastructure.voice.ConfiguredIceServers
import com.kglabs28.yapnplay.backend.application.voice.IceServerSource
import com.kglabs28.yapnplay.backend.application.voice.VoiceHub
import com.kglabs28.yapnplay.backend.application.voice.VoicePasses
import com.kglabs28.yapnplay.backend.application.voice.RelayUsageMeter
import com.kglabs28.yapnplay.backend.application.voice.VoiceKillSwitch
import com.kglabs28.yapnplay.backend.application.voice.VoicePolicy
import com.kglabs28.yapnplay.backend.domain.voice.RelayUsageStore
import com.kglabs28.yapnplay.backend.infrastructure.db.ExposedRelayUsageStore
import kotlinx.serialization.json.Json
import org.koin.dsl.module

/** Friends, devices and pushes, voice passes, moderation. */
fun socialModule(config: BackendConfig) = module {
    single { FriendDirectory(get(), get()) }
    single { BlockChecker(get(), get()) }
    single<PushSender> {
        val account = config.firebaseServiceAccountJson?.let { get<Json>().decodeFromString(FirebaseServiceAccount.serializer(), it) }
        if (account != null) FcmPushSender(account, get(), get(), get()) else LoggingPushSender()
    }
    single { RoomActivityNotifier(get(), get(), get(ROOMS_SCOPE)) }
    single { VoicePasses() }
    single<IceServerSource> { ConfiguredIceServers(config.voice, get()) }
    single<RelayUsageStore> { ExposedRelayUsageStore(get()) }
    single { RelayUsageMeter(get(), get(), get(ROOMS_SCOPE)) }
    single { VoicePolicy(get(), get()) }
    single { VoiceHub(get(), get(), get(), get(), get<VoicePolicy>()::voiceOn) }
    single(createdAtStart = true) { VoiceKillSwitch(get(), get(), get(ROOMS_SCOPE)) }
    single { IssueVoiceTokenUseCase(get(), get(), get(), get(), get(), get()) }
    single { ModerationService(get(), get(), get(), get(), get(), get(), get(), get(), get(ROOMS_SCOPE)) }
    single { RegisterDeviceUseCase(get(), get()) }
    single { FeedbackUseCase(get(), get(), get()) }
    single { GetRoomLeaderboardUseCase(get(), get()) }
    single { GetRoomPendingInvitesUseCase(get(), get()) }
    single { FriendPushes(get()) }
    single { FriendsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
