package com.singam.lionlibrary.di

import com.singam.lionlibrary.domain.usecase.GetHomeContentUseCase
import com.singam.lionlibrary.domain.usecase.MarkMediaWatchedUseCase
import org.koin.dsl.module

val domainModule = module {
    // Lambda form keeps the dispatcher's production default; tests pass their own.
    single { GetHomeContentUseCase(get(), get()) }
    factory { MarkMediaWatchedUseCase(get(), get()) }
}
