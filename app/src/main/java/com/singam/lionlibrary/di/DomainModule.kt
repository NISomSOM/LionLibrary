package com.singam.lionlibrary.di

import com.singam.lionlibrary.domain.usecase.GetHomeContentUseCase
import com.singam.lionlibrary.domain.usecase.GetMediaDetailsUseCase
import com.singam.lionlibrary.domain.usecase.SearchMediaUseCase
import com.singam.lionlibrary.domain.usecase.UpdateWatchProgressUseCase
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val domainModule = module {
    // Lambda form keeps the dispatcher's production default; tests pass their own.
    single { GetHomeContentUseCase(get(), get()) }
    singleOf(::SearchMediaUseCase)
    singleOf(::GetMediaDetailsUseCase)
    singleOf(::UpdateWatchProgressUseCase)
}
