package com.example.mediahub.di

import com.example.mediahub.data.local.preferences.PreferencesManager
import com.example.mediahub.data.player.LaunchPlayerUseCaseImpl
import com.example.mediahub.data.repository.DataStoreSettingsRepository
import com.example.mediahub.data.repository.RoomMediaRepository
import com.example.mediahub.data.repository.RoomWatchProgressRepository
import com.example.mediahub.data.scanner.FileNameParser
import com.example.mediahub.data.scanner.FolderScanner
import com.example.mediahub.data.scanner.ImageCacheManager
import com.example.mediahub.data.scanner.MediaScannerImpl
import com.example.mediahub.domain.repository.MediaRepository
import com.example.mediahub.domain.repository.SettingsRepository
import com.example.mediahub.domain.repository.WatchProgressRepository
import com.example.mediahub.domain.usecase.LaunchPlayerUseCase
import com.example.mediahub.domain.usecase.ScanLibraryUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

val dataModule = module {

    single { PreferencesManager(androidContext()) }

    single { FileNameParser() }

    single { FolderScanner(androidContext()) }

    single { ImageCacheManager(androidContext(), get()) }

    single { RoomMediaRepository(get(), get(), get()) } bind MediaRepository::class

    single { RoomWatchProgressRepository(get()) } bind WatchProgressRepository::class

    single { DataStoreSettingsRepository(get()) } bind SettingsRepository::class

    single {
        MediaScannerImpl(get(), get(), get(), get(), get(), get(), get(), get())
    } bind ScanLibraryUseCase::class

    single { LaunchPlayerUseCaseImpl(androidContext()) } bind LaunchPlayerUseCase::class
}
