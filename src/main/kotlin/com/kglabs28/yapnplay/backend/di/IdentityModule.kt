package com.kglabs28.yapnplay.backend.di

import com.kglabs28.yapnplay.backend.application.identity.PlayGamesSignInUseCase
import com.kglabs28.yapnplay.backend.application.identity.AuthenticateUseCase
import com.kglabs28.yapnplay.backend.application.identity.CreateGuestUseCase
import com.kglabs28.yapnplay.backend.application.identity.GetMeUseCase
import com.kglabs28.yapnplay.backend.application.identity.UpdateProfileUseCase
import com.kglabs28.yapnplay.backend.application.identity.UserCodeAssigner
import com.kglabs28.yapnplay.backend.domain.identity.DisplayNamePolicy
import com.kglabs28.yapnplay.backend.domain.identity.GuestNameGenerator
import com.kglabs28.yapnplay.backend.domain.identity.ProgressionPolicy
import com.kglabs28.yapnplay.backend.domain.identity.ProfilePolicy
import com.kglabs28.yapnplay.backend.domain.identity.UserCodeGenerator
import org.koin.dsl.module

/** Guest accounts, sign-in and the user's own profile. */
val identityModule = module {
    single { DisplayNamePolicy() }
    single { GuestNameGenerator(get()) }
    single { ProgressionPolicy(get()) }
    single { ProfilePolicy() }
    single { UserCodeGenerator(get()) }
    single { UserCodeAssigner(get(), get()) }
    single { CreateGuestUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    single { PlayGamesSignInUseCase(get(), get(), get(), get(), get(), get()) }
    single { AuthenticateUseCase(get(), get()) }
    single { GetMeUseCase(get(), get(), get(), get()) }
    single { UpdateProfileUseCase(get(), get(), get(), get(), get(), get()) }
}
