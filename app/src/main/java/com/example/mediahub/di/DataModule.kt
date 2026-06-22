package com.example.mediahub.di

import com.example.mediahub.data.local.preferences.PreferencesManager
import com.example.mediahub.data.player.AndroidLaunchPlayerUseCase
import com.example.mediahub.data.repository.DataStoreSettingsRepository
import com.example.mediahub.data.repository.RoomMediaRepository
import com.example.mediahub.data.repository.RoomWatchProgressRepository
import com.example.mediahub.data.scanner.FileNameParser
import com.example.mediahub.data.scanner.FolderScanner
import com.example.mediahub.data.scanner.ImageCacheManager
import com.example.mediahub.data.scanner.AndroidMediaScanner
import com.example.mediahub.domain.repository.MediaRepository
import com.example.mediahub.domain.repository.SettingsRepository
import com.example.mediahub.domain.repository.WatchProgressRepository
import com.example.mediahub.domain.usecase.LaunchPlayerUseCase
import com.example.mediahub.domain.usecase.ScanLibraryUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val dataModule = module {

    single { PreferencesManager(androidContext()) }

    singleOf(::FileNameParser)

    single { FolderScanner(androidContext()) }

    single { ImageCacheManager(androidContext(), get()) }

    singleOf(::RoomMediaRepository) bind MediaRepository::class

    singleOf(::RoomWatchProgressRepository) bind WatchProgressRepository::class

    singleOf(::DataStoreSettingsRepository) bind SettingsRepository::class

    single {
        AndroidMediaScanner(get(), get(), get(), get(), get(), get(), get(), get())
    } bind ScanLibraryUseCase::class

    single { AndroidLaunchPlayerUseCase(androidContext()) } bind LaunchPlayerUseCase::class
}
