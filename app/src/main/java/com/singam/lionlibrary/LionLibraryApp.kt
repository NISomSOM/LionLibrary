package com.singam.lionlibrary

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.singam.lionlibrary.di.dataModule
import com.singam.lionlibrary.di.databaseModule
import com.singam.lionlibrary.di.domainModule
import com.singam.lionlibrary.di.networkModule
import com.singam.lionlibrary.di.presentationModule
import okio.Path.Companion.toPath
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class LionLibraryApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()

        // Prewarm LibVLC and fontconfig.
        com.singam.lionlibrary.presentation.player.engine.LibVlcProvider.prewarm(this)

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@LionLibraryApp)
            modules(networkModule, databaseModule, dataModule, domainModule, presentationModule)
        }
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache { MemoryCache.Builder().maxSizePercent(this@LionLibraryApp, 0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").absolutePath.toPath())
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
