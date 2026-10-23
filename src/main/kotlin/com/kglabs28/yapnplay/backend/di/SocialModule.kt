package com.kglabs28.yapnplay.backend.di

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
import com.kglabs28.yapnplay.backend.infrastructure.voice.LiveKitTokenSigner
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
    single { IssueVoiceTokenUseCase(config.voice?.let { LiveKitTokenSigner(it) }, get(), get(), get(), get()) }
    single { ModerationService(get(), get(), get(), get(), get(), get(), get(), get(), get(ROOMS_SCOPE)) }
    single { RegisterDeviceUseCase(get(), get()) }
    single { FeedbackUseCase(get(), get(), get()) }
    single { GetRoomLeaderboardUseCase(get(), get()) }
    single { FriendsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
