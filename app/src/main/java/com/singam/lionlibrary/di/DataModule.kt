package com.singam.lionlibrary.di

import com.singam.lionlibrary.data.local.preferences.PreferencesManager
import com.singam.lionlibrary.data.player.AndroidLaunchPlayerUseCase
import com.singam.lionlibrary.data.repository.DataStoreSettingsRepository
import com.singam.lionlibrary.data.repository.RoomMediaRepository
import com.singam.lionlibrary.data.repository.RoomWatchProgressRepository
import com.singam.lionlibrary.data.scanner.FileNameParser
import com.singam.lionlibrary.data.scanner.FolderScanner
import com.singam.lionlibrary.data.scanner.ImageCacheManager
import com.singam.lionlibrary.data.scanner.AndroidMediaScanner
import com.singam.lionlibrary.domain.repository.MediaRepository
import com.singam.lionlibrary.domain.repository.SettingsRepository
import com.singam.lionlibrary.domain.repository.WatchProgressRepository
import com.singam.lionlibrary.domain.usecase.LaunchPlayerUseCase
import com.singam.lionlibrary.domain.usecase.ScanLibraryUseCase
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

