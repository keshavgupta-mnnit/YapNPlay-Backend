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
import org.koin.dsl.module

/** The read-only operator dashboard. */
fun adminModule(config: BackendConfig) = module {
    single<AdminStatsQueries> { ExposedAdminStatsQueries(get()) }
    single { GetAdminStatsUseCase(get(), get(), get(), get(), get(), get(), get()) }
    single { AdminTokenCheck(config.adminToken) }
    single<AdminDirectory> { ExposedAdminDirectory(get()) }
    single { AdminActionsUseCase(get(), get(), get(), get(), get(), get()) }
    single { AdminMatchesUseCase(get()) }
}
