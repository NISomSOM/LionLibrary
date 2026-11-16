package com.singam.lionlibrary.di

import com.singam.lionlibrary.presentation.details.DetailsViewModel
import com.singam.lionlibrary.presentation.home.HomeViewModel
import com.singam.lionlibrary.presentation.library.LibraryViewModel
import com.singam.lionlibrary.presentation.player.ExternalPlaybackRouter
import com.singam.lionlibrary.presentation.player.PlayerViewModel
import com.singam.lionlibrary.presentation.player.engine.AndroidPlaybackAudioFocus
import com.singam.lionlibrary.presentation.player.engine.AndroidPlayerEngineFactory
import com.singam.lionlibrary.presentation.player.engine.PlaybackAudioFocus
import com.singam.lionlibrary.presentation.player.engine.PlayerEngineFactory
import com.singam.lionlibrary.presentation.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val presentationModule = module {
    single {
        AndroidPlayerEngineFactory(androidContext())
    } bind PlayerEngineFactory::class
    factory {
        AndroidPlaybackAudioFocus(androidContext())
    } bind PlaybackAudioFocus::class
    factoryOf(::ExternalPlaybackRouter)

    viewModelOf(::LibraryViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::DetailsViewModel)
    viewModelOf(::PlayerViewModel)
}
