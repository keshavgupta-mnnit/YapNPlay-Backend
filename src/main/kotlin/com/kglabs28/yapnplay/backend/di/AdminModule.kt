package com.kglabs28.yapnplay.backend.di

import com.kglabs28.yapnplay.backend.application.admin.AdminMatchesUseCase
import com.kglabs28.yapnplay.backend.application.admin.AdminActionsUseCase
import com.kglabs28.yapnplay.backend.infrastructure.db.ExposedAdminDirectory
import com.kglabs28.yapnplay.backend.domain.admin.AdminDirectory
import com.kglabs28.yapnplay.backend.api.auth.AdminTokenCheck
import com.kglabs28.yapnplay.backend.application.admin.AdminStatsQueries
import com.kglabs28.yapnplay.backend.application.admin.GetAdminStatsUseCase
import com.kglabs28.yapnplay.backend.config.BackendConfig
import com.kglabs28.yapnplay.backend.infrastructure.db.ExposedAdminStatsQueries
import com.kglabs28.yapnplay.backend.api.auth.AdminAuth
import com.kglabs28.yapnplay.backend.application.admin.AdminAuthUseCase
import com.kglabs28.yapnplay.backend.application.admin.AdminRemovalUseCase
import com.kglabs28.yapnplay.backend.domain.admin.AdminAccountStore
import com.kglabs28.yapnplay.backend.infrastructure.db.ExposedAdminAccountStore
import org.koin.dsl.module

/** The operator dashboard: stats, sign-in, moderation and removals. */
fun adminModule(config: BackendConfig) = module {
    single<AdminStatsQueries> { ExposedAdminStatsQueries(get()) }
    single { GetAdminStatsUseCase(get(), get(), get(), get(), get(), get(), get()) }
    single { AdminTokenCheck(config.adminToken) }
    single<AdminDirectory> { ExposedAdminDirectory(get()) }
    single { AdminActionsUseCase(get(), get(), get(), get(), get(), get()) }
    single { AdminMatchesUseCase(get()) }
    single<AdminAccountStore> { ExposedAdminAccountStore(get()) }
    single { AdminAuthUseCase(get(), get(), get()) }
    single { AdminAuth(get(), get()) }
    single { AdminRemovalUseCase(get(), get(), get(), get(), get(), get()) }
}
