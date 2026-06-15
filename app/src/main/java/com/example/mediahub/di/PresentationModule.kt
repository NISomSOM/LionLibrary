package com.example.mediahub.di

import com.example.mediahub.presentation.details.DetailsViewModel
import com.example.mediahub.presentation.home.HomeViewModel
import com.example.mediahub.presentation.search.SearchViewModel
import com.example.mediahub.presentation.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule = module {
    viewModelOf(::SettingsViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::DetailsViewModel)
}
