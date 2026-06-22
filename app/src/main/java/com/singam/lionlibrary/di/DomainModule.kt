package com.singam.lionlibrary.di

import com.singam.lionlibrary.domain.usecase.GetMediaDetailsUseCase
import com.singam.lionlibrary.domain.usecase.SearchMediaUseCase
import com.singam.lionlibrary.domain.usecase.UpdateWatchProgressUseCase
import com.singam.lionlibrary.domain.usecase.GetHomeContentUseCase
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val domainModule = module {
    singleOf(::GetHomeContentUseCase)
    singleOf(::SearchMediaUseCase)
    singleOf(::GetMediaDetailsUseCase)
    singleOf(::UpdateWatchProgressUseCase)
}

