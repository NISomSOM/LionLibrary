package com.example.mediahub.di

import com.example.mediahub.domain.usecase.GetMediaDetailsUseCase
import com.example.mediahub.domain.usecase.SearchMediaUseCase
import com.example.mediahub.domain.usecase.UpdateWatchProgressUseCase
import com.example.mediahub.domain.usecase.GetHomeContentUseCase
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val domainModule = module {
    singleOf(::GetHomeContentUseCase)
    singleOf(::SearchMediaUseCase)
    singleOf(::GetMediaDetailsUseCase)
    singleOf(::UpdateWatchProgressUseCase)
}
