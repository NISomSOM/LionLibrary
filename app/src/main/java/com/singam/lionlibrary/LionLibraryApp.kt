package com.singam.lionlibrary

import android.app.Application
import com.singam.lionlibrary.di.dataModule
import com.singam.lionlibrary.di.databaseModule
import com.singam.lionlibrary.di.domainModule
import com.singam.lionlibrary.di.networkModule
import com.singam.lionlibrary.di.presentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class LionLibraryApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@LionLibraryApp)
            modules(
                networkModule,
                databaseModule,
                dataModule,
                domainModule,
                presentationModule
            )
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}

