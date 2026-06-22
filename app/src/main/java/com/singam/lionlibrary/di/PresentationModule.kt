package com.singam.lionlibrary.di

import com.singam.lionlibrary.presentation.details.DetailsViewModel
import com.singam.lionlibrary.presentation.home.HomeViewModel
import com.singam.lionlibrary.presentation.search.SearchViewModel
import com.singam.lionlibrary.presentation.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule = module {
    viewModelOf(::SettingsViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::DetailsViewModel)
}

