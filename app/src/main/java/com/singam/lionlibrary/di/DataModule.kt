package com.singam.lionlibrary.di

import com.singam.lionlibrary.data.local.preferences.PreferencesManager
import com.singam.lionlibrary.data.player.AndroidLaunchPlayerUseCase
import com.singam.lionlibrary.data.repository.DataStoreSettingsRepository
import com.singam.lionlibrary.data.repository.RoomMediaRepository
import com.singam.lionlibrary.data.repository.RoomPlaybackRepository
import com.singam.lionlibrary.data.repository.RoomWatchProgressRepository
import com.singam.lionlibrary.data.repository.SafMediaDeletionRepository
import com.singam.lionlibrary.data.repository.WebLetterboxdRepository
import com.singam.lionlibrary.data.scanner.AndroidMediaScanner
import com.singam.lionlibrary.data.scanner.AndroidScanCoordinator
import com.singam.lionlibrary.data.scanner.FileNameParser
import com.singam.lionlibrary.data.scanner.FolderScanner
import com.singam.lionlibrary.data.scanner.ImageCacheManager
import com.singam.lionlibrary.data.storage.*
import com.singam.lionlibrary.domain.repository.LetterboxdRepository
import com.singam.lionlibrary.domain.repository.MediaDeletionRepository
import com.singam.lionlibrary.domain.repository.MediaRepository
import com.singam.lionlibrary.domain.repository.SettingsRepository
import com.singam.lionlibrary.domain.repository.WatchProgressRepository
import com.singam.lionlibrary.domain.usecase.LaunchPlayerUseCase
import com.singam.lionlibrary.domain.usecase.ScanCoordinator
import com.singam.lionlibrary.domain.usecase.ScanLibraryUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val dataModule = module {
    singleOf(::LibraryMutationGate)
    single { SafLibraryDocuments(androidContext()) } bind LibraryDocuments::class
    singleOf(::LibraryReconciler)
    singleOf(::SafMediaDeletionRepository) bind MediaDeletionRepository::class
    single { PreferencesManager(androidContext()) }

    singleOf(::FileNameParser)

    single { FolderScanner(androidContext(), get()) }

    single { ImageCacheManager(androidContext(), get()) } bind
        com.singam.lionlibrary.domain.repository.ArtworkRepository::class

    single {
        com.singam.lionlibrary.data.repository.TmdbMetadataRepository(
            androidContext(),
            get(),
            get(),
            get(),
            get(),
        )
    } bind com.singam.lionlibrary.domain.repository.MetadataRepository::class

    singleOf(::RoomPlaybackRepository) bind
        com.singam.lionlibrary.domain.repository.PlaybackRepository::class
    singleOf(::RoomMediaRepository) bind MediaRepository::class

    singleOf(::RoomWatchProgressRepository) bind WatchProgressRepository::class

    singleOf(::DataStoreSettingsRepository) bind SettingsRepository::class
    single {
        com.singam.lionlibrary.data.repository.DataStoreTrackPreferenceRepository(androidContext())
    } bind com.singam.lionlibrary.domain.repository.TrackPreferenceRepository::class

    single {
        AndroidMediaScanner(
            androidContext(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
        )
    } bind ScanLibraryUseCase::class

    single { AndroidScanCoordinator(androidContext(), get(), get(), get()) } bind
        ScanCoordinator::class

    single { AndroidLaunchPlayerUseCase(androidContext()) } bind LaunchPlayerUseCase::class

    single { WebLetterboxdRepository(get(), get()) } bind LetterboxdRepository::class
}
